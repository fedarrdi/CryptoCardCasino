package com.raretable.casino.paper_trading.market_data.persistence;

import java.math.BigDecimal;
import java.time.Instant;

import com.raretable.casino.paper_trading.api.BtcCandle;

public record StoredCandle(
    String symbol,
    String interval,
    Instant openTime,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    BigDecimal volume
)
{
    public BtcCandle toResponse()
    {
        return new BtcCandle(
            openTime.getEpochSecond(),
            open,
            high,
            low,
            close,
            volume
        );
    }
}
