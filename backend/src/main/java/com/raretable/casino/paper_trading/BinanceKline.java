package com.raretable.casino.paper_trading;

import java.math.BigDecimal;
import java.time.Instant;

record BinanceKline(
    Instant openTime,
    Instant closeTime,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    BigDecimal volume
) {}
