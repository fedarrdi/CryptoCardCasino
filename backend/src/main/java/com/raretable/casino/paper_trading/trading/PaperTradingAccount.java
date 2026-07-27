package com.raretable.casino.paper_trading.trading;

import java.math.BigDecimal;
import java.util.UUID;

public record PaperTradingAccount(
    UUID userId,
    BigDecimal balanceUsd
)
{
}
