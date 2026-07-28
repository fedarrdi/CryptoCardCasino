package com.raretable.casino.paper_trading.trading;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.raretable.casino.paper_trading.api.OpenPositionRequest;
import com.raretable.casino.paper_trading.api.PaperPositionPreviewResponse;
import com.raretable.casino.paper_trading.api.PaperTradingPortfolioResponse;
import com.raretable.casino.paper_trading.api.PreviewPositionRequest;
import com.raretable.casino.paper_trading.api.UpdateRiskControlsRequest;

public interface PaperTradingOperations
{
    PaperTradingPortfolioResponse getPortfolio(
        UUID userId,
        int closedTradeLimit
    );

    PaperTradingPortfolioResponse openPosition(
        UUID userId,
        OpenPositionRequest request
    );

    PaperPositionPreviewResponse previewPosition(
        UUID userId,
        PreviewPositionRequest request
    );

    PaperTradingPortfolioResponse updateRiskControls(
        UUID userId,
        UUID positionId,
        UpdateRiskControlsRequest request
    );

    PaperTradingPortfolioResponse closePosition(
        UUID userId,
        UUID positionId
    );

    void processRiskControls(
        BigDecimal observedTradePrice,
        Instant observedAt
    );

    void processLiquidations(
        BigDecimal observedMarkPrice,
        Instant observedAt
    );
}
