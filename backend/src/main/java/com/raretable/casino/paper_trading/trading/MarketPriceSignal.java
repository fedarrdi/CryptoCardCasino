package com.raretable.casino.paper_trading.trading;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketPriceSignal(
    MarketPriceType type,
    BigDecimal price,
    Instant observedAt
)
{
    public MarketPriceSignal
    {
        if (type == null)
        {
            throw new IllegalArgumentException(
                "Market price type is required"
            );
        }
        if (price == null)
        {
            throw new IllegalArgumentException(
                "Observed market price is required"
            );
        }
        if (observedAt == null)
        {
            throw new IllegalArgumentException(
                "Market observation time is required"
            );
        }
    }

    public enum MarketPriceType
    {
        LAST,
        MARK
    }
}
