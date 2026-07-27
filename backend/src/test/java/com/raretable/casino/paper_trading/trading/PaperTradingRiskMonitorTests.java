package com.raretable.casino.paper_trading.trading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;

import com.raretable.casino.paper_trading.api.OpenPositionRequest;
import com.raretable.casino.paper_trading.api.PaperTradingPortfolioResponse;
import com.raretable.casino.paper_trading.api.UpdateRiskControlsRequest;

class PaperTradingRiskMonitorTests
{
    private static final Instant OBSERVED_AT =
        Instant.parse("2026-07-27T12:00:01Z");

    @Test
    void preservesAThresholdCrossingThatIsFollowedByARebound()
        throws Exception
    {
        CountDownLatch firstProcessingStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstProcessing = new CountDownLatch(1);
        CountDownLatch allSignalsProcessed = new CountDownLatch(3);
        List<BigDecimal> processedPrices =
            Collections.synchronizedList(new ArrayList<>());

        PaperTradingOperations tradingService =
            new StubPaperTradingOperations((price, observedAt) -> {
                assertEquals(OBSERVED_AT, observedAt);
                processedPrices.add(price);
                allSignalsProcessed.countDown();
                if (processedPrices.size() == 1)
                {
                    firstProcessingStarted.countDown();
                    try
                    {
                        releaseFirstProcessing.await();
                    }
                    catch (InterruptedException exception)
                    {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(exception);
                    }
                }
            });

        PaperTradingRiskMonitor monitor =
            new PaperTradingRiskMonitor(tradingService);
        try
        {
            monitor.accept(new BigDecimal("100"), OBSERVED_AT);
            assertTrue(
                firstProcessingStarted.await(5, TimeUnit.SECONDS),
                "The first market signal was not processed"
            );

            monitor.accept(new BigDecimal("90"), OBSERVED_AT);
            monitor.accept(new BigDecimal("100"), OBSERVED_AT);
            releaseFirstProcessing.countDown();

            assertTrue(
                allSignalsProcessed.await(5, TimeUnit.SECONDS),
                "Queued market signals were not drained"
            );
            assertEquals(
                List.of(
                    new BigDecimal("100"),
                    new BigDecimal("90"),
                    new BigDecimal("100")
                ),
                processedPrices
            );
        }
        finally
        {
            releaseFirstProcessing.countDown();
            monitor.destroy();
        }
    }

    @Test
    void retriesAFailedSignalBeforeProcessingLaterSignals()
        throws Exception
    {
        CountDownLatch successfulSignals = new CountDownLatch(2);
        List<BigDecimal> attemptedPrices =
            Collections.synchronizedList(new ArrayList<>());

        PaperTradingOperations tradingService =
            new StubPaperTradingOperations((price, observedAt) -> {
                assertEquals(OBSERVED_AT, observedAt);
                attemptedPrices.add(price);
                if (attemptedPrices.size() == 1)
                {
                    throw new TransientDataAccessResourceException(
                        "temporary failure"
                    );
                }
                successfulSignals.countDown();
            });

        PaperTradingRiskMonitor monitor =
            new PaperTradingRiskMonitor(tradingService);
        try
        {
            monitor.accept(new BigDecimal("90"), OBSERVED_AT);
            monitor.accept(new BigDecimal("100"), OBSERVED_AT);

            assertTrue(
                successfulSignals.await(5, TimeUnit.SECONDS),
                "The failed signal was not retried"
            );
            assertEquals(
                List.of(
                    new BigDecimal("90"),
                    new BigDecimal("90"),
                    new BigDecimal("100")
                ),
                attemptedPrices
            );
        }
        finally
        {
            monitor.destroy();
        }
    }

    static final class StubPaperTradingOperations
        implements PaperTradingOperations
    {
        private final BiConsumer<BigDecimal, Instant> processor;

        StubPaperTradingOperations(
            BiConsumer<BigDecimal, Instant> processor
        )
        {
            this.processor = processor;
        }

        @Override
        public void processRiskControls(
            BigDecimal observedTradePrice,
            Instant observedAt
        )
        {
            processor.accept(observedTradePrice, observedAt);
        }

        @Override
        public PaperTradingPortfolioResponse getPortfolio(
            UUID userId,
            int closedTradeLimit
        )
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaperTradingPortfolioResponse openPosition(
            UUID userId,
            OpenPositionRequest request
        )
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaperTradingPortfolioResponse updateRiskControls(
            UUID userId,
            UUID positionId,
            UpdateRiskControlsRequest request
        )
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaperTradingPortfolioResponse closePosition(
            UUID userId,
            UUID positionId
        )
        {
            throw new UnsupportedOperationException();
        }
    }
}
