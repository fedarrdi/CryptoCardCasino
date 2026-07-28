package com.raretable.casino.paper_trading.trading;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import org.springframework.stereotype.Component;

import com.raretable.casino.paper_trading.price.BtcPerpetualMarketSnapshot;

@Component
public final class PaperTradingRiskCalculator
{
    private static final MathContext CALCULATION_CONTEXT =
        new MathContext(34, RoundingMode.HALF_UP);
    private static final BigDecimal WARNING_MMR_PERCENT =
        new BigDecimal("80");

    public AccountRiskMetrics calculate(
        BigDecimal walletBalance,
        List<PaperTrade> openTrades,
        BtcPerpetualMarketSnapshot market,
        PerpetualRuleVersion rule
    )
    {
        if (walletBalance == null)
        {
            throw new IllegalArgumentException("Wallet balance is required");
        }
        List<PaperTrade> trades = List.copyOf(openTrades);
        BigDecimal grossUnrealizedPnl = moneyZero();
        BigDecimal initialMargin = moneyZero();
        BigDecimal estimatedClosingFee = moneyZero();
        BigDecimal longQuantity = BigDecimal.ZERO;
        BigDecimal shortQuantity = BigDecimal.ZERO;
        BigDecimal longEntryCost = BigDecimal.ZERO;
        BigDecimal shortEntryCost = BigDecimal.ZERO;

        for (PaperTrade trade : trades)
        {
            grossUnrealizedPnl = grossUnrealizedPnl.add(
                PaperTradingMath.pnl(
                    trade.side(),
                    trade.entryPrice(),
                    market.markPrice(),
                    trade.quantity()
                )
            );
            BigDecimal markNotional =
                market.markPrice().multiply(trade.quantity());
            initialMargin = initialMargin.add(
                markNotional.divide(
                    BigDecimal.valueOf(trade.leverage()),
                    PaperTradingMath.MONEY_SCALE,
                    RoundingMode.CEILING
                )
            );
            BigDecimal closingPrice =
                trade.side().closingPrice(market.quote());
            estimatedClosingFee = estimatedClosingFee.add(
                PaperTradingMath.reservedFee(
                    closingPrice,
                    trade.quantity(),
                    trade.entryFeeRate()
                )
            );
            BigDecimal entryCost =
                trade.entryPrice().multiply(trade.quantity());
            if (trade.side() == TradeSide.LONG)
            {
                longQuantity = longQuantity.add(trade.quantity());
                longEntryCost = longEntryCost.add(entryCost);
            }
            else
            {
                shortQuantity = shortQuantity.add(trade.quantity());
                shortEntryCost = shortEntryCost.add(entryCost);
            }
        }

        BigDecimal maintenanceMargin = maintenanceMargin(
            longQuantity,
            shortQuantity,
            market.markPrice(),
            rule
        );
        BigDecimal equity = walletBalance.add(grossUnrealizedPnl)
            .setScale(PaperTradingMath.MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal availableMargin = equity
            .subtract(initialMargin)
            .subtract(estimatedClosingFee)
            .setScale(PaperTradingMath.MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal mmr = PaperTradingMath
            .maintenanceMarginRatioPercent(maintenanceMargin, equity);
        AccountRiskState riskState = riskState(
            trades,
            equity,
            maintenanceMargin,
            mmr
        );
        PriceRoots liquidationRoots = liquidationRoots(
            walletBalance,
            longQuantity,
            shortQuantity,
            longEntryCost,
            shortEntryCost,
            market.markPrice(),
            rule
        );
        PriceRoots bankruptcyRoots = bankruptcyRoots(
            walletBalance,
            longQuantity,
            shortQuantity,
            longEntryCost,
            shortEntryCost,
            market.markPrice()
        );
        return new AccountRiskMetrics(
            grossUnrealizedPnl.setScale(PaperTradingMath.MONEY_SCALE),
            equity,
            initialMargin.setScale(PaperTradingMath.MONEY_SCALE),
            maintenanceMargin,
            estimatedClosingFee.setScale(PaperTradingMath.MONEY_SCALE),
            availableMargin,
            mmr,
            riskState,
            liquidationRoots.lower(),
            liquidationRoots.upper(),
            bankruptcyRoots.lower(),
            bankruptcyRoots.upper()
        );
    }

    public BigDecimal maintenanceMarginForNotional(
        BigDecimal notional,
        PerpetualRuleVersion rule
    )
    {
        return rule.tierFor(notional).maintenanceMargin(notional);
    }

    private static BigDecimal maintenanceMargin(
        BigDecimal longQuantity,
        BigDecimal shortQuantity,
        BigDecimal markPrice,
        PerpetualRuleVersion rule
    )
    {
        BigDecimal result = moneyZero();
        if (longQuantity.signum() > 0)
        {
            BigDecimal notional = markPrice.multiply(longQuantity)
                .setScale(PaperTradingMath.MONEY_SCALE, RoundingMode.HALF_UP);
            result = result.add(
                rule.tierFor(notional).maintenanceMargin(notional)
            );
        }
        if (shortQuantity.signum() > 0)
        {
            BigDecimal notional = markPrice.multiply(shortQuantity)
                .setScale(PaperTradingMath.MONEY_SCALE, RoundingMode.HALF_UP);
            result = result.add(
                rule.tierFor(notional).maintenanceMargin(notional)
            );
        }
        return result.setScale(
            PaperTradingMath.MONEY_SCALE,
            RoundingMode.CEILING
        );
    }

    private static AccountRiskState riskState(
        List<PaperTrade> trades,
        BigDecimal equity,
        BigDecimal maintenanceMargin,
        BigDecimal mmr
    )
    {
        if (trades.isEmpty())
        {
            return AccountRiskState.NO_POSITIONS;
        }
        if (equity.compareTo(maintenanceMargin) <= 0)
        {
            return AccountRiskState.LIQUIDATABLE;
        }
        if (mmr != null && mmr.compareTo(WARNING_MMR_PERCENT) >= 0)
        {
            return AccountRiskState.WARNING;
        }
        return AccountRiskState.HEALTHY;
    }

    private static PriceRoots bankruptcyRoots(
        BigDecimal walletBalance,
        BigDecimal longQuantity,
        BigDecimal shortQuantity,
        BigDecimal longEntryCost,
        BigDecimal shortEntryCost,
        BigDecimal currentMark
    )
    {
        BigDecimal slope = longQuantity.subtract(shortQuantity);
        if (slope.signum() == 0)
        {
            return PriceRoots.NONE;
        }
        BigDecimal constant = walletBalance
            .subtract(longEntryCost)
            .add(shortEntryCost);
        BigDecimal root = constant.negate().divide(
            slope,
            PaperTradingMath.PRICE_SCALE,
            RoundingMode.HALF_UP
        );
        if (root.signum() <= 0)
        {
            return PriceRoots.NONE;
        }
        if (root.compareTo(currentMark) <= 0)
        {
            return new PriceRoots(root, null);
        }
        return new PriceRoots(null, root);
    }

    private static PriceRoots liquidationRoots(
        BigDecimal walletBalance,
        BigDecimal longQuantity,
        BigDecimal shortQuantity,
        BigDecimal longEntryCost,
        BigDecimal shortEntryCost,
        BigDecimal currentMark,
        PerpetualRuleVersion rule
    )
    {
        if (longQuantity.signum() == 0 && shortQuantity.signum() == 0)
        {
            return PriceRoots.NONE;
        }

        TreeSet<BigDecimal> breakpoints = new TreeSet<>();
        breakpoints.add(BigDecimal.ZERO);
        addBreakpoints(breakpoints, longQuantity, rule);
        addBreakpoints(breakpoints, shortQuantity, rule);
        List<BigDecimal> points = new ArrayList<>(breakpoints);
        List<BigDecimal> roots = new ArrayList<>();

        for (int index = 0; index < points.size(); index++)
        {
            BigDecimal lower = points.get(index);
            BigDecimal upper =
                index + 1 < points.size() ? points.get(index + 1) : null;
            BigDecimal sample = upper == null
                ? lower.add(BigDecimal.ONE)
                : lower.add(upper).divide(
                    BigDecimal.valueOf(2),
                    CALCULATION_CONTEXT
                );
            TierTerms longTerms = tierTerms(
                longQuantity,
                sample,
                rule
            );
            TierTerms shortTerms = tierTerms(
                shortQuantity,
                sample,
                rule
            );
            BigDecimal constant = walletBalance
                .subtract(longEntryCost)
                .add(shortEntryCost)
                .add(longTerms.maintenanceAmount())
                .add(shortTerms.maintenanceAmount());
            BigDecimal slope = longQuantity
                .subtract(shortQuantity)
                .subtract(
                    longQuantity.multiply(
                        longTerms.maintenanceRate(),
                        CALCULATION_CONTEXT
                    )
                )
                .subtract(
                    shortQuantity.multiply(
                        shortTerms.maintenanceRate(),
                        CALCULATION_CONTEXT
                    )
                );
            if (slope.signum() == 0)
            {
                continue;
            }
            BigDecimal root = constant.negate().divide(
                slope,
                PaperTradingMath.PRICE_SCALE,
                RoundingMode.HALF_UP
            );
            if (root.signum() <= 0 || root.compareTo(lower) < 0)
            {
                continue;
            }
            if (upper != null && root.compareTo(upper) > 0)
            {
                continue;
            }
            if (roots.stream().noneMatch(value ->
                value.compareTo(root) == 0
            ))
            {
                roots.add(root);
            }
        }

        BigDecimal lower = roots.stream()
            .filter(root -> root.compareTo(currentMark) <= 0)
            .max(Comparator.naturalOrder())
            .orElse(null);
        BigDecimal upper = roots.stream()
            .filter(root -> root.compareTo(currentMark) >= 0)
            .min(Comparator.naturalOrder())
            .orElse(null);
        return new PriceRoots(lower, upper);
    }

    private static void addBreakpoints(
        TreeSet<BigDecimal> breakpoints,
        BigDecimal quantity,
        PerpetualRuleVersion rule
    )
    {
        if (quantity.signum() == 0)
        {
            return;
        }
        for (MaintenanceMarginTier tier : rule.maintenanceTiers())
        {
            if (tier.notionalFloor().signum() > 0)
            {
                breakpoints.add(
                    tier.notionalFloor().divide(
                        quantity,
                        PaperTradingMath.PRICE_SCALE,
                        RoundingMode.HALF_UP
                    )
                );
            }
        }
    }

    private static TierTerms tierTerms(
        BigDecimal quantity,
        BigDecimal price,
        PerpetualRuleVersion rule
    )
    {
        if (quantity.signum() == 0)
        {
            return TierTerms.ZERO;
        }
        MaintenanceMarginTier tier = rule.tierFor(
            quantity.multiply(price)
        );
        return new TierTerms(
            tier.maintenanceMarginRate(),
            tier.maintenanceAmountUsd()
        );
    }

    private static BigDecimal moneyZero()
    {
        return BigDecimal.ZERO.setScale(PaperTradingMath.MONEY_SCALE);
    }

    private record TierTerms(
        BigDecimal maintenanceRate,
        BigDecimal maintenanceAmount
    )
    {
        private static final TierTerms ZERO = new TierTerms(
            BigDecimal.ZERO,
            BigDecimal.ZERO
        );
    }

    private record PriceRoots(BigDecimal lower, BigDecimal upper)
    {
        private static final PriceRoots NONE =
            new PriceRoots(null, null);
    }
}
