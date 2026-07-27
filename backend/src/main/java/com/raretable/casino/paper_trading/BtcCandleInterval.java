package com.raretable.casino.paper_trading;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.stream.Collectors;

public enum BtcCandleInterval
{
    ONE_HOUR("1h", Duration.ofHours(1)),
    TWO_HOURS("2h", Duration.ofHours(2)),
    FOUR_HOURS("4h", Duration.ofHours(4)),
    SIX_HOURS("6h", Duration.ofHours(6)),
    EIGHT_HOURS("8h", Duration.ofHours(8)),
    TWELVE_HOURS("12h", Duration.ofHours(12)),
    ONE_DAY("1d", Duration.ofDays(1)),
    THREE_DAYS("3d", Duration.ofDays(3)),
    ONE_WEEK("1w", Duration.ofDays(7)),
    ONE_MONTH("1M", Duration.ofDays(31));

    private static final String SUPPORTED_VALUES = Arrays.stream(values())
        .map(BtcCandleInterval::value)
        .collect(Collectors.joining(", "));

    private final String value;
    private final Duration maximumSpan;

    BtcCandleInterval(String value, Duration maximumSpan)
    {
        this.value = value;
        this.maximumSpan = maximumSpan;
    }

    public String value()
    {
        return value;
    }

    Duration maximumSpan()
    {
        return maximumSpan;
    }

    Instant nextOpenTime(Instant openTime)
    {
        if (this == ONE_MONTH)
        {
            return openTime.atZone(ZoneOffset.UTC)
                .plusMonths(1)
                .toInstant();
        }
        return openTime.plus(maximumSpan);
    }

    public static BtcCandleInterval parse(String value)
    {
        for (BtcCandleInterval interval : values())
        {
            if (interval.value.equals(value))
            {
                return interval;
            }
        }
        throw new IllegalArgumentException(
            "Unsupported BTC candle interval '" + value
                + "'. Supported intervals: " + SUPPORTED_VALUES
        );
    }
}
