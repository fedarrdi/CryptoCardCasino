package com.raretable.casino.paper_trading.price;

import java.math.BigDecimal;

public record BtcQuote(
    BigDecimal bidPrice,
    BigDecimal askPrice
)
{
    public BtcQuote
    {
        if (bidPrice == null || bidPrice.signum() <= 0)
        {
            throw new IllegalArgumentException("BTC bid price must be positive");
        }
        if (askPrice == null || askPrice.signum() <= 0)
        {
            throw new IllegalArgumentException("BTC ask price must be positive");
        }
        if (bidPrice.compareTo(askPrice) > 0)
        {
            throw new IllegalArgumentException(
                "BTC bid price cannot exceed the ask price"
            );
        }
    }

    public BigDecimal midpoint()
    {
        return bidPrice.add(askPrice)
            .divide(BigDecimal.valueOf(2));
    }
}
