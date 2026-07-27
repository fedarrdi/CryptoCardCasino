package com.raretable.casino.paper_trading.trading;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.raretable.casino.paper_trading.api.OpenPositionRequest;
import com.raretable.casino.paper_trading.api.PaperPositionResponse;
import com.raretable.casino.paper_trading.api.PaperTradingPortfolioResponse;
import com.raretable.casino.paper_trading.api.TradingAccountResponse;
import com.raretable.casino.paper_trading.api.TradingQuoteResponse;
import com.raretable.casino.paper_trading.api.UpdateRiskControlsRequest;
import com.raretable.casino.paper_trading.price.BinanceWrapper;
import com.raretable.casino.paper_trading.price.BtcQuote;

@Service
public final class PaperTradingService implements PaperTradingOperations
{
    public static final int DEFAULT_CLOSED_TRADE_LIMIT = 50;
    public static final int MAX_CLOSED_TRADE_LIMIT = 100;
    public static final BigDecimal INITIAL_BALANCE =
        new BigDecimal("10000.00000000");
    private static final String SYMBOL = "BTCUSDT";
    private static final String ORDER_TYPE = "MARKET";
    private static final String MARGIN_MODE = "CROSS";

    private final JdbcPaperTradingRepository repository;
    private final BinanceWrapper binance;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public PaperTradingService(
        JdbcPaperTradingRepository repository,
        BinanceWrapper binance,
        TransactionTemplate transactions,
        Clock clock
    )
    {
        this.repository = repository;
        this.binance = binance;
        this.transactions = transactions;
        this.clock = clock;
    }

    public PaperTradingPortfolioResponse getPortfolio(
        UUID userId,
        int closedTradeLimit
    )
    {
        requireUser(userId);
        requireClosedTradeLimit(closedTradeLimit);
        return inTransaction(() -> {
            PaperTradingAccount account = repository.lockAccount(userId);
            BtcQuote quote = binance.getBtcQuote();
            return portfolio(account, quote, closedTradeLimit);
        });
    }

