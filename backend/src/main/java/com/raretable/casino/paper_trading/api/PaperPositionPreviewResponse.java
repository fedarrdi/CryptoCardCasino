package com.raretable.casino.paper_trading.api;

import java.math.BigDecimal;
import java.time.Instant;

public record PaperPositionPreviewResponse(
    BigDecimal entryPrice,
    BigDecimal notionalUsd,
    BigDecimal quantity,
    BigDecimal entryFee,
    BigDecimal estimatedExitFee,
    BigDecimal breakEvenPrice,
    BigDecimal bankruptcyPrice,
    BigDecimal estimatedLiquidationPrice,
    BigDecimal lowerBankruptcyPrice,
    BigDecimal upperBankruptcyPrice,
    BigDecimal estimatedLowerLiquidationPrice,
    BigDecimal estimatedUpperLiquidationPrice,
    BigDecimal maintenanceMargin,
    BigDecimal maintenanceMarginRate,
    BigDecimal postOrderMaintenanceMarginRatioPercent,
    BigDecimal availableMarginAfter,
    BigDecimal maxOrderMargin,
    BigDecimal takerFeeRate,
    BigDecimal liquidationFeeRate,
    String liquidationMode,
    String negativeBalancePolicy,
    String stopTriggerPriceType,
    String ruleVersion,
    Instant pricingAt
)
{
}
