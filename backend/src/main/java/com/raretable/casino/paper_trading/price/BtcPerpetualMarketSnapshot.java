package com.raretable.casino.paper_trading.price;

import java.math.BigDecimal;
import java.time.Instant;

public record BtcPerpetualMarketSnapshot(
    BigDecimal lastPrice,
    Instant lastPriceTime,
    BigDecimal bidPrice,
    BigDecimal askPrice,
    Instant bookTickerTime,
    BigDecimal markPrice,
    BigDecimal indexPrice,
    BigDecimal fundingRate,
    Instant nextFundingTime,
    Instant markPriceTime
)
{
    public BtcPerpetualMarketSnapshot
    {
        requirePositive(lastPrice, "BTC last price");
        requireTime(lastPriceTime, "BTC last-price time");
        requirePositive(bidPrice, "BTC bid price");
        requirePositive(askPrice, "BTC ask price");
        if (bidPrice.compareTo(askPrice) > 0)
        {
            throw new IllegalArgumentException(
                "BTC bid price cannot exceed the ask price"
            );
        }
        requireTime(bookTickerTime, "BTC book-ticker time");
        requirePositive(markPrice, "BTC mark price");
        requirePositive(indexPrice, "BTC index price");
        if (fundingRate == null)
        {
            throw new IllegalArgumentException("BTC funding rate is required");
        }
        requireTime(nextFundingTime, "BTC next-funding time");
        requireTime(markPriceTime, "BTC mark-price time");
    }

    public BtcQuote quote()
    {
        return new BtcQuote(bidPrice, askPrice);
    }

    private static void requirePositive(BigDecimal value, String label)
    {
        if (value == null || value.signum() <= 0)
        {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }

    private static void requireTime(Instant value, String label)
    {
        if (value == null || !value.isAfter(Instant.EPOCH))
        {
            throw new IllegalArgumentException(
                label + " must be after the Unix epoch"
            );
        }
    }
}
