package com.raretable.casino.paper_trading.trading;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

import com.raretable.casino.paper_trading.market_data.MarketDataSynchronizingException;

@Component
public final class PaperTradingRiskMonitor implements DisposableBean
{
    private static final Logger LOGGER =
        LoggerFactory.getLogger(PaperTradingRiskMonitor.class);
    private static final long RETRY_DELAY_MILLIS = 1_000;

    private final PaperTradingOperations tradingService;
    private final Deque<MarketPriceSignal> observedPrices =
        new ConcurrentLinkedDeque<>();
    private final AtomicBoolean drainScheduled = new AtomicBoolean();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final ScheduledExecutorService executor =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform()
                .daemon(true)
                .name("paper-trading-risk-controls")
                .factory()
        );

    public PaperTradingRiskMonitor(PaperTradingOperations tradingService)
    {
        this.tradingService = tradingService;
    }

    public void accept(
        BigDecimal observedTradePrice,
        Instant observedAt
    )
    {
        if (!running.get())
        {
            return;
        }
        observedPrices.addLast(
            new MarketPriceSignal(
                MarketPriceSignal.MarketPriceType.LAST,
                observedTradePrice,
                observedAt
            )
        );
        scheduleDrain(0);
    }

    public void acceptMarkPrice(
        BigDecimal observedMarkPrice,
        Instant observedAt
    )
    {
        if (!running.get())
        {
            return;
        }
        observedPrices.addLast(
            new MarketPriceSignal(
                MarketPriceSignal.MarketPriceType.MARK,
                observedMarkPrice,
                observedAt
            )
        );
        scheduleDrain(0);
    }

    @Override
    public void destroy()
    {
        running.set(false);
        observedPrices.clear();
        executor.shutdownNow();
    }

    private void scheduleDrain(long delayMillis)
    {
        if (!drainScheduled.compareAndSet(false, true))
        {
            return;
        }
        try
        {
            executor.schedule(
                this::drain,
                delayMillis,
                TimeUnit.MILLISECONDS
            );
        }
        catch (RejectedExecutionException exception)
        {
            drainScheduled.set(false);
            if (running.get())
            {
                throw exception;
            }
        }
    }

    private void drain()
    {
        boolean retryRequired = false;
        try
        {
            MarketPriceSignal signal;
            while (
                running.get()
                && (signal = observedPrices.pollFirst()) != null
            )
            {
                try
                {
                    if (
                        signal.type()
                            == MarketPriceSignal.MarketPriceType.LAST
                    )
                    {
                        tradingService.processRiskControls(
                            signal.price(),
                            signal.observedAt()
                        );
                    }
                    else
                    {
                        tradingService.processLiquidations(
                            signal.price(),
                            signal.observedAt()
                        );
                    }
                }
                catch (RuntimeException exception)
                {
                    if (!isRetryable(exception))
                    {
                        running.set(false);
                        LOGGER.error(
                            "Paper-trading risk-control monitor stopped "
                                + "after a non-transient failure",
                            exception
                        );
                        throw exception;
                    }

                    observedPrices.addFirst(signal);
                    retryRequired = true;
                    LOGGER.error(
                        "Paper-trading risk-control evaluation failed; "
                            + "the market signal will be retried",
                        exception
                    );
                    break;
                }
            }
        }
        finally
        {
            drainScheduled.set(false);
            if (running.get() && !observedPrices.isEmpty())
            {
                scheduleDrain(
                    retryRequired ? RETRY_DELAY_MILLIS : 0
                );
            }
        }
    }

    private static boolean isRetryable(RuntimeException exception)
    {
        Throwable failure = exception;
        while (failure != null)
        {
            if (
                failure instanceof TransientDataAccessException
                || failure instanceof RecoverableDataAccessException
                || failure instanceof CannotCreateTransactionException
                || failure instanceof MarketDataSynchronizingException
            )
            {
                return true;
            }
            failure = failure.getCause();
        }
        return false;
    }
}
