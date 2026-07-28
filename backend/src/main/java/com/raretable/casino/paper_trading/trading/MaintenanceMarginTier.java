package com.raretable.casino.paper_trading.trading;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record MaintenanceMarginTier(
    int tier,
    BigDecimal notionalFloor,
    BigDecimal notionalCap,
    int maxLeverage,
    BigDecimal maintenanceMarginRate,
    BigDecimal maintenanceAmountUsd
)
{
    public MaintenanceMarginTier
    {
        if (tier < 1)
        {
            throw new IllegalArgumentException(
                "Maintenance-margin tier must be positive"
            );
        }
        if (notionalFloor == null || notionalFloor.signum() < 0)
        {
            throw new IllegalArgumentException(
                "Maintenance-margin floor must be non-negative"
            );
        }
        if (notionalCap != null
            && notionalCap.compareTo(notionalFloor) <= 0)
        {
            throw new IllegalArgumentException(
                "Maintenance-margin cap must exceed its floor"
            );
        }
        if (maxLeverage < 1 || maxLeverage > 100)
        {
            throw new IllegalArgumentException(
                "Tier leverage must be between 1 and 100"
            );
        }
        if (maintenanceMarginRate == null
            || maintenanceMarginRate.signum() <= 0
            || maintenanceMarginRate.compareTo(BigDecimal.ONE) >= 0)
        {
            throw new IllegalArgumentException(
                "Maintenance-margin rate must be between zero and one"
            );
        }
        if (maintenanceAmountUsd == null
            || maintenanceAmountUsd.signum() < 0)
        {
            throw new IllegalArgumentException(
                "Maintenance deduction must be non-negative"
            );
        }
    }

    public boolean contains(BigDecimal notional)
    {
        return notional.compareTo(notionalFloor) >= 0
            && (
                notionalCap == null
                || notional.compareTo(notionalCap) < 0
            );
    }

    public BigDecimal maintenanceMargin(BigDecimal notional)
    {
        if (!contains(notional)
            && (
                notionalCap == null
                || notional.compareTo(notionalCap) != 0
            ))
        {
            throw new IllegalArgumentException(
                "Notional is outside maintenance-margin tier " + tier
            );
        }
        return notional.multiply(maintenanceMarginRate)
            .subtract(maintenanceAmountUsd)
            .setScale(PaperTradingMath.MONEY_SCALE, RoundingMode.CEILING);
    }
}
