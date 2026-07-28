package com.raretable.casino.paper_trading.market_data.binance;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
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
    private final Map<Route, Channel> channels;

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

        channels = new EnumMap<>(Route.class);
        channels.put(
            Route.MARKET,
            new Channel(Route.MARKET, properties.marketWebSocketUri())
        );
        channels.put(
            Route.PUBLIC,
            new Channel(Route.PUBLIC, properties.publicWebSocketUri())
        );
    }

    void start()
    {
        if (running.compareAndSet(false, true))
        {
            channels.values().forEach(this::connect);
        }
    }

    void stop()
    {
        if (!running.compareAndSet(true, false))
        {
            return;
        }

        browserClients.clearLiveSnapshots();
        binance.clear();
        for (Channel channel : channels.values())
        {
            ScheduledFuture<?> scheduled =
                channel.reconnectTask.getAndSet(null);
            if (scheduled != null)
            {
                scheduled.cancel(false);
            }

            WebSocket webSocket = channel.connection.getAndSet(null);
            if (webSocket != null)
            {
                webSocket.sendClose(
                    WebSocket.NORMAL_CLOSURE,
                    "Application stopping"
                );
            }
        }
        reconnectExecutor.shutdownNow();
    }

    private void connect(Channel channel)
    {
        if (!running.get()
            || channel.connection.get() != null
            || !channel.connecting.compareAndSet(false, true))
        {
            return;
        }

        httpClient.newWebSocketBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .buildAsync(channel.uri, new Listener(channel))
            .whenComplete((webSocket, failure) -> {
                channel.connecting.set(false);
                if (failure != null)
                {
                    clear(channel.route);
                    LOGGER.warn(
                        "Could not connect to the Binance {} streams",
                        channel.route.label,
                        failure
                    );
                    scheduleReconnect(channel);
                }
            });
    }

    private void scheduleReconnect(Channel channel)
    {
        if (!running.get()
            || !channel.reconnectScheduled.compareAndSet(false, true))
        {
            return;
        }

        long delayMillis = properties.reconnectDelay().toMillis();
        channel.reconnectTask.set(reconnectExecutor.schedule(() -> {
            channel.reconnectScheduled.set(false);
            connect(channel);
        }, delayMillis, TimeUnit.MILLISECONDS));
    }

    private void accept(
        Channel channel,
        WebSocket webSocket,
        String message
    )
    {
        try
        {
            BinanceStreamEvent event = parser.parse(message);
            requireCorrectRoute(channel.route, event);

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
                binance.updateLastPrice(
                    tradeEvent.price(),
                    tradeEvent.observedAt()
                );
                riskMonitor.accept(
                    tradeEvent.price(),
                    tradeEvent.observedAt()
                );
            }
            else if (event instanceof BinanceBookTickerStreamEvent bookEvent)
            {
                binance.updateBtcQuote(
                    bookEvent.quote(),
                    bookEvent.observedAt()
                );
            }
            else if (event instanceof BinanceMarkPriceStreamEvent markEvent)
            {
                binance.updateMarkPrice(
                    markEvent.markPrice(),
                    markEvent.indexPrice(),
                    markEvent.fundingRate(),
                    markEvent.nextFundingTime(),
                    markEvent.observedAt()
                );
                riskMonitor.acceptMarkPrice(
                    markEvent.markPrice(),
                    markEvent.observedAt()
                );
            }
            webSocket.request(1);
        }
        catch (RuntimeException exception)
        {
            failConnection(channel, webSocket, exception);
        }
    }

    private static void requireCorrectRoute(
        Route route,
        BinanceStreamEvent event
    )
    {
        boolean correct = route == Route.PUBLIC
            ? event instanceof BinanceBookTickerStreamEvent
            : !(event instanceof BinanceBookTickerStreamEvent);
        if (!correct)
        {
            throw new IllegalArgumentException(
                "Binance sent market data through the wrong routed endpoint"
            );
        }
    }

    private void failConnection(
        Channel channel,
        WebSocket webSocket,
        Throwable failure
    )
    {
        LOGGER.error(
            "Binance {} stream failed",
            channel.route.label,
            failure
        );
        boolean activeConnection =
            channel.connection.compareAndSet(webSocket, null);
        webSocket.abort();
        if (activeConnection)
        {
            clear(channel.route);
            scheduleReconnect(channel);
        }
    }

    private void clear(Route route)
    {
        if (route == Route.MARKET)
        {
            browserClients.clearLiveSnapshots();
            binance.clearMarketPrices();
        }
        else
        {
            binance.clearBtcQuote();
        }
    }

    private final class Listener implements WebSocket.Listener
    {
        private final Channel channel;
        private final StringBuilder fragments = new StringBuilder();

        private Listener(Channel channel)
        {
            this.channel = channel;
        }

        @Override
        public void onOpen(WebSocket webSocket)
        {
            if (!running.get()
                || !channel.connection.compareAndSet(null, webSocket))
            {
                webSocket.abort();
                return;
            }

            try
            {
                LOGGER.info(
                    "Connected to the Binance BTCUSDT perpetual {} streams",
                    channel.route.label
                );
                webSocket.request(1);
            }
            catch (RuntimeException exception)
            {
                failConnection(channel, webSocket, exception);
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
                accept(channel, webSocket, message);
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
                channel.connection.compareAndSet(webSocket, null);
            if (activeConnection)
            {
                clear(channel.route);
            }
            LOGGER.info(
                "Binance {} stream closed with status {}",
                channel.route.label,
                statusCode
            );
            if (activeConnection)
            {
                scheduleReconnect(channel);
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error)
        {
            failConnection(channel, webSocket, error);
        }
    }

    private static final class Channel
    {
        private final Route route;
        private final URI uri;
        private final AtomicBoolean connecting = new AtomicBoolean();
        private final AtomicBoolean reconnectScheduled = new AtomicBoolean();
        private final AtomicReference<WebSocket> connection =
            new AtomicReference<>();
        private final AtomicReference<ScheduledFuture<?>> reconnectTask =
            new AtomicReference<>();

        private Channel(Route route, URI uri)
        {
            this.route = route;
            this.uri = uri;
        }
    }

    private enum Route
    {
        MARKET("market"),
        PUBLIC("public");

        private final String label;

        Route(String label)
        {
            this.label = label;
        }
    }
}
