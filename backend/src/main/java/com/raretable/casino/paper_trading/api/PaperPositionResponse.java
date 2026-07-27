package com.raretable.casino.paper_trading.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.raretable.casino.paper_trading.trading.TradeCloseReason;
import com.raretable.casino.paper_trading.trading.TradeSide;
import com.raretable.casino.paper_trading.trading.TradeStatus;

public record PaperPositionResponse(
    UUID id,
    UUID clientOrderId,
    String symbol,
    String orderType,
    String marginMode,
    TradeSide side,
    TradeStatus status,
    int leverage,
    BigDecimal marginUsd,
    BigDecimal notionalUsd,
    BigDecimal quantity,
    BigDecimal entryPrice,
    BigDecimal markPrice,
    BigDecimal unrealizedPnl,
    BigDecimal unrealizedRoePercent,
    BigDecimal stopLoss,
    BigDecimal takeProfit,
    Instant openedAt,
    BigDecimal exitPrice,
    BigDecimal realizedPnl,
    Instant closedAt,
    TradeCloseReason closeReason
)
{
}
