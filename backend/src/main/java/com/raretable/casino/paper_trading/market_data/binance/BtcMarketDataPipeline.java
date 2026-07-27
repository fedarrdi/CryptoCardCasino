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
    private final BinanceKlineWebSocketClient liveClient;
    private final MarketDataProperties properties;
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
        BinanceKlineWebSocketClient liveClient,
        MarketDataProperties properties
    )
    {
        this.candleService = candleService;
        this.liveClient = liveClient;
        this.properties = properties;
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
            candleService.reconcileAll();
            liveClient.start();
            scheduleReconciliation(properties.reconciliationInterval().toMillis());
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

        try
        {
            candleService.reconcileAll();
        }
        catch (RuntimeException exception)
        {
            LOGGER.error("Periodic BTC candle reconciliation failed", exception);
        }
        finally
        {
            scheduleReconciliation(properties.reconciliationInterval().toMillis());
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
