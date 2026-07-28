package com.raretable.casino.paper_trading.market_data.binance;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.raretable.casino.paper_trading.market_data.BinanceKlineSource;
import com.raretable.casino.paper_trading.market_data.BinanceFundingRateSource;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MarketDataProperties.class)
class MarketDataConfiguration
{
    @Bean
    BinanceKlineSource binanceRestKlineClient(
        MarketDataProperties properties,
        HttpClient marketDataHttpClient
    )
    {
        JdkClientHttpRequestFactory requestFactory =
            new JdkClientHttpRequestFactory(marketDataHttpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(20));

        RestClient restClient = RestClient.builder()
            .baseUrl(properties.restBaseUri().toString())
            .requestFactory(requestFactory)
            .build();
        return new BinanceRestKlineClient(restClient);
    }

    @Bean
    BinanceFundingRateSource binanceRestFundingRateClient(
        MarketDataProperties properties,
        HttpClient marketDataHttpClient
    )
    {
        JdkClientHttpRequestFactory requestFactory =
            new JdkClientHttpRequestFactory(marketDataHttpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        RestClient restClient = RestClient.builder()
            .baseUrl(properties.restBaseUri().toString())
            .requestFactory(requestFactory)
            .build();
        return new BinanceRestFundingRateClient(restClient);
    }

    @Bean
    HttpClient marketDataHttpClient()
    {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }
}
