package com.raretable.casino.paper_trading.market_data.binance;

import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.raretable.casino.paper_trading.market_data.BtcCandleService;
import com.raretable.casino.paper_trading.market_data.LiveBtcCandle;
import com.raretable.casino.paper_trading.market_data.websocket.MarketDataWebSocketHandler;
import com.raretable.casino.paper_trading.price.BinanceWrapper;
import com.raretable.casino.paper_trading.trading.PaperTradingRiskMonitor;

@Component
final class BinanceMarketDataWebSocketClient
{
    private static final Logger LOGGER =
        LoggerFactory.getLogger(BinanceMarketDataWebSocketClient.class);

    private final HttpClient httpClient;
    private final MarketDataProperties properties;
    private final BinanceWebSocketMessageParser parser;
    private final BtcCandleService candleService;
    private final MarketDataWebSocketHandler browserClients;
    private final PaperTradingRiskMonitor riskMonitor;
    private final BinanceWrapper binance;
    private final ScheduledExecutorService reconnectExecutor =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform()
                .daemon(true)
                .name("binance-market-data-reconnect")
                .factory()
        );
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean connecting = new AtomicBoolean();
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean();
    private final AtomicReference<WebSocket> connection = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> reconnectTask =
        new AtomicReference<>();

    BinanceMarketDataWebSocketClient(
        HttpClient httpClient,
        MarketDataProperties properties,
        BinanceWebSocketMessageParser parser,
        BtcCandleService candleService,
        MarketDataWebSocketHandler browserClients,
        PaperTradingRiskMonitor riskMonitor,
        BinanceWrapper binance
    )
    {
        this.httpClient = httpClient;
        this.properties = properties;
        this.parser = parser;
        this.candleService = candleService;
        this.browserClients = browserClients;
        this.riskMonitor = riskMonitor;
        this.binance = binance;
    }

    void start()
    {
        if (running.compareAndSet(false, true))
        {
            connect();
        }
    }

    void stop()
    {
        if (!running.compareAndSet(true, false))
        {
            return;
        }

        ScheduledFuture<?> scheduled = reconnectTask.getAndSet(null);
        if (scheduled != null)
        {
            scheduled.cancel(false);
        }

        browserClients.clearLiveSnapshots();
        binance.clearBtcQuote();
        WebSocket webSocket = connection.getAndSet(null);
        if (webSocket != null)
        {
            webSocket.sendClose(
                WebSocket.NORMAL_CLOSURE,
                "Application stopping"
            );
        }
        reconnectExecutor.shutdownNow();
    }

    private void connect()
    {
        if (!running.get()
            || connection.get() != null
            || !connecting.compareAndSet(false, true))
        {
            return;
        }

        httpClient.newWebSocketBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .buildAsync(properties.webSocketUri(), new Listener())
            .whenComplete((webSocket, failure) -> {
                connecting.set(false);
                if (failure != null)
                {
                    browserClients.clearLiveSnapshots();
                    LOGGER.warn(
                        "Could not connect to the Binance kline stream",
                        failure
                    );
                    scheduleReconnect();
                }
            });
    }

    private void scheduleReconnect()
    {
        if (!running.get() || !reconnectScheduled.compareAndSet(false, true))
        {
            return;
        }

        long delayMillis = properties.reconnectDelay().toMillis();
        reconnectTask.set(reconnectExecutor.schedule(() -> {
            reconnectScheduled.set(false);
            connect();
        }, delayMillis, TimeUnit.MILLISECONDS));
    }

    private void accept(WebSocket webSocket, String message)
    {
        try
        {
            BinanceStreamEvent event = parser.parse(message);
            if (event instanceof BinanceCandleStreamEvent candleEvent)
            {
                LiveBtcCandle candle = candleEvent.candle();
                if (candle.closed())
                {
                    candleService.storeClosed(candle);
                }
                browserClients.broadcast(candle);
            }
            else if (event instanceof BinanceTradeStreamEvent tradeEvent)
            {
                riskMonitor.accept(
                    tradeEvent.price(),
                    tradeEvent.observedAt()
                );
            }
            else if (event instanceof BinanceBookTickerStreamEvent bookEvent)
            {
                binance.updateBtcQuote(bookEvent.quote());
            }
            webSocket.request(1);
        }
        catch (RuntimeException exception)
        {
            failConnection(webSocket, exception);
        }
    }

    private void failConnection(WebSocket webSocket, Throwable failure)
    {
        LOGGER.error("Binance kline stream failed", failure);
        boolean activeConnection = connection.compareAndSet(webSocket, null);
        webSocket.abort();
        if (activeConnection)
        {
            browserClients.clearLiveSnapshots();
            binance.clearBtcQuote();
            scheduleReconnect();
        }
    }

    private final class Listener implements WebSocket.Listener
    {
        private final StringBuilder fragments = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket)
        {
            if (!running.get()
                || !connection.compareAndSet(null, webSocket))
            {
                webSocket.abort();
                return;
            }

            try
            {
                LOGGER.info(
                    "Connected to the Binance BTCUSDT market-data streams"
                );
                webSocket.request(1);
            }
            catch (RuntimeException exception)
            {
                failConnection(webSocket, exception);
            }
        }

        @Override
        public CompletionStage<?> onText(
            WebSocket webSocket,
            CharSequence data,
            boolean last
        )
        {
            fragments.append(data);
            if (last)
            {
                String message = fragments.toString();
                fragments.setLength(0);
                accept(webSocket, message);
            }
            else
            {
                webSocket.request(1);
            }
            return null;
        }

        @Override
        public CompletionStage<?> onClose(
            WebSocket webSocket,
            int statusCode,
            String reason
        )
        {
            boolean activeConnection =
                connection.compareAndSet(webSocket, null);
            if (activeConnection)
            {
                browserClients.clearLiveSnapshots();
                binance.clearBtcQuote();
            }
            LOGGER.info(
                "Binance kline stream closed with status {}",
                statusCode
            );
            if (activeConnection)
            {
                scheduleReconnect();
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error)
        {
            failConnection(webSocket, error);
        }
    }
}
