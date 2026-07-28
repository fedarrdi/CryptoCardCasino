package com.raretable.casino.paper_trading.market_data.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import tools.jackson.databind.json.JsonMapper;

import com.raretable.casino.paper_trading.market_data.BtcCandleInterval;
import com.raretable.casino.paper_trading.market_data.LiveBtcCandle;

class MarketDataWebSocketHandlerTests
{
    private MarketDataWebSocketHandler handler = handler(5_000);

    @AfterEach
    void closeHandler()
    {
        handler.destroy();
    }

    @Test
    void coalescesPendingUpdatesForTheSameCandle() throws Exception
    {
        RecordingSession session = new RecordingSession(
            "slow-client",
            true,
            2,
            false
        );

        handler.afterConnectionEstablished(session);
        handler.broadcast(candle(1785139200L, "65300.40", false));
        assertTrue(session.firstSendStarted.await(2, TimeUnit.SECONDS));

        handler.broadcast(candle(1785139200L, "65301.40", false));
        handler.broadcast(candle(1785139200L, "65302.40", false));
        session.releaseFirstSend.countDown();

        assertTrue(session.successfulSends.await(2, TimeUnit.SECONDS));
        assertEquals(2, session.messages.size());
        assertTrue(session.messages.get(1).contains("\"close\":65302.40"));
    }

    @Test
    void preservesFinalCandleBeforeSendingTheNextHour() throws Exception
    {
        RecordingSession session = new RecordingSession(
            "cross-hour-client",
            true,
            3,
            false
        );

        handler.afterConnectionEstablished(session);
        handler.broadcast(candle(1785139200L, "65300.40", false));
        assertTrue(session.firstSendStarted.await(2, TimeUnit.SECONDS));

        handler.broadcast(candle(1785139200L, "65310.40", true));
        handler.broadcast(candle(1785142800L, "65320.40", false));
        session.releaseFirstSend.countDown();

        assertTrue(session.successfulSends.await(2, TimeUnit.SECONDS));
        assertEquals(3, session.messages.size());
        assertTrue(session.messages.get(1).contains("\"time\":1785139200"));
        assertTrue(session.messages.get(1).contains("\"closed\":true"));
        assertTrue(session.messages.get(2).contains("\"time\":1785142800"));
    }

    @Test
    void blockedClientDoesNotStarveHealthyClientAndIsTimedOut()
        throws Exception
    {
        handler.destroy();
        handler = handler(500);
        RecordingSession blocked = new RecordingSession(
            "blocked-client",
            true,
            1,
            false
        );
        RecordingSession healthy = new RecordingSession(
            "healthy-client",
            false,
            1,
            false
        );
        handler.afterConnectionEstablished(blocked);
        handler.afterConnectionEstablished(healthy);

        handler.broadcast(candle(1785139200L, "65300.40", false));

        assertTrue(blocked.firstSendStarted.await(200, TimeUnit.MILLISECONDS));
        assertTrue(healthy.successfulSends.await(200, TimeUnit.MILLISECONDS));
        assertFalse(blocked.closed.await(100, TimeUnit.MILLISECONDS));
        assertTrue(blocked.closed.await(1, TimeUnit.SECONDS));
        assertTrue(
            CloseStatus.SESSION_NOT_RELIABLE.equalsCode(blocked.closeStatus)
        );
    }

    @Test
    void clearingSnapshotPreventsSendingStaleOpenCandleToNewClient()
        throws Exception
    {
        RecordingSession existing = new RecordingSession(
            "existing-client",
            false,
            2,
            false
        );
        handler.afterConnectionEstablished(existing);
        handler.broadcast(candle(1785139200L, "65300.40", false));
        assertTrue(existing.firstSendStarted.await(2, TimeUnit.SECONDS));
        handler.broadcast(
            candle("4h", 1785139200L, "65400.40", false)
        );

        handler.clearLiveSnapshots();
        RecordingSession newClient = new RecordingSession(
            "new-client",
            false,
            1,
            false
        );
        RecordingSession newFourHourClient = new RecordingSession(
            "new-four-hour-client",
            "4h",
            false,
            1,
            false
        );
        handler.afterConnectionEstablished(newClient);
        handler.afterConnectionEstablished(newFourHourClient);
        assertEquals(0, newClient.sendAttempts.get());
        assertEquals(0, newFourHourClient.sendAttempts.get());

        handler.broadcast(candle(1785142800L, "65320.40", false));
        handler.broadcast(
            candle("4h", 1785153600L, "65420.40", false)
        );
        assertTrue(newClient.successfulSends.await(2, TimeUnit.SECONDS));
        assertTrue(
            newFourHourClient.successfulSends.await(2, TimeUnit.SECONDS)
        );
        assertEquals(1, newClient.messages.size());
        assertEquals(1, newFourHourClient.messages.size());
        assertTrue(newClient.messages.getFirst().contains(
            "\"time\":1785142800"
        ));
        assertTrue(newFourHourClient.messages.getFirst().contains(
            "\"time\":1785153600"
        ));
    }

