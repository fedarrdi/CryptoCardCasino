package com.raretable.casino.paper_trading.api;

import java.math.BigDecimal;

import com.raretable.casino.paper_trading.trading.TradeSide;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PreviewPositionRequest(
    @NotNull TradeSide side,
    @NotNull @Min(1) @Max(100) Integer leverage,
    @NotNull @Positive BigDecimal marginUsd,
    @Positive BigDecimal stopLoss,
    @Positive BigDecimal takeProfit
)
{
}
