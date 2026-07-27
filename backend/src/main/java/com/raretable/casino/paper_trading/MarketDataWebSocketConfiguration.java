package com.raretable.casino.paper_trading;

import java.net.URI;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.raretable.casino.auth.SiweProperties;

@Configuration(proxyBeanMethods = false)
@EnableWebSocket
class MarketDataWebSocketConfiguration implements WebSocketConfigurer
{
    private static final String ENDPOINT_PREFIX =
        "/ws/market-data/btcusdt/";

    private final MarketDataWebSocketHandler handler;
    private final SiweProperties siweProperties;

    MarketDataWebSocketConfiguration(
        MarketDataWebSocketHandler handler,
        SiweProperties siweProperties
    )
    {
        this.handler = handler;
        this.siweProperties = siweProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry)
    {
        String allowedOrigin = origin(siweProperties.uri());
        for (BtcCandleInterval interval : BtcCandleInterval.values())
        {
            registry.addHandler(handler, endpoint(interval))
                .setAllowedOrigins(allowedOrigin);
        }
    }

    static String endpoint(BtcCandleInterval interval)
    {
        return ENDPOINT_PREFIX + interval.value();
    }

    static BtcCandleInterval interval(String path)
    {
        if (path == null || !path.startsWith(ENDPOINT_PREFIX))
        {
            throw new IllegalArgumentException(
                "Unexpected market-data WebSocket path"
            );
        }

        String value = path.substring(ENDPOINT_PREFIX.length());
        if (value.isEmpty() || value.indexOf('/') >= 0)
        {
            throw new IllegalArgumentException(
                "Unexpected market-data WebSocket path"
            );
        }
        return BtcCandleInterval.parse(value);
    }

    private static String origin(URI uri)
    {
        return uri.getScheme() + "://" + uri.getAuthority();
    }
}
