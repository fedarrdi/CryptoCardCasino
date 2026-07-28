package com.raretable.casino.paper_trading.trading;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.raretable.casino.paper_trading.api.OpenPositionRequest;
import com.raretable.casino.paper_trading.api.PaperPositionPreviewResponse;
import com.raretable.casino.paper_trading.api.PaperPositionResponse;
import com.raretable.casino.paper_trading.api.PaperTradingPortfolioResponse;
import com.raretable.casino.paper_trading.api.PreviewPositionRequest;
import com.raretable.casino.paper_trading.api.TradingAccountResponse;
import com.raretable.casino.paper_trading.api.TradingQuoteResponse;
import com.raretable.casino.paper_trading.api.UpdateRiskControlsRequest;
import com.raretable.casino.paper_trading.price.BinanceWrapper;
import com.raretable.casino.paper_trading.price.BtcPerpetualMarketSnapshot;

@Service
public final class PaperTradingService implements PaperTradingOperations
{
    public static final int DEFAULT_CLOSED_TRADE_LIMIT = 50;
    public static final int MAX_CLOSED_TRADE_LIMIT = 100;
    public static final BigDecimal INITIAL_BALANCE =
        new BigDecimal("10000.00000000");
    private static final String SYMBOL = "BTCUSDT";
    private static final String PRODUCT_TYPE = "USD_M_PERPETUAL";
    private static final String ORDER_TYPE = "MARKET";
    private static final String MARGIN_MODE = "CROSS";
    private static final UUID PREVIEW_TRADE_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PREVIEW_ORDER_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final JdbcPaperTradingRepository repository;
    private final JdbcPerpetualRuleRepository ruleRepository;
    private final PaperTradingRiskCalculator riskCalculator;
    private final BinanceWrapper binance;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public PaperTradingService(
        JdbcPaperTradingRepository repository,
        JdbcPerpetualRuleRepository ruleRepository,
        PaperTradingRiskCalculator riskCalculator,
        BinanceWrapper binance,
        TransactionTemplate transactions,
        Clock clock
    )
    {
        this.repository = repository;
        this.ruleRepository = ruleRepository;
        this.riskCalculator = riskCalculator;
        this.binance = binance;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public PaperTradingPortfolioResponse getPortfolio(
        UUID userId,
        int closedTradeLimit
    )
    {
        requireUser(userId);
        requireClosedTradeLimit(closedTradeLimit);
        return inTransaction(() -> {
            PaperTradingAccount account = repository.lockAccount(userId);
            return portfolio(
                account,
                binance.getBtcPerpetualMarketSnapshot(),
                closedTradeLimit
            );
        });
    }

    @Override
    public PaperPositionPreviewResponse previewPosition(
        UUID userId,
        PreviewPositionRequest request
    )
    {
        requireUser(userId);
        OpenCommand command = validatePreviewRequest(request);
        return inTransaction(() -> {
            PaperTradingAccount account = repository.lockAccount(userId);
            BtcPerpetualMarketSnapshot market =
                binance.getBtcPerpetualMarketSnapshot();
            PerpetualRuleVersion rule =
                ruleRepository.findActive(SYMBOL, clock.instant());
            List<PaperTrade> openTrades = repository.findOpenTrades(userId);
            OrderProjection projection = projectOrder(
                account,
                openTrades,
                command,
                market,
                rule
            );
            requireAdmissible(command, projection);
            BigDecimal maxOrderMargin = maximumOrderMargin(
                account,
                openTrades,
                command.side(),
                command.leverage(),
                market,
                rule
            );
            AccountRiskMetrics risk = projection.accountRisk();
            MaintenanceMarginTier tier = projection.tier();
            BigDecimal liquidationPrice =
                directionalLiquidationPrice(command.side(), risk);
            BigDecimal bankruptcyPrice =
                directionalBankruptcyPrice(command.side(), risk);
            return new PaperPositionPreviewResponse(
                projection.trade().entryPrice(),
                projection.trade().notionalUsd(),
                projection.trade().quantity(),
                projection.trade().entryFee(),
                projection.estimatedExitFee(),
                PaperTradingMath.breakEvenPrice(
                    projection.trade(),
                    rule.takerFeeRate()
                ),
                bankruptcyPrice,
                liquidationPrice,
                risk.lowerBankruptcyPrice(),
                risk.upperBankruptcyPrice(),
                risk.estimatedLowerLiquidationPrice(),
                risk.estimatedUpperLiquidationPrice(),
                risk.maintenanceMargin(),
                tier.maintenanceMarginRate(),
                risk.maintenanceMarginRatioPercent(),
                risk.availableMargin(),
                maxOrderMargin,
                rule.takerFeeRate(),
                rule.liquidationFeeRate(),
                rule.liquidationMode(),
                rule.negativeBalancePolicy(),
                rule.stopTriggerPriceType(),
                rule.code(),
                earliestPricingTime(market)
            );
        });
    }

    @Override
    public PaperTradingPortfolioResponse openPosition(
        UUID userId,
        OpenPositionRequest request
    )
    {
        requireUser(userId);
        OpenCommand command = validateOpenRequest(request);

        return inTransaction(() -> {
            PaperTradingAccount account = repository.lockAccount(userId);
            BtcPerpetualMarketSnapshot market =
                binance.getBtcPerpetualMarketSnapshot();
            PaperTrade existing = repository.findByClientOrderId(
                userId,
                command.clientOrderId()
            ).orElse(null);
            if (existing != null)
            {
                requireSameOpenIntent(existing, command);
                return portfolio(
                    account,
                    market,
                    DEFAULT_CLOSED_TRADE_LIMIT
                );
            }

            Instant openedAt = clock.instant();
            PerpetualRuleVersion rule =
                ruleRepository.findActive(SYMBOL, openedAt);
            List<PaperTrade> openTrades = repository.findOpenTrades(userId);
            OrderProjection projection = projectOrder(
                account,
                openTrades,
                command,
                market,
                rule
            );
            requireAdmissible(command, projection);

            PaperTrade projected = projection.trade();
            PaperTrade trade = new PaperTrade(
                UUID.randomUUID(),
                userId,
                command.clientOrderId(),
                projected.symbol(),
                projected.orderType(),
                projected.marginMode(),
                projected.side(),
                projected.status(),
                projected.leverage(),
                projected.marginUsd(),
                projected.notionalUsd(),
                projected.quantity(),
                projected.entryPrice(),
                null,
                projected.stopLoss(),
                projected.takeProfit(),
                0,
                rule.id(),
                openedAt,
                rule.takerFeeRate(),
                null,
                projected.entryFee(),
                moneyZero(),
                moneyZero(),
                moneyZero(),
                null,
                null,
                null,
                openedAt,
                null
            );
            repository.insert(trade);

            BigDecimal updatedBalance = account.balanceUsd()
                .subtract(trade.entryFee())
                .setScale(PaperTradingMath.MONEY_SCALE);
            repository.updateBalance(userId, updatedBalance, openedAt);
            repository.insertLedgerEntry(
                UUID.randomUUID(),
                userId,
                trade.id(),
                LedgerEventType.ENTRY_FEE,
                trade.entryFee().negate(),
                updatedBalance,
                openedAt,
                "trade:" + trade.id() + ":entry-fee"
            );
            return portfolio(
                new PaperTradingAccount(userId, updatedBalance),
                market,
                DEFAULT_CLOSED_TRADE_LIMIT
            );
        });
    }

    @Override
    public PaperTradingPortfolioResponse updateRiskControls(
        UUID userId,
        UUID positionId,
        UpdateRiskControlsRequest request
    )
    {
        requireUser(userId);
        requirePosition(positionId);
        if (request == null)
        {
            throw new IllegalArgumentException(
                "Risk-control request is required"
            );
        }
        BigDecimal stopLoss = PaperTradingMath.optionalPrice(
            request.stopLoss(),
            "Stop loss"
        );
        BigDecimal takeProfit = PaperTradingMath.optionalPrice(
            request.takeProfit(),
            "Take profit"
        );

        return inTransaction(() -> {
            PaperTradingAccount account = repository.lockAccount(userId);
            PaperTrade trade = repository.lockTrade(positionId, userId)
                .orElseThrow(() ->
                    new PaperPositionNotFoundException(positionId)
                );
            requireOpen(trade);
            BtcPerpetualMarketSnapshot market =
                binance.getBtcPerpetualMarketSnapshot();
            trade.side().validateRiskControls(
                market.lastPrice(),
                stopLoss,
                takeProfit
            );
            repository.updateRiskControls(
                trade,
                stopLoss,
                takeProfit,
                clock.instant()
            );
            return portfolio(
                account,
                market,
                DEFAULT_CLOSED_TRADE_LIMIT
            );
        });
    }

    @Override
    public PaperTradingPortfolioResponse closePosition(
        UUID userId,
        UUID positionId
    )
    {
        requireUser(userId);
        requirePosition(positionId);

        return inTransaction(() -> {
            PaperTradingAccount account = repository.lockAccount(userId);
            PaperTrade trade = repository.lockTrade(positionId, userId)
                .orElseThrow(() ->
                    new PaperPositionNotFoundException(positionId)
                );
            requireOpen(trade);
            Instant requestedAt = clock.instant();
            List<PaperTrade> openTrades = repository.lockOpenTrades(
                userId,
                requestedAt
            );
            BtcPerpetualMarketSnapshot market =
                binance.getBtcPerpetualMarketSnapshot();
            PerpetualRuleVersion rule =
                ruleRepository.findActive(SYMBOL, requestedAt);
            AccountRiskMetrics risk = riskCalculator.calculate(
                account.balanceUsd(),
                openTrades,
                market,
                rule
            );
            if (risk.riskState() == AccountRiskState.LIQUIDATABLE)
            {
                BigDecimal liquidatedBalance = liquidateAccount(
                    account,
                    openTrades,
                    market,
                    rule,
                    risk,
                    market.markPrice(),
                    market.markPriceTime()
                );
                return portfolio(
                    new PaperTradingAccount(userId, liquidatedBalance),
                    market,
                    DEFAULT_CLOSED_TRADE_LIMIT
                );
            }
            BigDecimal updatedBalance = closeTrade(
                account.balanceUsd(),
                trade,
                market,
                TradeCloseReason.USER,
                trade.entryFeeRate(),
                BigDecimal.ZERO
            );
            updatedBalance = floorNegativeBalance(
                userId,
                updatedBalance,
                "trade:" + trade.id() + ":insurance"
            );
            return portfolio(
                new PaperTradingAccount(userId, updatedBalance),
                market,
                DEFAULT_CLOSED_TRADE_LIMIT
            );
        });
    }

    @Override
    public void processRiskControls(
        BigDecimal observedTradePrice,
        Instant observedAt
    )
    {
        BigDecimal signalPrice = PaperTradingMath.positivePrice(
            observedTradePrice,
            "Observed BTC last price"
        );
        requireObservationTime(observedAt);
        List<TriggeredPaperTrade> candidates =
            repository.findTriggeredTrades(signalPrice, observedAt);

        for (TriggeredPaperTrade candidate : candidates)
        {
            inTransaction(() -> {
                PaperTradingAccount account =
                    repository.lockAccount(candidate.userId());
                PaperTrade trade = repository
                    .lockTrade(candidate.id(), candidate.userId())
                    .orElse(null);
                if (trade == null || trade.status() != TradeStatus.OPEN)
                {
                    return null;
                }
                BtcPerpetualMarketSnapshot market =
                    binance.getBtcPerpetualMarketSnapshot();
                Instant evaluatedAt = clock.instant();
                List<PaperTrade> openTrades = repository.lockOpenTrades(
                    candidate.userId(),
                    evaluatedAt
                );
                PerpetualRuleVersion rule =
                    ruleRepository.findActive(SYMBOL, evaluatedAt);
                AccountRiskMetrics risk = riskCalculator.calculate(
                    account.balanceUsd(),
                    openTrades,
                    market,
                    rule
                );
                if (risk.riskState() == AccountRiskState.LIQUIDATABLE)
                {
                    liquidateAccount(
                        account,
                        openTrades,
                        market,
                        rule,
                        risk,
                        market.markPrice(),
                        market.markPriceTime()
                    );
                    return null;
                }
                BigDecimal updatedBalance = closeTrade(
                    account.balanceUsd(),
                    trade,
                    market,
                    candidate.reason(),
                    trade.entryFeeRate(),
                    BigDecimal.ZERO
                );
                floorNegativeBalance(
                    candidate.userId(),
                    updatedBalance,
                    "trade:" + trade.id() + ":insurance"
                );
                return null;
            });
        }
    }

    @Override
    public void processLiquidations(
        BigDecimal observedMarkPrice,
        Instant observedAt
    )
    {
        BigDecimal markPrice = PaperTradingMath.positivePrice(
            observedMarkPrice,
            "Observed BTC mark price"
        );
        requireObservationTime(observedAt);

        for (
            UUID userId
                : repository.findUserIdsWithOpenTrades(observedAt)
        )
        {
            inTransaction(() -> {
                PaperTradingAccount account = repository.lockAccount(userId);
                List<PaperTrade> openTrades =
                    repository.lockOpenTrades(userId, observedAt);
                if (openTrades.isEmpty())
                {
                    return null;
                }
                BtcPerpetualMarketSnapshot liveMarket =
                    binance.getBtcPerpetualMarketSnapshot();
                BtcPerpetualMarketSnapshot riskMarket =
                    withObservedMark(liveMarket, markPrice, observedAt);
                PerpetualRuleVersion rule =
                    ruleRepository.findActive(SYMBOL, observedAt);
                AccountRiskMetrics risk = riskCalculator.calculate(
                    account.balanceUsd(),
                    openTrades,
                    riskMarket,
                    rule
                );
                if (risk.riskState() != AccountRiskState.LIQUIDATABLE)
                {
                    return null;
                }

                liquidateAccount(
                    account,
                    openTrades,
                    liveMarket,
                    rule,
                    risk,
                    markPrice,
                    observedAt
                );
                return null;
            });
        }
    }

    private BigDecimal closeTrade(
        BigDecimal walletBalance,
        PaperTrade trade,
        BtcPerpetualMarketSnapshot market,
        TradeCloseReason reason,
        BigDecimal exitFeeRate,
        BigDecimal liquidationFeeRate
    )
    {
        BigDecimal exitPrice =
            trade.side().closingPrice(market.quote());
        BigDecimal grossPnl = PaperTradingMath.pnl(
            trade.side(),
            trade.entryPrice(),
            exitPrice,
            trade.quantity()
        );
        boolean liquidation = reason == TradeCloseReason.LIQUIDATION;
        BigDecimal exitFee = PaperTradingMath.fee(
            exitPrice,
            trade.quantity(),
            exitFeeRate
        );
        BigDecimal liquidationFee = liquidation
            ? PaperTradingMath.fee(
                exitPrice,
                trade.quantity(),
                liquidationFeeRate
            )
            : moneyZero();
        BigDecimal netPnl = grossPnl
            .subtract(trade.entryFee())
            .subtract(exitFee)
            .subtract(liquidationFee)
            .add(trade.fundingPnl())
            .setScale(PaperTradingMath.MONEY_SCALE, RoundingMode.HALF_UP);
        Instant closedAt = clock.instant();

        BigDecimal afterGross = walletBalance.add(grossPnl)
            .setScale(PaperTradingMath.MONEY_SCALE);
        repository.updateBalance(trade.userId(), afterGross, closedAt);
        repository.insertLedgerEntry(
            UUID.randomUUID(),
            trade.userId(),
            trade.id(),
            LedgerEventType.REALIZED_PNL,
            grossPnl,
            afterGross,
            closedAt,
            "trade:" + trade.id() + ":realized-pnl"
        );

        BigDecimal afterExitFee = afterGross.subtract(exitFee)
            .setScale(PaperTradingMath.MONEY_SCALE);
        if (exitFee.signum() > 0)
        {
            repository.updateBalance(
                trade.userId(),
                afterExitFee,
                closedAt
            );
            repository.insertLedgerEntry(
                UUID.randomUUID(),
                trade.userId(),
                trade.id(),
                LedgerEventType.EXIT_FEE,
                exitFee.negate(),
                afterExitFee,
                closedAt,
                "trade:" + trade.id() + ":exit-fee"
            );
        }
        BigDecimal finalBalance = afterExitFee.subtract(liquidationFee)
            .setScale(PaperTradingMath.MONEY_SCALE);
        if (liquidationFee.signum() > 0)
        {
            repository.updateBalance(
                trade.userId(),
                finalBalance,
                closedAt
            );
            repository.insertLedgerEntry(
                UUID.randomUUID(),
                trade.userId(),
                trade.id(),
                LedgerEventType.LIQUIDATION_FEE,
                liquidationFee.negate(),
                finalBalance,
                closedAt,
                "trade:" + trade.id() + ":liquidation-fee"
            );
        }
        repository.close(
            trade.id(),
            exitPrice,
            exitFeeRate,
            exitFee,
            liquidationFee,
            grossPnl,
            netPnl,
            reason,
            closedAt
        );
        return finalBalance;
    }

    private BigDecimal liquidateAccount(
        PaperTradingAccount account,
        List<PaperTrade> openTrades,
        BtcPerpetualMarketSnapshot market,
        PerpetualRuleVersion rule,
        AccountRiskMetrics risk,
        BigDecimal triggeringMarkPrice,
        Instant triggeredAt
    )
    {
        BigDecimal wallet = account.balanceUsd();
        for (PaperTrade trade : openTrades)
        {
            wallet = closeTrade(
                wallet,
                trade,
                market,
                TradeCloseReason.LIQUIDATION,
                trade.entryFeeRate(),
                rule.liquidationFeeRate()
            );
        }
        BigDecimal walletAfterCloses = wallet;
        BigDecimal insuranceCredit = wallet.signum() < 0
            ? wallet.negate().setScale(PaperTradingMath.MONEY_SCALE)
            : moneyZero();
        if (insuranceCredit.signum() > 0)
        {
            wallet = floorNegativeBalance(
                account.userId(),
                wallet,
                "liquidation:"
                    + triggeredAt.toEpochMilli()
                    + ":"
                    + account.userId()
                    + ":insurance"
            );
        }
        Instant now = clock.instant();
        Instant completedAt = now.isBefore(triggeredAt)
            ? triggeredAt
            : now;
        repository.insertLiquidationEvent(
            UUID.randomUUID(),
            account.userId(),
            rule.id(),
            triggeringMarkPrice,
            market.indexPrice(),
            market.lastPrice(),
            risk.equity(),
            risk.maintenanceMargin(),
            walletAfterCloses,
            insuranceCredit,
            triggeredAt,
            completedAt
        );
        return wallet;
    }

    private BigDecimal floorNegativeBalance(
        UUID userId,
        BigDecimal balance,
        String idempotencyKey
    )
    {
        if (balance.signum() >= 0)
        {
            return balance;
        }
        Instant creditedAt = clock.instant();
        BigDecimal insuranceCredit = balance.negate()
            .setScale(PaperTradingMath.MONEY_SCALE);
        BigDecimal flooredBalance = moneyZero();
        repository.updateBalance(userId, flooredBalance, creditedAt);
        repository.insertLedgerEntry(
            UUID.randomUUID(),
            userId,
            null,
            LedgerEventType.INSURANCE_CREDIT,
            insuranceCredit,
            flooredBalance,
            creditedAt,
            idempotencyKey
        );
        return flooredBalance;
    }

    private PaperTradingPortfolioResponse portfolio(
        PaperTradingAccount account,
        BtcPerpetualMarketSnapshot market,
        int closedTradeLimit
    )
    {
        List<PaperTrade> openTrades =
            repository.findOpenTrades(account.userId());
        List<PaperTrade> closedTrades =
            repository.findClosedTrades(account.userId(), closedTradeLimit);
        PerpetualRuleVersion rule =
            ruleRepository.findActive(SYMBOL, clock.instant());
        AccountRiskMetrics risk = riskCalculator.calculate(
            account.balanceUsd(),
            openTrades,
            market,
            rule
        );
        return new PaperTradingPortfolioResponse(
            account.userId(),
            quoteResponse(market),
            accountResponse(account, risk),
            openTrades.stream()
                .map(trade -> openPositionResponse(
                    trade,
                    openTrades,
                    market,
                    risk,
                    rule
                ))
                .toList(),
            closedTrades.stream()
                .map(PaperTradingService::closedPositionResponse)
                .toList()
        );
    }

    private static TradingQuoteResponse quoteResponse(
        BtcPerpetualMarketSnapshot market
    )
    {
        return new TradingQuoteResponse(
            SYMBOL,
            PRODUCT_TYPE,
            market.bidPrice(),
            market.askPrice(),
            market.lastPrice(),
            market.markPrice(),
            market.indexPrice(),
            market.fundingRate(),
            market.nextFundingTime(),
            market.bookTickerTime(),
            market.lastPriceTime(),
            market.markPriceTime()
        );
    }

    private static TradingAccountResponse accountResponse(
        PaperTradingAccount account,
        AccountRiskMetrics risk
    )
    {
        return new TradingAccountResponse(
            INITIAL_BALANCE,
            account.balanceUsd(),
            risk.equity(),
            risk.grossUnrealizedPnl(),
            risk.initialMargin(),
            risk.maintenanceMargin(),
            risk.estimatedClosingFee(),
            risk.availableMargin(),
            risk.maintenanceMarginRatioPercent(),
            risk.riskState(),
            risk.estimatedLowerLiquidationPrice(),
            risk.estimatedUpperLiquidationPrice()
        );
    }

    private static PaperPositionResponse openPositionResponse(
        PaperTrade trade,
        List<PaperTrade> openTrades,
        BtcPerpetualMarketSnapshot market,
        AccountRiskMetrics accountRisk,
        PerpetualRuleVersion rule
    )
    {
        BigDecimal grossMarkPnl = PaperTradingMath.pnl(
            trade.side(),
            trade.entryPrice(),
            market.markPrice(),
            trade.quantity()
        );
        BigDecimal closePrice =
            trade.side().closingPrice(market.quote());
        BigDecimal closeGrossPnl = PaperTradingMath.pnl(
            trade.side(),
            trade.entryPrice(),
            closePrice,
            trade.quantity()
        );
        BigDecimal estimatedExitFee = PaperTradingMath.reservedFee(
            closePrice,
            trade.quantity(),
            trade.entryFeeRate()
        );
        BigDecimal estimatedNetPnl = closeGrossPnl
            .subtract(trade.entryFee())
            .subtract(estimatedExitFee)
            .add(trade.fundingPnl())
            .setScale(PaperTradingMath.MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal sameSideQuantity = openTrades.stream()
            .filter(openTrade -> openTrade.side() == trade.side())
            .map(PaperTrade::quantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sameSideNotional = PaperTradingMath.notional(
            market.markPrice(),
            sameSideQuantity
        );
        MaintenanceMarginTier tier = rule.tierFor(sameSideNotional);
        BigDecimal sideMaintenance =
            tier.maintenanceMargin(sameSideNotional);
        BigDecimal allocatedMaintenance = sideMaintenance
            .multiply(trade.quantity())
            .divide(
                sameSideQuantity,
                PaperTradingMath.MONEY_SCALE,
                RoundingMode.HALF_UP
            );
        return new PaperPositionResponse(
            trade.id(),
            trade.clientOrderId(),
            trade.symbol(),
            trade.orderType(),
            trade.marginMode(),
            trade.side(),
            trade.status(),
            trade.leverage(),
            trade.marginUsd(),
            trade.notionalUsd(),
            trade.quantity(),
            trade.entryPrice(),
            market.markPrice(),
            grossMarkPnl,
            PaperTradingMath.roePercent(grossMarkPnl, trade.marginUsd()),
            grossMarkPnl,
            estimatedNetPnl,
            trade.entryFee(),
            estimatedExitFee,
            trade.fundingPnl(),
            PaperTradingMath.breakEvenPrice(
                trade,
                trade.entryFeeRate()
            ),
            directionalBankruptcyPrice(trade.side(), accountRisk),
            directionalLiquidationPrice(trade.side(), accountRisk),
            allocatedMaintenance,
            tier.maintenanceMarginRate(),
            closePrice,
            closeGrossPnl,
            estimatedNetPnl,
            trade.stopLoss(),
            trade.takeProfit(),
            trade.openedAt(),
            null,
            null,
            null,
            moneyZero(),
            moneyZero(),
            null,
            null
        );
    }

    private static PaperPositionResponse closedPositionResponse(
        PaperTrade trade
    )
    {
        return new PaperPositionResponse(
            trade.id(),
            trade.clientOrderId(),
            trade.symbol(),
            trade.orderType(),
            trade.marginMode(),
            trade.side(),
            trade.status(),
            trade.leverage(),
            trade.marginUsd(),
            trade.notionalUsd(),
            trade.quantity(),
            trade.entryPrice(),
            trade.exitPrice(),
            null,
            null,
            null,
            null,
            trade.entryFee(),
            null,
            trade.fundingPnl(),
            null,
            null,
            null,
            null,
            null,
            trade.exitPrice(),
            trade.grossRealizedPnl(),
            trade.realizedPnl(),
            trade.stopLoss(),
            trade.takeProfit(),
            trade.openedAt(),
            trade.exitPrice(),
            trade.realizedPnl(),
            trade.grossRealizedPnl(),
            trade.exitFee(),
            trade.liquidationFee(),
            trade.closedAt(),
            trade.closeReason()
        );
    }

    private OrderProjection projectOrder(
        PaperTradingAccount account,
        List<PaperTrade> openTrades,
        OpenCommand command,
        BtcPerpetualMarketSnapshot market,
        PerpetualRuleVersion rule
    )
    {
        command.side().validateRiskControls(
            market.lastPrice(),
            command.stopLoss(),
            command.takeProfit()
        );
        BigDecimal entryPrice =
            command.side().openingPrice(market.quote());
        BigDecimal notional = command.marginUsd()
            .multiply(BigDecimal.valueOf(command.leverage()))
            .setScale(PaperTradingMath.MONEY_SCALE);
        BigDecimal quantity = PaperTradingMath.quantity(
            notional,
            entryPrice
        );
        if (quantity.signum() <= 0)
        {
            throw new IllegalArgumentException(
                "Order quantity is below the supported precision"
            );
        }
        BigDecimal entryFee = PaperTradingMath.fee(
            entryPrice,
            quantity,
            rule.takerFeeRate()
        );
        PaperTrade projectedTrade = new PaperTrade(
            PREVIEW_TRADE_ID,
            account.userId(),
            command.clientOrderId() == null
                ? PREVIEW_ORDER_ID
                : command.clientOrderId(),
            SYMBOL,
            ORDER_TYPE,
            MARGIN_MODE,
            command.side(),
            TradeStatus.OPEN,
            command.leverage(),
            command.marginUsd(),
            notional,
            quantity,
            entryPrice,
            null,
            command.stopLoss(),
            command.takeProfit(),
            0,
            rule.id(),
            clock.instant(),
            rule.takerFeeRate(),
            null,
            entryFee,
            moneyZero(),
            moneyZero(),
            moneyZero(),
            null,
            null,
            null,
            clock.instant(),
            null
        );
        List<PaperTrade> projectedTrades = new ArrayList<>(openTrades);
        projectedTrades.add(projectedTrade);
        BigDecimal walletAfterEntryFee = account.balanceUsd()
            .subtract(entryFee)
            .setScale(PaperTradingMath.MONEY_SCALE);
        AccountRiskMetrics accountRisk = riskCalculator.calculate(
            walletAfterEntryFee,
            projectedTrades,
            market,
            rule
        );
        BigDecimal aggregateSideNotional = projectedTrades.stream()
            .filter(trade -> trade.side() == command.side())
            .map(trade -> market.markPrice().multiply(trade.quantity()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        MaintenanceMarginTier tier = rule.tierFor(
            aggregateSideNotional
        );
        int maximumSameSideLeverage = projectedTrades.stream()
            .filter(trade -> trade.side() == command.side())
            .mapToInt(PaperTrade::leverage)
            .max()
            .orElseThrow();
        BigDecimal estimatedExitFee = PaperTradingMath.reservedFee(
            command.side().closingPrice(market.quote()),
            quantity,
            rule.takerFeeRate()
        );
        return new OrderProjection(
            projectedTrade,
            accountRisk,
            tier,
            estimatedExitFee,
            maximumSameSideLeverage
        );
    }

    private static void requireAdmissible(
        OpenCommand command,
        OrderProjection projection
    )
    {
        if (
            projection.maximumSameSideLeverage()
                > projection.tier().maxLeverage()
        )
        {
            throw new IllegalArgumentException(
                "The projected same-side notional is in maintenance tier "
                    + projection.tier().tier()
                    + ", which allows at most "
                    + projection.tier().maxLeverage()
                    + "x leverage"
            );
        }
        AccountRiskMetrics risk = projection.accountRisk();
        if (risk.availableMargin().signum() < 0)
        {
            throw new InsufficientPaperMarginException(
                command.marginUsd(),
                risk.availableMargin().add(command.marginUsd()).max(
                    BigDecimal.ZERO
                )
            );
        }
        if (risk.riskState() == AccountRiskState.LIQUIDATABLE)
        {
            throw new InsufficientPaperMarginException(
                command.marginUsd(),
                BigDecimal.ZERO.setScale(PaperTradingMath.MONEY_SCALE)
            );
        }
    }

    private BigDecimal maximumOrderMargin(
        PaperTradingAccount account,
        List<PaperTrade> openTrades,
        TradeSide side,
        int leverage,
        BtcPerpetualMarketSnapshot market,
        PerpetualRuleVersion rule
    )
    {
        AccountRiskMetrics currentRisk = riskCalculator.calculate(
            account.balanceUsd(),
            openTrades,
            market,
            rule
        );
        BigDecimal lower = BigDecimal.ZERO;
        BigDecimal upper = currentRisk.availableMargin()
            .max(BigDecimal.ZERO)
            .setScale(PaperTradingMath.MONEY_SCALE, RoundingMode.DOWN);
        for (int iteration = 0; iteration < 48; iteration++)
        {
            BigDecimal midpoint = lower.add(upper).divide(
                BigDecimal.valueOf(2),
                PaperTradingMath.MONEY_SCALE,
                RoundingMode.DOWN
            );
            if (midpoint.compareTo(lower) == 0
                || midpoint.compareTo(upper) == 0)
            {
                break;
            }
            OpenCommand candidate = new OpenCommand(
                null,
                side,
                leverage,
                midpoint,
                null,
                null
            );
            OrderProjection projection = projectOrder(
                account,
                openTrades,
                candidate,
                market,
                rule
            );
            boolean admissible =
                projection.maximumSameSideLeverage()
                    <= projection.tier().maxLeverage()
                && projection.accountRisk().availableMargin().signum() >= 0
                && projection.accountRisk().riskState()
                    != AccountRiskState.LIQUIDATABLE;
            if (admissible)
            {
                lower = midpoint;
            }
            else
            {
                upper = midpoint;
            }
        }
        return lower.setScale(
            PaperTradingMath.MONEY_SCALE,
            RoundingMode.DOWN
        );
    }

    private static BigDecimal directionalLiquidationPrice(
        TradeSide side,
        AccountRiskMetrics risk
    )
    {
        return side == TradeSide.LONG
            ? risk.estimatedLowerLiquidationPrice()
            : risk.estimatedUpperLiquidationPrice();
    }

    private static BigDecimal directionalBankruptcyPrice(
        TradeSide side,
        AccountRiskMetrics risk
    )
    {
        return side == TradeSide.LONG
            ? risk.lowerBankruptcyPrice()
            : risk.upperBankruptcyPrice();
    }

    private static BtcPerpetualMarketSnapshot withObservedMark(
        BtcPerpetualMarketSnapshot market,
        BigDecimal observedMarkPrice,
        Instant observedAt
    )
    {
        return new BtcPerpetualMarketSnapshot(
            market.lastPrice(),
            market.lastPriceTime(),
            market.bidPrice(),
            market.askPrice(),
            market.bookTickerTime(),
            observedMarkPrice,
            market.indexPrice(),
            market.fundingRate(),
            market.nextFundingTime(),
            observedAt
        );
    }

    private static Instant earliestPricingTime(
        BtcPerpetualMarketSnapshot market
    )
    {
        return List.of(
                market.bookTickerTime(),
                market.lastPriceTime(),
                market.markPriceTime()
            )
            .stream()
            .min(Comparator.naturalOrder())
            .orElseThrow();
    }

    private static OpenCommand validateOpenRequest(OpenPositionRequest request)
    {
        if (request == null)
        {
            throw new IllegalArgumentException(
                "Open-position request is required"
            );
        }
        if (request.clientOrderId() == null)
        {
            throw new IllegalArgumentException(
                "Client order id is required"
            );
        }
        return validateCommand(
            request.clientOrderId(),
            request.side(),
            request.leverage(),
            request.marginUsd(),
            request.stopLoss(),
            request.takeProfit()
        );
    }

    private static OpenCommand validatePreviewRequest(
        PreviewPositionRequest request
    )
    {
        if (request == null)
        {
            throw new IllegalArgumentException(
                "Position-preview request is required"
            );
        }
        return validateCommand(
            null,
            request.side(),
            request.leverage(),
            request.marginUsd(),
            request.stopLoss(),
            request.takeProfit()
        );
    }

    private static OpenCommand validateCommand(
        UUID clientOrderId,
        TradeSide side,
        Integer leverage,
        BigDecimal marginUsd,
        BigDecimal stopLoss,
        BigDecimal takeProfit
    )
    {
        if (side == null)
        {
            throw new IllegalArgumentException("Position side is required");
        }
        if (leverage == null || leverage < 1 || leverage > 100)
        {
            throw new IllegalArgumentException(
                "Leverage must be between 1 and 100"
            );
        }
        BigDecimal margin = PaperTradingMath.money(marginUsd, "Margin");
        if (margin.signum() <= 0)
        {
            throw new IllegalArgumentException("Margin must be positive");
        }
        return new OpenCommand(
            clientOrderId,
            side,
            leverage,
            margin,
            PaperTradingMath.optionalPrice(stopLoss, "Stop loss"),
            PaperTradingMath.optionalPrice(takeProfit, "Take profit")
        );
    }

    private static void requireSameOpenIntent(
        PaperTrade trade,
        OpenCommand command
    )
    {
        if (trade.side() != command.side()
            || trade.leverage() != command.leverage()
            || trade.marginUsd().compareTo(command.marginUsd()) != 0
            || !sameDecimal(trade.stopLoss(), command.stopLoss())
            || !sameDecimal(trade.takeProfit(), command.takeProfit()))
        {
            throw new PaperOrderIdConflictException(
                command.clientOrderId()
            );
        }
    }

    private static boolean sameDecimal(BigDecimal first, BigDecimal second)
    {
        if (first == null || second == null)
        {
            return first == second;
        }
        return first.compareTo(second) == 0;
    }

    private static void requireOpen(PaperTrade trade)
    {
        if (trade.status() != TradeStatus.OPEN)
        {
            throw new PaperPositionNotOpenException(trade.id());
        }
    }

    private static void requireUser(UUID userId)
    {
        if (userId == null)
        {
            throw new IllegalArgumentException("User id is required");
        }
    }

    private static void requirePosition(UUID positionId)
    {
        if (positionId == null)
        {
            throw new IllegalArgumentException("Position id is required");
        }
    }

    private static void requireObservationTime(Instant observedAt)
    {
        if (observedAt == null)
        {
            throw new IllegalArgumentException(
                "Market observation time is required"
            );
        }
    }

    private static void requireClosedTradeLimit(int limit)
    {
        if (limit < 1 || limit > MAX_CLOSED_TRADE_LIMIT)
        {
            throw new IllegalArgumentException(
                "Closed-trade limit must be between 1 and "
                    + MAX_CLOSED_TRADE_LIMIT
            );
        }
    }

    private <T> T inTransaction(TransactionWork<T> work)
    {
        return transactions.execute(status -> work.run());
    }

    private static BigDecimal moneyZero()
    {
        return BigDecimal.ZERO.setScale(PaperTradingMath.MONEY_SCALE);
    }

    @FunctionalInterface
    private interface TransactionWork<T>
    {
        T run();
    }

    private record OpenCommand(
        UUID clientOrderId,
        TradeSide side,
        int leverage,
        BigDecimal marginUsd,
        BigDecimal stopLoss,
        BigDecimal takeProfit
    )
    {
    }

    private record OrderProjection(
        PaperTrade trade,
        AccountRiskMetrics accountRisk,
        MaintenanceMarginTier tier,
        BigDecimal estimatedExitFee,
        int maximumSameSideLeverage
    )
    {
    }
}
