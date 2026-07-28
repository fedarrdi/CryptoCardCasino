package com.raretable.casino.paper_trading.trading;

import java.math.BigDecimal;

public record AccountRiskMetrics(
    BigDecimal grossUnrealizedPnl,
    BigDecimal equity,
    BigDecimal initialMargin,
    BigDecimal maintenanceMargin,
    BigDecimal estimatedClosingFee,
    BigDecimal availableMargin,
    BigDecimal maintenanceMarginRatioPercent,
    AccountRiskState riskState,
    BigDecimal estimatedLowerLiquidationPrice,
    BigDecimal estimatedUpperLiquidationPrice,
    BigDecimal lowerBankruptcyPrice,
    BigDecimal upperBankruptcyPrice
)
{
}
