package com.raretable.casino.paper_trading.market_data.binance;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import com.raretable.casino.paper_trading.market_data.BtcCandleService;
import com.raretable.casino.paper_trading.trading.PaperFundingService;

@Component
@ConditionalOnProperty(
    prefix = "raretable.market-data",
    name = "enabled",
    havingValue = "true"
)
final class BtcMarketDataPipeline implements SmartLifecycle
{
    private static final Logger LOGGER =
        LoggerFactory.getLogger(BtcMarketDataPipeline.class);

    private final BtcCandleService candleService;
    private final BinanceMarketDataWebSocketClient liveClient;
    private final MarketDataProperties properties;
    private final PaperFundingService fundingService;
    private final ScheduledExecutorService reconciliationExecutor =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform()
                .daemon(true)
                .name("btc-candle-reconciliation")
                .factory()
        );
    private final AtomicBoolean running = new AtomicBoolean();

    BtcMarketDataPipeline(
        BtcCandleService candleService,
        BinanceMarketDataWebSocketClient liveClient,
        MarketDataProperties properties,
        PaperFundingService fundingService
    )
    {
        this.candleService = candleService;
        this.liveClient = liveClient;
        this.properties = properties;
        this.fundingService = fundingService;
    }

    @Override
    public void start()
    {
        if (running.compareAndSet(false, true))
        {
            reconciliationExecutor.execute(this::initialize);
        }
    }

    @Override
    public void stop()
    {
        if (running.compareAndSet(true, false))
        {
            liveClient.stop();
            reconciliationExecutor.shutdownNow();
        }
    }

    @Override
    public boolean isRunning()
    {
        return running.get();
    }

    private void initialize()
    {
        if (!running.get())
        {
            return;
        }

        try
        {
            liveClient.start();
            reconcileCandles();
            reconcileFunding();
            scheduleReconciliation(
                properties.reconciliationInterval().toMillis()
            );
        }
        catch (RuntimeException exception)
        {
            LOGGER.error("Initial BTC candle synchronization failed", exception);
            scheduleInitializationRetry();
        }
    }

    private void reconcile()
    {
        if (!running.get())
        {
            return;
        }

        reconcileCandles();
        reconcileFunding();
        scheduleReconciliation(
            properties.reconciliationInterval().toMillis()
        );
    }

    private void reconcileCandles()
    {
        try
        {
            candleService.reconcileAll();
        }
        catch (RuntimeException exception)
        {
            LOGGER.error(
                "BTC perpetual candle reconciliation failed",
                exception
            );
        }
    }

    private void reconcileFunding()
    {
        try
        {
            fundingService.reconcile();
        }
        catch (RuntimeException exception)
        {
            LOGGER.error(
                "BTC perpetual funding reconciliation failed",
                exception
            );
        }
    }

    private void scheduleInitializationRetry()
    {
        if (running.get())
        {
            reconciliationExecutor.schedule(
                this::initialize,
                properties.reconnectDelay().toMillis(),
                TimeUnit.MILLISECONDS
            );
        }
    }

    private void scheduleReconciliation(long delayMillis)
    {
        if (running.get())
        {
            reconciliationExecutor.schedule(
                this::reconcile,
                delayMillis,
                TimeUnit.MILLISECONDS
            );
        }
    }
}
