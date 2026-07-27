package com.raretable.casino.paper_trading;

import java.math.BigDecimal;

public record BtcCandle(
    long time,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    BigDecimal volume
) {}
