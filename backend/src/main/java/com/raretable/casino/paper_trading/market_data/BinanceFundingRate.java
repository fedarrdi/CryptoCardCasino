package com.raretable.casino.paper_trading.market_data;

import java.math.BigDecimal;
import java.time.Instant;

public record BinanceFundingRate(
    String symbol,
    Instant fundingTime,
    String rateType,
    BigDecimal fundingRate,
    BigDecimal markPrice
)
{
    public BinanceFundingRate
    {
        if (!"BTCUSDT".equals(symbol))
        {
            throw new IllegalArgumentException(
                "Funding record must describe BTCUSDT"
            );
        }
        if (fundingTime == null)
        {
            throw new IllegalArgumentException(
                "Funding time is required"
            );
        }
        if (rateType == null || rateType.isBlank())
        {
            throw new IllegalArgumentException(
                "Funding rate type is required"
            );
        }
        if (fundingRate == null)
        {
            throw new IllegalArgumentException(
                "Funding rate is required"
            );
        }
        if (markPrice == null || markPrice.signum() <= 0)
        {
            throw new IllegalArgumentException(
                "Funding mark price must be positive"
            );
        }
    }
}
