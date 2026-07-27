package com.raretable.casino.paper_trading;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
final class BinanceWebSocketMessageParser
{
    private final JsonMapper jsonMapper;

    BinanceWebSocketMessageParser(JsonMapper jsonMapper)
    {
        this.jsonMapper = jsonMapper;
    }

    LiveBtcCandle parse(String message)
    {
        try
        {
            JsonNode root = jsonMapper.readTree(message);
            JsonNode kline = required(root, "k");
            if (!"kline".equals(required(root, "e").stringValue()))
            {
                throw new IllegalArgumentException(
                    "Binance sent an unexpected stream event"
                );
            }

            String symbol = required(kline, "s").stringValue();
            String interval = required(kline, "i").stringValue();
            if (!BtcCandleService.SYMBOL.equals(symbol)
                || !BtcCandleService.INTERVAL.equals(interval))
            {
                throw new IllegalArgumentException(
                    "Binance sent an unexpected kline stream"
                );
            }

            JsonNode openTime = required(kline, "t");
            if (!openTime.isIntegralNumber())
            {
                throw new IllegalArgumentException(
                    "Binance kline time is not an integer"
                );
            }
            long openTimeMillis = openTime.longValue();
            if (openTimeMillis % 1_000 != 0)
            {
                throw new IllegalArgumentException(
                    "Binance kline time is not an exact epoch second"
                );
            }

            JsonNode closed = required(kline, "x");
            if (!closed.isBoolean())
            {
                throw new IllegalArgumentException(
                    "Binance kline closed flag is not boolean"
                );
            }

            return new LiveBtcCandle(
                symbol,
                interval,
                openTimeMillis / 1_000,
                decimal(kline, "o"),
                decimal(kline, "h"),
                decimal(kline, "l"),
                decimal(kline, "c"),
                decimal(kline, "v"),
                closed.booleanValue()
            );
        }
        catch (JacksonException exception)
        {
            throw new IllegalArgumentException(
                "Binance sent invalid kline JSON",
                exception
            );
        }
    }

    private static JsonNode required(JsonNode parent, String field)
    {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || value.isNull())
        {
            throw new IllegalArgumentException(
                "Binance kline is missing field " + field
            );
        }
        return value;
    }

    private static BigDecimal decimal(JsonNode kline, String field)
    {
        JsonNode value = required(kline, field);
        if (!value.isString())
        {
            throw new IllegalArgumentException(
                "Binance kline field " + field + " is not a string"
            );
        }
        return new BigDecimal(value.stringValue());
    }
}
