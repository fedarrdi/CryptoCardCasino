package com.raretable.casino.paper_trading.api;

import java.math.BigDecimal;
import java.time.Instant;

public record TradingQuoteResponse(
    String symbol,
    String productType,
    BigDecimal bidPrice,
    BigDecimal askPrice,
    BigDecimal lastPrice,
    BigDecimal markPrice,
    BigDecimal indexPrice,
    BigDecimal fundingRate,
    Instant nextFundingAt,
    Instant bookUpdatedAt,
    Instant lastPriceUpdatedAt,
    Instant markPriceUpdatedAt
)
{
}
