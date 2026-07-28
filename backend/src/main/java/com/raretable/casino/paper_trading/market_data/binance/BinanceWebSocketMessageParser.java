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
    private static final String AGGREGATE_TRADE_STREAM = "btcusdt@aggTrade";
    private static final String BOOK_TICKER_STREAM = "btcusdt@bookTicker";
    private static final String MARK_PRICE_STREAM = "btcusdt@markPrice@1s";

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
            if (AGGREGATE_TRADE_STREAM.equals(stream))
            {
                return parseAggregateTrade(event);
            }
            if (BOOK_TICKER_STREAM.equals(stream))
            {
                return parseBookTicker(event);
            }
            if (MARK_PRICE_STREAM.equals(stream))
            {
                return parseMarkPrice(event);
            }
            if (stream.startsWith("btcusdt@kline_"))
            {
                return new BinanceCandleStreamEvent(
                    parseCandle(stream, event)
                );
            }
            throw new IllegalArgumentException(
                "Binance sent an unexpected BTCUSDT perpetual stream"
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
        epochMillis(event, "E", "kline event time");
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
            BtcCandleService.PRODUCT_TYPE,
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
        requireUsdMPerpetual(event);
        if (!"aggTrade".equals(text(event, "e"))
            || !BtcCandleService.SYMBOL.equals(text(event, "s")))
        {
            throw new IllegalArgumentException(
                "Binance sent an unexpected aggregate-trade stream"
            );
        }
        epochMillis(event, "E", "aggregate-trade event time");
        return new BinanceTradeStreamEvent(
            nonNegativeLong(event, "a", "aggregate-trade ID"),
            decimal(event, "p"),
            epochMillis(event, "T", "aggregate-trade time")
        );
    }

    private static BinanceBookTickerStreamEvent parseBookTicker(JsonNode event)
    {
        requireUsdMPerpetual(event);
        if (!"bookTicker".equals(text(event, "e"))
            || !BtcCandleService.SYMBOL.equals(text(event, "s")))
        {
            throw new IllegalArgumentException(
                "Binance sent an unexpected book-ticker stream"
            );
        }
        epochMillis(event, "E", "book-ticker event time");
        return new BinanceBookTickerStreamEvent(
            nonNegativeLong(event, "u", "book-ticker update ID"),
            new BtcQuote(
                decimal(event, "b"),
                decimal(event, "a")
            ),
            epochMillis(event, "T", "book-ticker transaction time")
        );
    }

    private static BinanceMarkPriceStreamEvent parseMarkPrice(JsonNode event)
    {
        requireUsdMPerpetual(event);
        if (!"markPriceUpdate".equals(text(event, "e"))
            || !BtcCandleService.SYMBOL.equals(text(event, "s")))
        {
            throw new IllegalArgumentException(
                "Binance sent an unexpected mark-price stream"
            );
        }
        return new BinanceMarkPriceStreamEvent(
            decimal(event, "p"),
            decimal(event, "i"),
            decimal(event, "r"),
            epochMillis(
                event,
                "T",
                "next funding time"
            ),
            epochMillis(
                event,
                "E",
                "mark-price event time"
            )
        );
    }

    private static void requireUsdMPerpetual(JsonNode event)
    {
        long symbolType = integralLong(event, "st", "symbol type");
        if (symbolType != 1)
        {
            throw new IllegalArgumentException(
                "Binance stream is not USD-M perpetual market data"
            );
        }
    }

    private static long integralLong(
        JsonNode parent,
        String field,
        String label
    )
    {
        JsonNode value = required(parent, field);
        if (!value.isIntegralNumber())
        {
            throw new IllegalArgumentException(
                "Binance " + label + " is not an integer"
            );
        }
        return value.longValue();
    }

    private static long nonNegativeLong(
        JsonNode parent,
        String field,
        String label
    )
    {
        long value = integralLong(parent, field, label);
        if (value < 0)
        {
            throw new IllegalArgumentException(
                "Binance " + label + " cannot be negative"
            );
        }
        return value;
    }

    private static Instant epochMillis(
        JsonNode parent,
        String field,
        String label
    )
    {
        long value = integralLong(parent, field, label);
        if (value <= 0)
        {
            throw new IllegalArgumentException(
                "Binance " + label + " must be positive"
            );
        }
        return Instant.ofEpochMilli(value);
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
