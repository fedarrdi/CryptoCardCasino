package com.raretable.casino.paper_trading.api;

import java.util.List;
import java.util.UUID;

public record PaperTradingPortfolioResponse(
    UUID userId,
    TradingQuoteResponse quote,
    TradingAccountResponse account,
    List<PaperPositionResponse> openPositions,
    List<PaperPositionResponse> closedTrades
)
{
}
