package com.raretable.casino.paper_trading.market_data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class BtcCandleIntervalTests
{
    @Test
    void exposesEverySupportedBinanceIntervalAtOrAboveOneHour()
    {
        List<String> values = Arrays.stream(BtcCandleInterval.values())
            .map(BtcCandleInterval::value)
            .toList();

        assertEquals(
            List.of("1h", "2h", "4h", "6h", "8h", "12h",
                "1d", "3d", "1w", "1M"),
            values
        );
        values.forEach(value ->
            assertEquals(value, BtcCandleInterval.parse(value).value())
        );
    }

    @Test
    void advancesMonthlyCandlesByCalendarMonth()
    {
        assertEquals(
            Instant.parse("2024-03-01T00:00:00Z"),
            BtcCandleInterval.ONE_MONTH.nextOpenTime(
                Instant.parse("2024-02-01T00:00:00Z")
            )
        );
    }

    @Test
    void rejectsIntervalsBelowOneHourAndWrongCase()
    {
        assertThrows(
            IllegalArgumentException.class,
            () -> BtcCandleInterval.parse("30m")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> BtcCandleInterval.parse("1m")
        );
    }
}
