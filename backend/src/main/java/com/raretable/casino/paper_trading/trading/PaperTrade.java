package com.raretable.casino.paper_trading.trading;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaperTrade(
    UUID id,
    UUID userId,
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
    BigDecimal exitPrice,
    BigDecimal stopLoss,
    BigDecimal takeProfit,
    long riskControlVersion,
    long ruleVersionId,
    Instant fundingEligibleFrom,
    BigDecimal entryFeeRate,
    BigDecimal exitFeeRate,
    BigDecimal entryFee,
    BigDecimal exitFee,
    BigDecimal liquidationFee,
    BigDecimal fundingPnl,
    BigDecimal grossRealizedPnl,
    BigDecimal realizedPnl,
    TradeCloseReason closeReason,
    Instant openedAt,
    Instant closedAt
)
{
    public TradeCloseReason triggeredReason(BigDecimal closingPrice)
    {
        if (side.stopLossTriggered(closingPrice, stopLoss))
        {
            return TradeCloseReason.STOP_LOSS;
        }
        if (side.takeProfitTriggered(closingPrice, takeProfit))
        {
            return TradeCloseReason.TAKE_PROFIT;
        }
        return null;
    }
}
