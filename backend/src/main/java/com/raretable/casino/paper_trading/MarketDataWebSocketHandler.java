package com.raretable.casino.paper_trading;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
final class MarketDataWebSocketHandler
    extends TextWebSocketHandler
    implements DisposableBean
{
    private static final Logger LOGGER =
        LoggerFactory.getLogger(MarketDataWebSocketHandler.class);
    private static final int DEFAULT_SEND_TIME_LIMIT_MILLIS = 5_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 64 * 1_024;
    private static final int MAX_PENDING_TIMESTAMPS = 2;

    private final JsonMapper jsonMapper;
    private final int sendTimeLimitMillis;
    private final Map<String, ClientSession> sessions =
        new java.util.concurrent.ConcurrentHashMap<>();
    private final AtomicLong messageSequence = new AtomicLong();
    private final AtomicReference<PendingCandle> latestMessage =
        new AtomicReference<>();
    private final ExecutorService fanoutExecutor =
        Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService sendWatchdog =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform()
                .daemon(true)
                .name("market-data-send-watchdog")
                .factory()
        );

    @Autowired
    MarketDataWebSocketHandler(JsonMapper jsonMapper)
    {
        this(jsonMapper, DEFAULT_SEND_TIME_LIMIT_MILLIS);
    }

    MarketDataWebSocketHandler(
        JsonMapper jsonMapper,
        int sendTimeLimitMillis
    )
    {
        if (sendTimeLimitMillis <= 0)
        {
            throw new IllegalArgumentException(
                "Market-data send time limit must be positive"
            );
        }
        this.jsonMapper = jsonMapper;
        this.sendTimeLimitMillis = sendTimeLimitMillis;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session)
    {
        ConcurrentWebSocketSessionDecorator concurrentSession =
            new ConcurrentWebSocketSessionDecorator(
                session,
                sendTimeLimitMillis,
                BUFFER_SIZE_LIMIT_BYTES
            );
        ClientSession client = new ClientSession(concurrentSession);
        sessions.put(session.getId(), client);

        PendingCandle current = latestMessage.get();
        if (current != null)
        {
            enqueue(client, current);
        }
        finishInitialization(client);
    }

    @Override
    public void afterConnectionClosed(
        WebSocketSession session,
        CloseStatus status
    )
    {
        remove(session.getId());
    }

    @Override
    public void handleTransportError(
        WebSocketSession session,
        Throwable exception
    )
    {
        ClientSession client = remove(session.getId());
        if (client != null)
        {
            close(client.session);
        }
    }

    void broadcast(LiveBtcCandle candle)
    {
        PendingCandle message = new PendingCandle(
            candle.time(),
            messageSequence.incrementAndGet(),
            new TextMessage(serialize(candle))
        );
        latestMessage.set(message);
        sessions.values().forEach(client -> enqueue(client, message));
    }

    void clearLiveSnapshot()
    {
        latestMessage.set(null);
    }

    @Override
    public void destroy()
    {
        sendWatchdog.shutdownNow();
        fanoutExecutor.shutdownNow();
        sessions.values().forEach(client -> {
            markClosed(client);
            close(client.session);
        });
        sessions.clear();
        latestMessage.set(null);
    }

    private void enqueue(ClientSession client, PendingCandle message)
    {
        boolean submit = false;
        boolean overflow = false;

        synchronized (client)
        {
            if (client.closed.get())
            {
                return;
            }

            overflow = !offer(client, message);
            if (!overflow
                && !client.initializing
                && !client.sendTaskRunning)
            {
                client.sendTaskRunning = true;
                submit = true;
            }
        }

        if (overflow)
        {
            removeAndClose(
                client,
                new IllegalStateException(
                    "Market-data client fell more than two candles behind"
                )
            );
        }
        else if (submit)
        {
            submit(client);
        }
    }

    private void finishInitialization(ClientSession client)
    {
        boolean submit = false;
        synchronized (client)
        {
            client.initializing = false;
            if (!client.closed.get()
                && !client.pending.isEmpty()
                && !client.sendTaskRunning)
            {
                client.sendTaskRunning = true;
                submit = true;
            }
        }
        if (submit)
        {
            submit(client);
        }
    }

    private void submit(ClientSession client)
    {
        try
        {
            fanoutExecutor.execute(() -> sendPending(client));
        }
        catch (RejectedExecutionException exception)
        {
            synchronized (client)
            {
                client.sendTaskRunning = false;
            }
            removeAndClose(client, exception);
        }
    }

    private void sendPending(ClientSession client)
    {
        while (true)
        {
            PendingCandle message;
            synchronized (client)
            {
                if (client.closed.get())
                {
                    client.sendTaskRunning = false;
                    return;
                }

                message = client.pending.pollFirst();
                if (message == null)
                {
                    client.sendTaskRunning = false;
                    return;
                }
                client.sendingTime = message.time();
                client.sendingSequence = message.sequence();
            }

            try
            {
                sendWithWatchdog(client, message.message());
            }
            catch (IOException | RuntimeException exception)
            {
                removeAndClose(client, exception);
                synchronized (client)
                {
                    client.sendTaskRunning = false;
                    client.sendingTime = Long.MIN_VALUE;
                    client.sendingSequence = Long.MIN_VALUE;
                }
                return;
            }

            synchronized (client)
            {
                client.lastDeliveredTime = message.time();
                client.lastDeliveredSequence = message.sequence();
                client.sendingTime = Long.MIN_VALUE;
                client.sendingSequence = Long.MIN_VALUE;
            }
        }
    }

    private void sendWithWatchdog(
        ClientSession client,
        TextMessage message
    ) throws IOException
    {
        long token = client.nextSendToken.incrementAndGet();
        client.activeSendToken.set(token);
        ScheduledFuture<?> timeout = sendWatchdog.schedule(
            () -> {
                if (client.activeSendToken.compareAndSet(token, 0))
                {
                    removeAndClose(
                        client,
                        new IllegalStateException(
                            "Market-data client exceeded the send time limit"
                        )
                    );
                }
            },
            sendTimeLimitMillis,
            TimeUnit.MILLISECONDS
        );

        try
        {
            client.session.sendMessage(message);
        }
        finally
        {
            client.activeSendToken.compareAndSet(token, 0);
            timeout.cancel(false);
        }
    }

    private static boolean offer(
        ClientSession client,
        PendingCandle message
    )
    {
        if (isNotNewer(
            message,
            client.lastDeliveredTime,
            client.lastDeliveredSequence
        ) || isNotNewer(
            message,
            client.sendingTime,
            client.sendingSequence
        ))
        {
            return true;
        }

        PendingCandle first = client.pending.peekFirst();
        if (first == null)
        {
            client.pending.add(message);
            return true;
        }

        if (first.time() == message.time())
        {
            if (first.sequence() >= message.sequence())
            {
                return true;
            }
            client.pending.removeFirst();
            client.pending.addFirst(message);
            return true;
        }

        PendingCandle last = client.pending.peekLast();
        if (last != null && last.time() == message.time())
        {
            if (last.sequence() >= message.sequence())
            {
                return true;
            }
            client.pending.removeLast();
            client.pending.addLast(message);
            return true;
        }

        if (client.pending.size() >= MAX_PENDING_TIMESTAMPS)
        {
            return false;
        }

        if (message.time() < first.time())
        {
            client.pending.addFirst(message);
        }
        else
        {
            client.pending.addLast(message);
        }
        return true;
    }

    private static boolean isNotNewer(
        PendingCandle message,
        long time,
        long sequence
    )
    {
        return message.time() < time
            || (message.time() == time && message.sequence() <= sequence);
    }

    private ClientSession remove(String id)
    {
        ClientSession client = sessions.remove(id);
        if (client != null)
        {
            markClosed(client);
        }
        return client;
    }

    private void removeAndClose(ClientSession client, Throwable failure)
    {
        if (!sessions.remove(client.session.getId(), client))
        {
            return;
        }

        markClosed(client);
        try
        {
            fanoutExecutor.execute(() -> close(client.session));
        }
        catch (RejectedExecutionException exception)
        {
            close(client.session);
        }
        LOGGER.debug(
            "Closed a market-data client after a fan-out failure",
            failure
        );
    }

    private static void markClosed(ClientSession client)
    {
        client.closed.set(true);
        synchronized (client)
        {
            client.pending.clear();
        }
    }

    private String serialize(LiveBtcCandle candle)
    {
        try
        {
            return jsonMapper.writeValueAsString(candle);
        }
        catch (JacksonException exception)
        {
            throw new IllegalStateException(
                "Could not serialize a BTC candle update",
                exception
            );
        }
    }

    private static void close(WebSocketSession session)
    {
        try
        {
            session.close(CloseStatus.SERVER_ERROR);
        }
        catch (IOException exception)
        {
            LOGGER.debug("Could not close a market-data WebSocket", exception);
        }
    }

    private record PendingCandle(
        long time,
        long sequence,
        TextMessage message
    ) {}

    private static final class ClientSession
    {
        private final ConcurrentWebSocketSessionDecorator session;
        private final ArrayDeque<PendingCandle> pending = new ArrayDeque<>(
            MAX_PENDING_TIMESTAMPS
        );
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicLong nextSendToken = new AtomicLong();
        private final AtomicLong activeSendToken = new AtomicLong();
        private boolean initializing = true;
        private boolean sendTaskRunning;
        private long sendingTime = Long.MIN_VALUE;
        private long sendingSequence = Long.MIN_VALUE;
        private long lastDeliveredTime = Long.MIN_VALUE;
        private long lastDeliveredSequence = Long.MIN_VALUE;

        private ClientSession(
            ConcurrentWebSocketSessionDecorator session
        )
        {
            this.session = session;
        }
    }
}
