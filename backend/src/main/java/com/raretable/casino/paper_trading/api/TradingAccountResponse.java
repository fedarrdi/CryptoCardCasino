package com.raretable.casino.paper_trading.api;

import java.math.BigDecimal;

public record TradingAccountResponse(
    BigDecimal initialBalance,
    BigDecimal balance,
    BigDecimal equity,
    BigDecimal unrealizedPnl,
    BigDecimal usedMargin,
    BigDecimal availableMargin
)
{
}
