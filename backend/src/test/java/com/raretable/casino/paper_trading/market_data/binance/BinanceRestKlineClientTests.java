package com.raretable.casino.paper_trading.market_data.binance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.raretable.casino.paper_trading.market_data.BinanceKline;
import com.raretable.casino.paper_trading.market_data.BtcCandleInterval;

class BinanceRestKlineClientTests
{
    @Test
    void requestsTheSelectedIntervalAndMapsPositionalKlines()
    {
        RestClient.Builder builder = RestClient.builder()
            .baseUrl("https://fapi.binance.com");
        MockRestServiceServer binance = MockRestServiceServer
            .bindTo(builder)
            .build();
        binance.expect(
                once(),
                requestTo(
                    "https://fapi.binance.com/fapi/v1/klines"
                        + "?symbol=BTCUSDT"
                        + "&interval=1M"
                        + "&startTime=1785139200000"
                        + "&limit=1000"
                )
            )
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(
                """
                [
                  [
                    1785139200000,
                    "65000.10000000",
                    "65500.20000000",
                    "64900.30000000",
                    "65300.40000000",
                    "123.45000000",
                    1785142799999,
                    "0",
                    42,
                    "0",
                    "0",
                    "0"
                  ]
                ]
                """,
                MediaType.APPLICATION_JSON
            ));

        List<BinanceKline> result = new BinanceRestKlineClient(
            builder.build()
        ).getBtcKlines(
            BtcCandleInterval.ONE_MONTH,
            Instant.ofEpochMilli(1785139200000L),
            1_000
        );

        assertEquals(1, result.size());
        BinanceKline candle = result.getFirst();
        assertEquals(
            Instant.ofEpochMilli(1785139200000L),
            candle.openTime()
        );
        assertEquals(
            Instant.ofEpochMilli(1785142799999L),
            candle.closeTime()
        );
        assertEquals(new BigDecimal("65000.10000000"), candle.open());
        assertEquals(new BigDecimal("65500.20000000"), candle.high());
        assertEquals(new BigDecimal("64900.30000000"), candle.low());
        assertEquals(new BigDecimal("65300.40000000"), candle.close());
        assertEquals(new BigDecimal("123.45000000"), candle.volume());
        binance.verify();
    }
}
