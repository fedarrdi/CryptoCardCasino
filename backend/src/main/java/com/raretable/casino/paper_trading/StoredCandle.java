package com.raretable.casino.paper_trading;

import java.math.BigDecimal;
import java.time.Instant;

record StoredCandle(
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
    BtcCandle toResponse()
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