    @Test
    void removesAClientWhoseSendFails() throws Exception
    {
        RecordingSession session = new RecordingSession(
            "failed-client",
            false,
            0,
            true
        );

        handler.afterConnectionEstablished(session);
        handler.broadcast(candle(1785139200L, "65300.40", false));

        assertTrue(session.closed.await(2, TimeUnit.SECONDS));
        assertEquals(CloseStatus.SERVER_ERROR, session.closeStatus);
        assertEquals(1, session.sendAttempts.get());

        handler.broadcast(candle(1785139200L, "65301.40", false));
        assertEquals(1, session.sendAttempts.get());
    }

    @Test
    void routesUpdatesOnlyToClientsForTheSelectedInterval()
        throws Exception
    {
        RecordingSession oneHour = new RecordingSession(
            "one-hour-client",
            "1h",
            false,
            1,
            false
        );
        RecordingSession fourHours = new RecordingSession(
            "four-hour-client",
            "4h",
            false,
            1,
            false
        );
        handler.afterConnectionEstablished(oneHour);
        handler.afterConnectionEstablished(fourHours);

        handler.broadcast(candle("1h", 1785139200L, "65300.40", false));

        assertTrue(oneHour.successfulSends.await(2, TimeUnit.SECONDS));
        assertEquals(1, oneHour.messages.size());
        assertEquals(0, fourHours.sendAttempts.get());

        handler.broadcast(candle("4h", 1785139200L, "65400.40", false));

        assertTrue(fourHours.successfulSends.await(2, TimeUnit.SECONDS));
        assertEquals(1, oneHour.messages.size());
        assertEquals(1, fourHours.messages.size());
        assertTrue(fourHours.messages.getFirst().contains(
            "\"interval\":\"4h\""
        ));
    }

    @Test
    void sendsTheCachedSnapshotForEachClientsSelectedInterval()
        throws Exception
    {
        handler.broadcast(candle("2h", 1785139200L, "65200.40", false));
        handler.broadcast(candle("1d", 1785081600L, "66000.40", false));

        RecordingSession twoHours = new RecordingSession(
            "two-hour-client",
            "2h",
            false,
            1,
            false
        );
        RecordingSession oneDay = new RecordingSession(
            "one-day-client",
            "1d",
            false,
            1,
            false
        );
        handler.afterConnectionEstablished(twoHours);
        handler.afterConnectionEstablished(oneDay);

        assertTrue(twoHours.successfulSends.await(2, TimeUnit.SECONDS));
        assertTrue(oneDay.successfulSends.await(2, TimeUnit.SECONDS));
        assertTrue(twoHours.messages.getFirst().contains(
            "\"interval\":\"2h\""
        ));
        assertTrue(oneDay.messages.getFirst().contains(
            "\"interval\":\"1d\""
        ));
    }

