package com.raretable.casino.paper_trading.trading;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PerpetualRuleVersion(
    long id,
    String code,
    String symbol,
    String productType,
    Instant effectiveFrom,
    BigDecimal takerFeeRate,
    BigDecimal liquidationFeeRate,
    String liquidationMode,
    String negativeBalancePolicy,
    String stopTriggerPriceType,
    List<MaintenanceMarginTier> maintenanceTiers
)
{
    public PerpetualRuleVersion
    {
        if (code == null || code.isBlank())
        {
            throw new IllegalArgumentException("Rule-version code is required");
        }
        if (!"BTCUSDT".equals(symbol))
        {
            throw new IllegalArgumentException(
                "Rule version must describe BTCUSDT"
            );
        }
        if (!"USD_M_PERPETUAL".equals(productType))
        {
            throw new IllegalArgumentException(
                "Rule version must describe USD-M perpetual trading"
            );
        }
        if (effectiveFrom == null)
        {
            throw new IllegalArgumentException(
                "Rule-version effective time is required"
            );
        }
        requireRate(takerFeeRate, "Taker fee rate");
        requireRate(liquidationFeeRate, "Liquidation fee rate");
        requireValue(liquidationMode, "FULL", "Liquidation mode");
        requireValue(
            negativeBalancePolicy,
            "FLOOR_ZERO",
            "Negative-balance policy"
        );
        requireValue(
            stopTriggerPriceType,
            "LAST",
            "Stop-trigger price type"
        );
        maintenanceTiers = List.copyOf(maintenanceTiers);
        validateTiers(maintenanceTiers);
    }

    public MaintenanceMarginTier tierFor(BigDecimal notional)
    {
        if (notional == null || notional.signum() < 0)
        {
            throw new IllegalArgumentException(
                "Maintenance-margin notional must be non-negative"
            );
        }
        return maintenanceTiers.stream()
            .filter(tier -> tier.contains(notional))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "The active maintenance-margin schedule has no tier for "
                    + notional
            ));
    }

    private static void validateTiers(List<MaintenanceMarginTier> tiers)
    {
        if (tiers.isEmpty())
        {
            throw new IllegalStateException(
                "A rule version must have maintenance-margin tiers"
            );
        }
        BigDecimal expectedFloor = BigDecimal.ZERO;
        for (int index = 0; index < tiers.size(); index++)
        {
            MaintenanceMarginTier tier = tiers.get(index);
            if (tier.tier() != index + 1)
            {
                throw new IllegalStateException(
                    "Maintenance-margin tiers must be sequential"
                );
            }
            if (tier.notionalFloor().compareTo(expectedFloor) != 0)
            {
                throw new IllegalStateException(
                    "Maintenance-margin tiers must be continuous"
                );
            }
            if (index == tiers.size() - 1)
            {
                if (tier.notionalCap() != null)
                {
                    throw new IllegalStateException(
                        "The final maintenance-margin tier must be unbounded"
                    );
                }
            }
            else
            {
                if (tier.notionalCap() == null)
                {
                    throw new IllegalStateException(
                        "Only the final maintenance-margin tier may be unbounded"
                    );
                }
                expectedFloor = tier.notionalCap();
                MaintenanceMarginTier next = tiers.get(index + 1);
                BigDecimal currentBoundaryMaintenance =
                    tier.maintenanceMargin(tier.notionalCap());
                BigDecimal nextBoundaryMaintenance =
                    next.maintenanceMargin(tier.notionalCap());
                if (currentBoundaryMaintenance.compareTo(
                    nextBoundaryMaintenance
                ) != 0)
                {
                    throw new IllegalStateException(
                        "Maintenance-margin tiers must be value-continuous"
                    );
                }
            }
        }
    }

    private static void requireRate(BigDecimal rate, String label)
    {
        if (rate == null
            || rate.signum() < 0
            || rate.compareTo(BigDecimal.ONE) >= 0)
        {
            throw new IllegalArgumentException(
                label + " must be between zero and one"
            );
        }
    }

    private static void requireValue(
        String value,
        String expected,
        String label
    )
    {
        if (!expected.equals(value))
        {
            throw new IllegalArgumentException(
                label + " must be " + expected
            );
        }
    }
}
