package com.raretable.casino.paper_trading.market_data;

import java.math.BigDecimal;
import java.time.Instant;

public record BinanceKline(
    Instant openTime,
    Instant closeTime,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    BigDecimal volume
) {}
