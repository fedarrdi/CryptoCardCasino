package com.raretable.casino.paper_trading.market_data.binance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import com.raretable.casino.paper_trading.market_data.BtcCandleInterval;
import com.raretable.casino.paper_trading.market_data.LiveBtcCandle;

class BinanceWebSocketMessageParserTests
{
    private final BinanceWebSocketMessageParser parser =
        new BinanceWebSocketMessageParser(JsonMapper.builder().build());

    @Test
    void mapsCurrentKlineToEpochSeconds()
    {
        LiveBtcCandle candle = parser.parse(message("1h", false));

        assertEquals("BTCUSDT", candle.symbol());
        assertEquals("1h", candle.interval());
        assertEquals(1785139200L, candle.time());
        assertEquals(new BigDecimal("65000.10000000"), candle.open());
        assertEquals(new BigDecimal("65500.20000000"), candle.high());
        assertEquals(new BigDecimal("64900.30000000"), candle.low());
        assertEquals(new BigDecimal("65300.40000000"), candle.close());
        assertEquals(new BigDecimal("123.45000000"), candle.volume());
        assertFalse(candle.closed());
    }

    @Test
    void mapsBinanceClosedFlag()
    {
        assertTrue(parser.parse(message("1h", true)).closed());
    }

    @Test
    void acceptsEveryConfiguredCombinedKlineStream()
    {
        for (BtcCandleInterval interval : BtcCandleInterval.values())
        {
            assertEquals(
                interval.value(),
                parser.parse(message(interval.value(), false)).interval()
            );
        }
    }

    @Test
    void rejectsAnUnsupportedKlineInterval()
    {
        assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(message("30m", false))
        );
    }

    @Test
    void rejectsACombinedStreamNameThatDoesNotMatchItsPayload()
    {
        assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(
                message("4h", false)
                    .replace("btcusdt@kline_4h", "btcusdt@kline_1h")
            )
        );
    }

    @Test
    void rejectsAnUnexpectedSymbol()
    {
        assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(
                message("1h", false)
                    .replace("\"s\": \"BTCUSDT\"", "\"s\": \"ETHUSDT\"")
            )
        );
    }

    private static String message(String interval, boolean closed)
    {
        return """
            {
              "stream": "btcusdt@kline_%s",
              "data": {
                "e": "kline",
                "E": 1785140000000,
                "s": "BTCUSDT",
                "k": {
                  "t": 1785139200000,
                  "T": 1785142799999,
                  "s": "BTCUSDT",
                  "i": "%s",
                  "o": "65000.10000000",
                  "c": "65300.40000000",
                  "h": "65500.20000000",
                  "l": "64900.30000000",
                  "v": "123.45000000",
                  "x": %s
                }
              }
            }
            """.formatted(interval, interval, closed);
    }
}
