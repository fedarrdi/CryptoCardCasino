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
    BigDecimal grossUnrealizedPnl,
    BigDecimal estimatedNetPnl,
    BigDecimal entryFee,
    BigDecimal estimatedExitFee,
    BigDecimal fundingPnl,
    BigDecimal breakEvenPrice,
    BigDecimal bankruptcyPrice,
    BigDecimal estimatedLiquidationPrice,
    BigDecimal maintenanceMargin,
    BigDecimal maintenanceMarginRate,
    BigDecimal estimatedClosePrice,
    BigDecimal estimatedCloseGrossPnl,
    BigDecimal estimatedCloseNetPnl,
    BigDecimal stopLoss,
    BigDecimal takeProfit,
    Instant openedAt,
    BigDecimal exitPrice,
    BigDecimal realizedPnl,
    BigDecimal grossRealizedPnl,
    BigDecimal exitFee,
    BigDecimal liquidationFee,
    Instant closedAt,
    TradeCloseReason closeReason
)
{
}
