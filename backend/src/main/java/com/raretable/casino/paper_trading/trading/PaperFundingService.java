package com.raretable.casino.paper_trading.trading;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.raretable.casino.paper_trading.market_data.BinanceFundingRate;
import com.raretable.casino.paper_trading.market_data.BinanceFundingRateSource;
import com.raretable.casino.paper_trading.price.BinanceWrapper;
import com.raretable.casino.paper_trading.price.BtcPerpetualMarketSnapshot;

@Service
public final class PaperFundingService
{
    private static final String SYMBOL = "BTCUSDT";

    private final BinanceFundingRateSource source;
    private final JdbcPaperFundingRepository fundingRepository;
    private final JdbcPaperTradingRepository tradingRepository;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final PaperTradingOperations tradingService;
    private final BinanceWrapper binance;

    public PaperFundingService(
        BinanceFundingRateSource source,
        JdbcPaperFundingRepository fundingRepository,
        JdbcPaperTradingRepository tradingRepository,
        TransactionTemplate transactions,
        Clock clock,
        PaperTradingOperations tradingService,
        BinanceWrapper binance
    )
    {
        this.source = source;
        this.fundingRepository = fundingRepository;
        this.tradingRepository = tradingRepository;
        this.transactions = transactions;
        this.clock = clock;
        this.tradingService = tradingService;
        this.binance = binance;
    }

    public void reconcile()
    {
        settlePendingEvents();
        Instant start = fundingRepository.findLatestFundingTime()
            .map(time -> time.plusMillis(1))
            .or(() ->
                fundingRepository.findEarliestFundingEligibility()
            )
            .orElse(null);
        if (start == null)
        {
            return;
        }
        Instant end = clock.instant();
        if (start.isAfter(end))
        {
            return;
        }

        while (!start.isAfter(end))
        {
            List<BinanceFundingRate> page = source.getBtcFundingRates(
                start,
                end,
                BinanceFundingRateSource.MAX_PAGE_SIZE
            );
            if (page.isEmpty())
            {
                return;
            }
            Instant previous = null;
            for (BinanceFundingRate funding : page)
            {
                if (!SYMBOL.equals(funding.symbol()))
                {
                    throw new IllegalStateException(
                        "Funding source returned an unexpected symbol"
                    );
                }
                if (previous != null
                    && !funding.fundingTime().isAfter(previous))
                {
                    throw new IllegalStateException(
                        "Funding history must be strictly chronological"
                    );
                }
                PaperFundingEvent event = inTransaction(() ->
                    fundingRepository.store(funding, clock.instant())
                );
                settle(event);
                previous = funding.fundingTime();
            }
            if (page.size() < BinanceFundingRateSource.MAX_PAGE_SIZE)
            {
                return;
            }
            start = previous.plusMillis(1);
        }
    }

    private void settlePendingEvents()
    {
        for (
            PaperFundingEvent event
                : fundingRepository.findPendingEvents()
        )
        {
            settle(event);
        }
    }

    private void settle(PaperFundingEvent event)
    {
        for (UUID userId : fundingRepository.findEligibleUserIds(event))
        {
            inTransaction(() -> {
                PaperTradingAccount account =
                    tradingRepository.lockAccount(userId);
                List<PaperTrade> trades =
                    tradingRepository.lockFundingEligibleTrades(
                        userId,
                        event.fundingTime()
                    );
                BigDecimal wallet = account.balanceUsd();
                Instant settledAt = clock.instant();
                for (PaperTrade trade : trades)
                {
                    BigDecimal notional = PaperTradingMath.notional(
                        event.markPrice(),
                        trade.quantity()
                    );
                    BigDecimal amount = PaperTradingMath.fundingPnl(
                        trade.side(),
                        trade.quantity(),
                        event.markPrice(),
                        event.fundingRate()
                    );
                    if (!fundingRepository.insertSettlement(
                        event,
                        trade,
                        notional,
                        amount,
                        settledAt
                    ))
                    {
                        continue;
                    }
                    wallet = wallet.add(amount)
                        .setScale(PaperTradingMath.MONEY_SCALE);
                    tradingRepository.addFundingPnl(trade.id(), amount);
                    tradingRepository.updateBalance(
                        userId,
                        wallet,
                        settledAt
                    );
                    tradingRepository.insertLedgerEntry(
                        UUID.randomUUID(),
                        userId,
                        trade.id(),
                        LedgerEventType.FUNDING,
                        amount,
                        wallet,
                        settledAt,
                        "funding:"
                            + event.id()
                            + ":trade:"
                            + trade.id()
                    );
                }
                if (
                    wallet.signum() < 0
                    && tradingRepository.findOpenTrades(userId).isEmpty()
                )
                {
                    BigDecimal insuranceCredit = wallet.negate()
                        .setScale(PaperTradingMath.MONEY_SCALE);
                    wallet = BigDecimal.ZERO.setScale(
                        PaperTradingMath.MONEY_SCALE
                    );
                    tradingRepository.updateBalance(
                        userId,
                        wallet,
                        settledAt
                    );
                    tradingRepository.insertLedgerEntry(
                        UUID.randomUUID(),
                        userId,
                        null,
                        LedgerEventType.INSURANCE_CREDIT,
                        insuranceCredit,
                        wallet,
                        settledAt,
                        "funding:"
                            + event.id()
                            + ":user:"
                            + userId
                            + ":insurance"
                    );
                }
                return null;
            });
        }
        BtcPerpetualMarketSnapshot market =
            binance.getBtcPerpetualMarketSnapshot();
        tradingService.processLiquidations(
            market.markPrice(),
            market.markPriceTime()
        );
    }

    private <T> T inTransaction(TransactionWork<T> work)
    {
        return transactions.execute(status -> work.run());
    }

    @FunctionalInterface
    private interface TransactionWork<T>
    {
        T run();
    }
}