    @Test
    void exposesOnlyExactSupportedIntervalPaths()
    {
        for (BtcCandleInterval interval : BtcCandleInterval.values())
        {
            String endpoint =
                "/ws/market-data/btcusdt/" + interval.value();
            assertEquals(
                endpoint,
                MarketDataWebSocketConfiguration.endpoint(interval)
            );
            assertEquals(
                interval,
                MarketDataWebSocketConfiguration.interval(endpoint)
            );
        }

        assertThrows(
            IllegalArgumentException.class,
            () -> MarketDataWebSocketConfiguration.interval(
                "/ws/market-data/btcusdt/1h/extra"
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> MarketDataWebSocketConfiguration.interval(
                "/ws/market-data/btcusdt/30m"
            )
        );
    }

    private static MarketDataWebSocketHandler handler(int sendTimeLimitMillis)
    {
        return new MarketDataWebSocketHandler(
            JsonMapper.builder().build(),
            sendTimeLimitMillis
        );
    }

    private static LiveBtcCandle candle(
        long time,
        String close,
        boolean closed
    )
    {
        return candle("1h", time, close, closed);
    }

    private static LiveBtcCandle candle(
        String interval,
        long time,
        String close,
        boolean closed
    )
    {
        return new LiveBtcCandle(
            "BTCUSDT",
            "USD_M_PERPETUAL",
            interval,
            time,
            new BigDecimal("65000.10"),
            new BigDecimal("65500.20"),
            new BigDecimal("64900.30"),
            new BigDecimal(close),
            new BigDecimal("123.45"),
            closed
        );
    }

    private static final class RecordingSession implements WebSocketSession
    {
        private final String id;
        private final URI uri;
        private final boolean blockFirstSend;
        private final boolean failSends;
        private final List<String> messages = new CopyOnWriteArrayList<>();
        private final AtomicInteger sendAttempts = new AtomicInteger();
        private final CountDownLatch firstSendStarted = new CountDownLatch(1);
        private final CountDownLatch releaseFirstSend;
        private final CountDownLatch successfulSends;
        private final CountDownLatch closed = new CountDownLatch(1);
        private volatile CloseStatus closeStatus;
        private volatile boolean open = true;
        private volatile int textMessageSizeLimit;
        private volatile int binaryMessageSizeLimit;

        private RecordingSession(
            String id,
            boolean blockFirstSend,
            int expectedSuccessfulSends,
            boolean failSends
        )
        {
            this(
                id,
                "1h",
                blockFirstSend,
                expectedSuccessfulSends,
                failSends
            );
        }

        private RecordingSession(
            String id,
            String interval,
            boolean blockFirstSend,
            int expectedSuccessfulSends,
            boolean failSends
        )
        {
            this.id = id;
            uri = URI.create(
                "ws://localhost/ws/market-data/btcusdt/" + interval
            );
            this.blockFirstSend = blockFirstSend;
            this.failSends = failSends;
            this.releaseFirstSend = new CountDownLatch(blockFirstSend ? 1 : 0);
            this.successfulSends = new CountDownLatch(expectedSuccessfulSends);
        }

        @Override
        public String getId()
        {
            return id;
        }

        @Override
        public URI getUri()
        {
            return uri;
        }

        @Override
        public HttpHeaders getHandshakeHeaders()
        {
            return new HttpHeaders();
        }

        @Override
        public Map<String, Object> getAttributes()
        {
            return Map.of();
        }

        @Override
        public Principal getPrincipal()
        {
            return null;
        }

        @Override
        public InetSocketAddress getLocalAddress()
        {
            return null;
        }

        @Override
        public InetSocketAddress getRemoteAddress()
        {
            return null;
        }

        @Override
        public String getAcceptedProtocol()
        {
            return null;
        }

        @Override
        public void setTextMessageSizeLimit(int messageSizeLimit)
        {
            textMessageSizeLimit = messageSizeLimit;
        }

        @Override
        public int getTextMessageSizeLimit()
        {
            return textMessageSizeLimit;
        }

        @Override
        public void setBinaryMessageSizeLimit(int messageSizeLimit)
        {
            binaryMessageSizeLimit = messageSizeLimit;
        }

        @Override
        public int getBinaryMessageSizeLimit()
        {
            return binaryMessageSizeLimit;
        }

        @Override
        public List<WebSocketExtension> getExtensions()
        {
            return Collections.emptyList();
        }

        @Override
        public void sendMessage(WebSocketMessage<?> message) throws IOException
        {
            int attempt = sendAttempts.incrementAndGet();
            firstSendStarted.countDown();
            if (failSends)
            {
                throw new IOException("connection closed");
            }

            messages.add(message.getPayload().toString());
            if (blockFirstSend && attempt == 1)
            {
                try
                {
                    if (!releaseFirstSend.await(2, TimeUnit.SECONDS))
                    {
                        throw new IOException("test send remained blocked");
                    }
                }
                catch (InterruptedException exception)
                {
                    Thread.currentThread().interrupt();
                    throw new IOException("test send interrupted", exception);
                }
            }
            successfulSends.countDown();
        }

        @Override
        public boolean isOpen()
        {
            return open;
        }

        @Override
        public void close()
        {
            close(CloseStatus.NORMAL);
        }

        @Override
        public void close(CloseStatus status)
        {
            closeStatus = status;
            open = false;
            releaseFirstSend.countDown();
            closed.countDown();
        }
    }
}
