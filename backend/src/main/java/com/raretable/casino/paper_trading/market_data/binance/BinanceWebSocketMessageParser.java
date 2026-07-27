package com.raretable.casino.paper_trading.market_data.binance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.raretable.casino.paper_trading.market_data.BtcCandleInterval;
import com.raretable.casino.paper_trading.market_data.BtcCandleService;
import com.raretable.casino.paper_trading.market_data.LiveBtcCandle;
import com.raretable.casino.paper_trading.price.BtcQuote;

@Component
final class BinanceWebSocketMessageParser
{
    private final JsonMapper jsonMapper;

    BinanceWebSocketMessageParser(JsonMapper jsonMapper)
    {
        this.jsonMapper = jsonMapper;
    }

    BinanceStreamEvent parse(String message)
    {
        try
        {
            JsonNode root = jsonMapper.readTree(message);
            String stream = text(root, "stream");
            JsonNode event = required(root, "data");
            if ("btcusdt@aggTrade".equals(stream))
            {
                return parseAggregateTrade(event);
            }
            if ("btcusdt@bookTicker".equals(stream))
            {
                return parseBookTicker(event);
            }
            return new BinanceCandleStreamEvent(
                parseCandle(stream, event)
            );
        }
        catch (JacksonException exception)
        {
            throw new IllegalArgumentException(
                "Binance sent invalid stream JSON",
                exception
            );
        }
    }

    private static LiveBtcCandle parseCandle(
        String stream,
        JsonNode event
    )
    {
        JsonNode kline = required(event, "k");
        if (!"kline".equals(text(event, "e")))
        {
            throw new IllegalArgumentException(
                "Binance sent an unexpected stream event"
            );
        }

        String eventSymbol = text(event, "s");
        String symbol = text(kline, "s");
        BtcCandleInterval interval =
            BtcCandleInterval.parse(text(kline, "i"));
        String expectedStream = symbol.toLowerCase(Locale.ROOT)
            + "@kline_"
            + interval.value();
        if (!BtcCandleService.SYMBOL.equals(eventSymbol)
            || !eventSymbol.equals(symbol)
            || !expectedStream.equals(stream))
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
            interval.value(),
            openTimeMillis / 1_000,
            decimal(kline, "o"),
            decimal(kline, "h"),
            decimal(kline, "l"),
            decimal(kline, "c"),
            decimal(kline, "v"),
            closed.booleanValue()
        );
    }

    private static BinanceTradeStreamEvent parseAggregateTrade(JsonNode event)
    {
        if (!"aggTrade".equals(text(event, "e"))
            || !BtcCandleService.SYMBOL.equals(text(event, "s")))
        {
            throw new IllegalArgumentException(
                "Binance sent an unexpected aggregate-trade stream"
            );
        }
        JsonNode tradeTime = required(event, "T");
        if (!tradeTime.isIntegralNumber())
        {
            throw new IllegalArgumentException(
                "Binance aggregate-trade time is not an integer"
            );
        }
        return new BinanceTradeStreamEvent(
            decimal(event, "p"),
            Instant.ofEpochMilli(tradeTime.longValue())
        );
    }

    private static BinanceBookTickerStreamEvent parseBookTicker(JsonNode event)
    {
        if (!BtcCandleService.SYMBOL.equals(text(event, "s")))
        {
            throw new IllegalArgumentException(
                "Binance sent an unexpected book-ticker stream"
            );
        }
        return new BinanceBookTickerStreamEvent(
            new BtcQuote(
                decimal(event, "b"),
                decimal(event, "a")
            )
        );
    }

    private static JsonNode required(JsonNode parent, String field)
    {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || value.isNull())
        {
            throw new IllegalArgumentException(
                "Binance stream is missing field " + field
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
                "Binance stream field " + field + " is not a string"
            );
        }
        return new BigDecimal(value.stringValue());
    }

    private static String text(JsonNode parent, String field)
    {
        JsonNode value = required(parent, field);
        if (!value.isString())
        {
            throw new IllegalArgumentException(
                "Binance stream field " + field + " is not a string"
            );
        }
        return value.stringValue();
    }
}
