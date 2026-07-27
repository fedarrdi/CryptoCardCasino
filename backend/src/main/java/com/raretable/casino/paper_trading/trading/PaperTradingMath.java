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