    public PaperTradingPortfolioResponse openPosition(
        UUID userId,
        OpenPositionRequest request
    )
    {
        requireUser(userId);
        OpenCommand command = validateOpenRequest(request);

        return inTransaction(() -> {
            PaperTradingAccount account = repository.lockAccount(userId);
            BtcQuote quote = binance.getBtcQuote();
            PaperTrade existing = repository.findByClientOrderId(
                userId,
                command.clientOrderId()
            ).orElse(null);
            if (existing != null)
            {
                requireSameOpenIntent(existing, command);
                return portfolio(
                    account,
                    quote,
                    DEFAULT_CLOSED_TRADE_LIMIT
                );
            }
            BigDecimal entryPrice = command.side().openingPrice(quote);
            command.side().validateRiskControls(
                quote,
                command.stopLoss(),
                command.takeProfit()
            );
            List<PaperTrade> openTrades = repository.findOpenTrades(userId);
            TradingAccountResponse summary =
                accountSummary(account, openTrades, quote);
            if (command.marginUsd().compareTo(summary.availableMargin()) > 0)
            {
                throw new InsufficientPaperMarginException(
                    command.marginUsd(),
                    summary.availableMargin()
                );
            }

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

            repository.insert(new PaperTrade(
                UUID.randomUUID(),
                userId,
                command.clientOrderId(),
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
                null,
                null,
                clock.instant(),
                null
            ));
            return portfolio(account, quote, DEFAULT_CLOSED_TRADE_LIMIT);
        });
    }

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
            BtcQuote quote = binance.getBtcQuote();
            trade.side().validateRiskControls(
                quote,
                stopLoss,
                takeProfit
            );
            repository.updateRiskControls(
                trade,
                stopLoss,
                takeProfit,
                clock.instant()
            );
            return portfolio(account, quote, DEFAULT_CLOSED_TRADE_LIMIT);
        });
    }

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
            BtcQuote quote = binance.getBtcQuote();
            close(account, trade, quote, TradeCloseReason.USER);
            PaperTradingAccount updatedAccount = new PaperTradingAccount(
                userId,
                account.balanceUsd().add(realizedPnl(trade, quote))
            );
            return portfolio(
                updatedAccount,
                quote,
                DEFAULT_CLOSED_TRADE_LIMIT
            );
        });
    }

    public void processRiskControls(
        BigDecimal observedTradePrice,
        Instant observedAt
    )
    {
        BigDecimal signalPrice = PaperTradingMath.positivePrice(
            observedTradePrice,
            "Observed BTC price"
        );
        if (observedAt == null)
        {
            throw new IllegalArgumentException(
                "Market observation time is required"
            );
        }
        List<TriggeredPaperTrade> candidates =
            repository.findTriggeredTrades(signalPrice, observedAt);
        if (candidates.isEmpty())
        {
            return;
        }

        for (TriggeredPaperTrade candidate : candidates)
        {
            inTransaction(() -> {
                PaperTradingAccount account =
                    repository.lockAccount(candidate.userId());
                PaperTrade trade = repository
                    .lockTrade(candidate.id(), candidate.userId())
                    .orElse(null);
                if (trade == null
                    || trade.status() != TradeStatus.OPEN)
                {
                    return null;
                }

                BtcQuote quote = binance.getBtcQuote();
                close(account, trade, quote, candidate.reason());
                return null;
            });
        }
    }

    private void close(
        PaperTradingAccount account,
        PaperTrade trade,
        BtcQuote quote,
        TradeCloseReason reason
    )
    {
        BigDecimal exitPrice = trade.side().closingPrice(quote);
        BigDecimal pnl = PaperTradingMath.pnl(
            trade.side(),
            trade.entryPrice(),
            exitPrice,
            trade.quantity()
        );
        Instant closedAt = clock.instant();
        repository.updateBalance(
            account.userId(),
            account.balanceUsd().add(pnl),
            closedAt
        );
        repository.close(trade.id(), exitPrice, pnl, reason, closedAt);
    }

    private PaperTradingPortfolioResponse portfolio(
        PaperTradingAccount account,
        BtcQuote quote,
        int closedTradeLimit
    )
    {
        List<PaperTrade> openTrades =
            repository.findOpenTrades(account.userId());
        List<PaperTrade> closedTrades =
            repository.findClosedTrades(account.userId(), closedTradeLimit);
        return new PaperTradingPortfolioResponse(
            account.userId(),
            new TradingQuoteResponse(
                SYMBOL,
                quote.bidPrice(),
                quote.askPrice()
            ),
            accountSummary(account, openTrades, quote),
            openTrades.stream()
                .map(trade -> openPositionResponse(trade, quote))
                .toList(),
            closedTrades.stream()
                .map(PaperTradingService::closedPositionResponse)
                .toList()
        );
    }

    private static TradingAccountResponse accountSummary(
        PaperTradingAccount account,
        List<PaperTrade> openTrades,
        BtcQuote quote
    )
    {
        BigDecimal unrealizedPnl = zeroMoney();
        BigDecimal usedMargin = zeroMoney();
        for (PaperTrade trade : openTrades)
        {
            unrealizedPnl = unrealizedPnl.add(
                PaperTradingMath.pnl(
                    trade.side(),
                    trade.entryPrice(),
                    trade.side().closingPrice(quote),
                    trade.quantity()
                )
            );
            usedMargin = usedMargin.add(trade.marginUsd());
        }
        BigDecimal equity = account.balanceUsd().add(unrealizedPnl);
        return new TradingAccountResponse(
            INITIAL_BALANCE,
            account.balanceUsd(),
            equity,
            unrealizedPnl,
            usedMargin,
            equity.subtract(usedMargin)
        );
    }

    private static PaperPositionResponse openPositionResponse(
        PaperTrade trade,
        BtcQuote quote
    )
    {
        BigDecimal markPrice = trade.side().closingPrice(quote);
        BigDecimal pnl = PaperTradingMath.pnl(
            trade.side(),
            trade.entryPrice(),
            markPrice,
            trade.quantity()
        );
        return positionResponse(
            trade,
            markPrice,
            pnl,
            PaperTradingMath.roePercent(pnl, trade.marginUsd())
        );
    }

    private static PaperPositionResponse closedPositionResponse(
        PaperTrade trade
    )
    {
        return positionResponse(trade, trade.exitPrice(), null, null);
    }

    private static PaperPositionResponse positionResponse(
        PaperTrade trade,
        BigDecimal markPrice,
        BigDecimal unrealizedPnl,
        BigDecimal unrealizedRoePercent
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
            markPrice,
            unrealizedPnl,
            unrealizedRoePercent,
            trade.stopLoss(),
            trade.takeProfit(),
            trade.openedAt(),
            trade.exitPrice(),
            trade.realizedPnl(),
            trade.closedAt(),
            trade.closeReason()
        );
    }

    private static BigDecimal realizedPnl(
        PaperTrade trade,
        BtcQuote quote
    )
    {
        return PaperTradingMath.pnl(
            trade.side(),
            trade.entryPrice(),
            trade.side().closingPrice(quote),
            trade.quantity()
        );
    }

    private static OpenCommand validateOpenRequest(OpenPositionRequest request)
    {
        if (request == null)
        {
            throw new IllegalArgumentException("Open-position request is required");
        }
        if (request.side() == null)
        {
            throw new IllegalArgumentException("Position side is required");
        }
        if (request.leverage() == null
            || request.leverage() < 1
            || request.leverage() > 100)
        {
            throw new IllegalArgumentException(
                "Leverage must be between 1 and 100"
            );
        }
        if (request.clientOrderId() == null)
        {
            throw new IllegalArgumentException(
                "Client order id is required"
            );
        }
        BigDecimal margin = PaperTradingMath.money(
            request.marginUsd(),
            "Margin"
        );
        if (margin.signum() <= 0)
        {
            throw new IllegalArgumentException("Margin must be positive");
        }
        return new OpenCommand(
            request.clientOrderId(),
            request.side(),
            request.leverage(),
            margin,
            PaperTradingMath.optionalPrice(request.stopLoss(), "Stop loss"),
            PaperTradingMath.optionalPrice(
                request.takeProfit(),
                "Take profit"
            )
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

    private static BigDecimal zeroMoney()
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
}
