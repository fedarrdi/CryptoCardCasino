package com.raretable.casino.paper_trading.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.raretable.casino.paper_trading.trading.PaperTradingOperations;
import com.raretable.casino.paper_trading.trading.PaperTradingService;
import com.raretable.casino.paper_trading.trading.PaperTradingSessionMismatchException;
import com.raretable.casino.security.WalletPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/paper-trading")
public final class PaperTradingPositionsController
{
    public static final String EXPECTED_USER_HEADER =
        "X-Paper-Trading-User-Id";

    private final PaperTradingOperations tradingService;

    public PaperTradingPositionsController(PaperTradingOperations tradingService)
    {
        this.tradingService = tradingService;
    }

    @GetMapping("/portfolio")
    public PaperTradingPortfolioResponse getPortfolio(
        @RequestParam(
            name = "closedTradeLimit",
            defaultValue = "50"
        )
        @Min(1)
        @Max(PaperTradingService.MAX_CLOSED_TRADE_LIMIT)
        int closedTradeLimit,
        @AuthenticationPrincipal WalletPrincipal principal
    )
    {
        return tradingService.getPortfolio(
            principal.userId(),
            closedTradeLimit
        );
    }

    @PostMapping("/positions")
    @ResponseStatus(HttpStatus.CREATED)
    public PaperTradingPortfolioResponse openPosition(
        @Valid @RequestBody OpenPositionRequest request,
        @RequestHeader(EXPECTED_USER_HEADER) UUID expectedUserId,
        @AuthenticationPrincipal WalletPrincipal principal
    )
    {
        UUID userId = requireExpectedUser(principal, expectedUserId);
        return tradingService.openPosition(userId, request);
    }

    @PatchMapping("/positions/{positionId}/risk-controls")
    public PaperTradingPortfolioResponse updateRiskControls(
        @PathVariable UUID positionId,
        @Valid @RequestBody UpdateRiskControlsRequest request,
        @RequestHeader(EXPECTED_USER_HEADER) UUID expectedUserId,
        @AuthenticationPrincipal WalletPrincipal principal
    )
    {
        UUID userId = requireExpectedUser(principal, expectedUserId);
        return tradingService.updateRiskControls(
            userId,
            positionId,
            request
        );
    }

    @PostMapping("/positions/{positionId}/close")
    public PaperTradingPortfolioResponse closePosition(
        @PathVariable UUID positionId,
        @RequestHeader(EXPECTED_USER_HEADER) UUID expectedUserId,
        @AuthenticationPrincipal WalletPrincipal principal
    )
    {
        UUID userId = requireExpectedUser(principal, expectedUserId);
        return tradingService.closePosition(
            userId,
            positionId
        );
    }

    private static UUID requireExpectedUser(
        WalletPrincipal principal,
        UUID expectedUserId
    )
    {
        if (!principal.userId().equals(expectedUserId))
        {
            throw new PaperTradingSessionMismatchException();
        }
        return principal.userId();
    }
}
