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
    static final String ENDPOINT = "/ws/market-data/btcusdt/1h";

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
        registry.addHandler(handler, ENDPOINT)
            .setAllowedOrigins(origin(siweProperties.uri()));
    }

    private static String origin(URI uri)
    {
        return uri.getScheme() + "://" + uri.getAuthority();
    }
}
