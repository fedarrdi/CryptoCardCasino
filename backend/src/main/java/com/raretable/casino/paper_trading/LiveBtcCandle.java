package com.raretable.casino.paper_trading;

import java.math.BigDecimal;

public record LiveBtcCandle(
    String symbol,
    String interval,
    long time,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    BigDecimal volume,
    boolean closed
)
{
    BtcCandle candle()
    {
        return new BtcCandle(time, open, high, low, close, volume);
    }
}
