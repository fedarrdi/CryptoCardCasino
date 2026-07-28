package com.raretable.casino.paper_trading.market_data.binance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

import com.raretable.casino.paper_trading.market_data.BinanceFundingRate;
import com.raretable.casino.paper_trading.market_data.BinanceFundingRateSource;

final class BinanceRestFundingRateClient
    implements BinanceFundingRateSource
{
    private static final String SYMBOL = "BTCUSDT";
    private final RestClient restClient;

    BinanceRestFundingRateClient(RestClient restClient)
    {
        this.restClient = restClient;
    }

    @Override
    public List<BinanceFundingRate> getBtcFundingRates(
        Instant startTime,
        Instant endTime,
        int limit
    )
    {
        if (startTime == null || endTime == null)
        {
            throw new IllegalArgumentException(
                "Funding-history time range is required"
            );
        }
        if (endTime.isBefore(startTime))
        {
            throw new IllegalArgumentException(
                "Funding-history end must not precede its start"
            );
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE)
        {
            throw new IllegalArgumentException(
                "Binance funding page size must be between 1 and 1000"
            );
        }
        JsonNode response = restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/fapi/v1/fundingRate")
                .queryParam("symbol", SYMBOL)
                .queryParam("startTime", startTime.toEpochMilli())
                .queryParam("endTime", endTime.toEpochMilli())
                .queryParam("limit", limit)
                .build()
            )
            .retrieve()
            .body(JsonNode.class);
        if (response == null || !response.isArray())
        {
            throw new IllegalStateException(
                "Binance returned an invalid funding-history response"
            );
        }
        List<BinanceFundingRate> records =
            new ArrayList<>(response.size());
        for (JsonNode row : response)
        {
            records.add(new BinanceFundingRate(
                text(row, "symbol"),
                Instant.ofEpochMilli(integer(row, "fundingTime")),
                text(row, "rateType"),
                decimal(row, "fundingRate"),
                decimal(row, "markPrice")
            ));
        }
        return List.copyOf(records);
    }

    private static String text(JsonNode row, String field)
    {
        JsonNode value = required(row, field);
        if (!value.isString())
        {
            throw new IllegalStateException(
                "Binance funding field " + field + " is not a string"
            );
        }
        return value.stringValue();
    }

    private static BigDecimal decimal(JsonNode row, String field)
    {
        return new BigDecimal(text(row, field));
    }

    private static long integer(JsonNode row, String field)
    {
        JsonNode value = required(row, field);
        if (!value.isIntegralNumber())
        {
            throw new IllegalStateException(
                "Binance funding field " + field + " is not an integer"
            );
        }
        return value.longValue();
    }

    private static JsonNode required(JsonNode row, String field)
    {
        JsonNode value = row == null ? null : row.get(field);
        if (value == null || value.isNull())
        {
            throw new IllegalStateException(
                "Binance funding row is missing field " + field
            );
        }
        return value;
    }
}
