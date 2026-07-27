package com.raretable.casino.paper_trading.price;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class BinanceWrapper
{
    private static final String BTC_PRICE_URL =
            "https://api.binance.com/api/v3/ticker/bookTicker?symbol=BTCUSDT";

    private final RestClient restClient;

    public BinanceWrapper()
    {
        this(RestClient.create());
    }

    BinanceWrapper(RestClient restClient)
    {
        this.restClient = restClient;
    }

    public BigDecimal getBtcPrice()
    {
        BookTickerResponse ticker = restClient.get()
                .uri(BTC_PRICE_URL)
                .retrieve()
                .body(BookTickerResponse.class);

        return ticker.bidPrice()
                .add(ticker.askPrice())
                .divide(BigDecimal.valueOf(2));
    }

    private record BookTickerResponse(BigDecimal bidPrice, BigDecimal askPrice) {}
}
