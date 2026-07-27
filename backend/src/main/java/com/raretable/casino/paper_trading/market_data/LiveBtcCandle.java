package com.raretable.casino.paper_trading.market_data;

import java.math.BigDecimal;

import com.raretable.casino.paper_trading.api.BtcCandle;

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
