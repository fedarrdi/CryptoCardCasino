package com.raretable.casino.paper_trading.api;

import java.math.BigDecimal;

public record TradingQuoteResponse(
    String symbol,
    BigDecimal bidPrice,
    BigDecimal askPrice
)
{
}
