package com.raretable.casino.paper_trading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class BinanceWrapperTests
{
    @Test
    void getsBtcMidPrice()
    {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer binance = MockRestServiceServer
            .bindTo(restClientBuilder)
            .build();
        binance.expect(
                once(),
                requestTo(
                    "https://api.binance.com/api/v3/ticker/bookTicker?symbol=BTCUSDT"
                )
            )
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(
                """
                {
                  "symbol": "BTCUSDT",
                  "bidPrice": "65000.10000000",
                  "askPrice": "65000.30000000"
                }
                """,
                MediaType.APPLICATION_JSON
            ));

        BigDecimal price = new BinanceWrapper(
            restClientBuilder.build()
        ).getBtcPrice();

        assertEquals(new BigDecimal("65000.20000000"), price);
        binance.verify();
    }
}
