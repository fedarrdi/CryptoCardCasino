package com.raretable.casino.paper_trading.trading;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaperFundingEvent(
    UUID id,
    String symbol,
    Instant fundingTime,
    String rateType,
    BigDecimal fundingRate,
    BigDecimal markPrice
)
{
}
