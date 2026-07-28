package com.raretable.casino.paper_trading.trading;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PaperTradingMath
{
    private static final int DECIMAL_PRECISION = 38;
    public static final int MONEY_SCALE = 8;
    public static final int PRICE_SCALE = 12;
    public static final int QUANTITY_SCALE = 12;
    public static final int PERCENT_SCALE = 4;

    private PaperTradingMath()
    {
    }

    public static BigDecimal money(BigDecimal value, String field)
    {
        return exactScale(value, MONEY_SCALE, field);
    }

    public static BigDecimal optionalPrice(BigDecimal value, String field)
    {
        return value == null ? null : positivePrice(value, field);
    }

    public static BigDecimal positivePrice(BigDecimal value, String field)
    {
        BigDecimal price = exactScale(value, PRICE_SCALE, field);
        if (price.signum() <= 0)
        {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return price;
    }

    public static BigDecimal quantity(
        BigDecimal notional,
        BigDecimal entryPrice
    )
    {
        return notional.divide(
            entryPrice,
            QUANTITY_SCALE,
            RoundingMode.HALF_UP
        );
    }

    public static BigDecimal pnl(
        TradeSide side,
        BigDecimal entryPrice,
        BigDecimal exitPrice,
        BigDecimal quantity
    )
    {
        return side.rawPnl(entryPrice, exitPrice, quantity)
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal notional(
        BigDecimal price,
        BigDecimal quantity
    )
    {
        return price.multiply(quantity)
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal fee(
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal rate
    )
    {
        return price.multiply(quantity)
            .multiply(rate)
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal reservedFee(
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal rate
    )
    {
        return price.multiply(quantity)
            .multiply(rate)
            .setScale(MONEY_SCALE, RoundingMode.CEILING);
    }

    public static BigDecimal maintenanceMarginRatioPercent(
        BigDecimal maintenanceMargin,
        BigDecimal equity
    )
    {
        if (maintenanceMargin.signum() == 0)
        {
            return null;
        }
        if (equity.signum() <= 0)
        {
            return null;
        }
        return maintenanceMargin.multiply(BigDecimal.valueOf(100))
            .divide(equity, PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal fundingPnl(
        TradeSide side,
        BigDecimal quantity,
        BigDecimal settlementMarkPrice,
        BigDecimal fundingRate
    )
    {
        BigDecimal unsigned = quantity.multiply(settlementMarkPrice)
            .multiply(fundingRate);
        if (side == TradeSide.LONG)
        {
            unsigned = unsigned.negate();
        }
        return unsigned.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal breakEvenPrice(
        PaperTrade trade,
        BigDecimal takerFeeRate
    )
    {
        BigDecimal quantity = trade.quantity();
        BigDecimal entryCost = trade.entryPrice().multiply(quantity);
        BigDecimal numerator;
        BigDecimal denominator;
        if (trade.side() == TradeSide.LONG)
        {
            numerator = entryCost
                .add(trade.entryFee())
                .subtract(trade.fundingPnl());
            denominator = quantity.multiply(
                BigDecimal.ONE.subtract(takerFeeRate)
            );
        }
        else
        {
            numerator = entryCost
                .subtract(trade.entryFee())
                .add(trade.fundingPnl());
            denominator = quantity.multiply(
                BigDecimal.ONE.add(takerFeeRate)
            );
        }
        return numerator.divide(
            denominator,
            PRICE_SCALE,
            RoundingMode.HALF_UP
        );
    }

    public static BigDecimal moneyRounded(BigDecimal value)
    {
        if (value == null)
        {
            throw new IllegalArgumentException("Money value is required");
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal priceRounded(BigDecimal value)
    {
        if (value == null)
        {
            throw new IllegalArgumentException("Price value is required");
        }
        return value.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal roePercent(
        BigDecimal pnl,
        BigDecimal margin
    )
    {
        return pnl.multiply(BigDecimal.valueOf(100))
            .divide(margin, PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal exactScale(
        BigDecimal value,
        int scale,
        String field
    )
    {
        if (value == null)
        {
            throw new IllegalArgumentException(field + " is required");
        }
        if (value.stripTrailingZeros().scale() > scale)
        {
            throw new IllegalArgumentException(
                field + " supports at most " + scale + " decimal places"
            );
        }
        BigDecimal normalized = value.setScale(scale);
        if (normalized.precision() > DECIMAL_PRECISION)
        {
            throw new IllegalArgumentException(
                field + " exceeds the supported numeric range"
            );
        }
        return normalized;
    }
}
