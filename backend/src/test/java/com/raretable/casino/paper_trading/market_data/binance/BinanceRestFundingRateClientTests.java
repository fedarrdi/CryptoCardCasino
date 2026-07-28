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

import com.raretable.casino.paper_trading.market_data.BinanceFundingRate;

class BinanceRestFundingRateClientTests
{
    @Test
    void requestsAndMapsAuthoritativeSettledFundingHistory()
    {
        RestClient.Builder builder = RestClient.builder()
            .baseUrl("https://fapi.binance.com");
        MockRestServiceServer binance = MockRestServiceServer
            .bindTo(builder)
            .build();
        binance.expect(
                once(),
                requestTo(
                    "https://fapi.binance.com/fapi/v1/fundingRate"
                        + "?symbol=BTCUSDT"
                        + "&startTime=1785225600000"
                        + "&endTime=1785254400000"
                        + "&limit=1000"
                )
            )
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(
                """
                [
                  {
                    "symbol": "BTCUSDT",
                    "fundingTime": 1785254400000,
                    "fundingRate": "0.00010000",
                    "markPrice": "65000.50000000",
                    "rateType": "FUNDING_RATE"
                  }
                ]
                """,
                MediaType.APPLICATION_JSON
            ));

        List<BinanceFundingRate> result =
            new BinanceRestFundingRateClient(builder.build())
                .getBtcFundingRates(
                    Instant.ofEpochMilli(1785225600000L),
                    Instant.ofEpochMilli(1785254400000L),
                    1000
                );

        assertEquals(1, result.size());
        BinanceFundingRate funding = result.getFirst();
        assertEquals("BTCUSDT", funding.symbol());
        assertEquals(
            Instant.ofEpochMilli(1785254400000L),
            funding.fundingTime()
        );
        assertEquals("FUNDING_RATE", funding.rateType());
        assertEquals(
            new BigDecimal("0.00010000"),
            funding.fundingRate()
        );
        assertEquals(
            new BigDecimal("65000.50000000"),
            funding.markPrice()
        );
        binance.verify();
    }
}
