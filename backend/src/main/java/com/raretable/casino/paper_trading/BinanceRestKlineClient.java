package com.raretable.casino.paper_trading;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

final class BinanceRestKlineClient implements BinanceKlineSource
{
    static final int MAX_PAGE_SIZE = 1_000;

    private static final String SYMBOL = "BTCUSDT";
    private final RestClient restClient;

    BinanceRestKlineClient(RestClient restClient)
    {
        this.restClient = restClient;
    }

    @Override
    public List<BinanceKline> getBtcKlines(
        BtcCandleInterval interval,
        Instant startTime,
        int limit
    )
    {
        if (limit <= 0 || limit > MAX_PAGE_SIZE)
        {
            throw new IllegalArgumentException(
                "Binance kline page size must be between 1 and 1000"
            );
        }

        JsonNode response = restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/v3/klines")
                .queryParam("symbol", SYMBOL)
                .queryParam("interval", interval.value())
                .queryParam("startTime", startTime.toEpochMilli())
                .queryParam("limit", limit)
                .build()
            )
            .retrieve()
            .body(JsonNode.class);

        if (response == null || !response.isArray())
        {
            throw new IllegalStateException(
                "Binance returned an invalid kline response"
            );
        }

        List<BinanceKline> klines = new ArrayList<>(response.size());

        for (JsonNode row : response)
        {
            if (!row.isArray() || row.size() < 7)
            {
                throw new IllegalStateException(
                    "Binance returned an invalid kline row"
                );
            }
            if (!row.get(0).isIntegralNumber()
                || !row.get(6).isIntegralNumber())
            {
                throw new IllegalStateException(
                    "Binance returned invalid kline timestamps"
                );
            }

            klines.add(new BinanceKline(
                Instant.ofEpochMilli(row.get(0).longValue()),
                Instant.ofEpochMilli(row.get(6).longValue()),
                decimal(row, 1),
                decimal(row, 2),
                decimal(row, 3),
                decimal(row, 4),
                decimal(row, 5)
            ));
        }

        return List.copyOf(klines);
    }

    private static BigDecimal decimal(JsonNode row, int index)
    {
        JsonNode value = row.get(index);
        if (value == null || !value.isString())
        {
            throw new IllegalStateException(
                "Binance returned an invalid kline decimal"
            );
        }
        return new BigDecimal(value.stringValue());
    }
}
