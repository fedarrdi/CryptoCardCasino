package com.raretable.casino.paper_trading.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.Positive;

public record UpdateRiskControlsRequest(
    @Positive BigDecimal stopLoss,
    @Positive BigDecimal takeProfit
)
{
}
