package com.raretable.casino.paper_trading.trading;

import java.util.UUID;

record TriggeredPaperTrade(
    UUID id,
    UUID userId,
    TradeCloseReason reason,
    long riskControlVersion
)
{
}
