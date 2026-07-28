package com.raretable.casino.paper_trading.price;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.raretable.casino.paper_trading.market_data.MarketDataSynchronizingException;
import com.raretable.casino.paper_trading.market_data.binance.MarketDataProperties;

@Component
public class BinanceWrapper
{
    private final AtomicReference<MarketComponents> liveMarket =
        new AtomicReference<>(MarketComponents.empty());
    private final Clock clock;
    private final Duration maxStaleness;

    @Autowired
    public BinanceWrapper(MarketDataProperties properties, Clock clock)
    {
        this(properties.maxStaleness(), clock);
    }

    BinanceWrapper(Duration maxStaleness, Clock clock)
    {
        this.maxStaleness = Objects.requireNonNull(
            maxStaleness,
            "Market-data max staleness is required"
        );
        if (maxStaleness.isZero() || maxStaleness.isNegative())
        {
            throw new IllegalArgumentException(
                "Market-data max staleness must be positive"
            );
        }
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    /**
     * Constructor for test doubles that override every price getter.
     */
    protected BinanceWrapper()
    {
        clock = null;
        maxStaleness = null;
    }

    public BigDecimal getBtcPrice()
    {
        return getBtcPerpetualMarketSnapshot().lastPrice();
    }

    public BtcQuote getBtcQuote()
    {
        return getBtcPerpetualMarketSnapshot().quote();
    }

    public BtcPerpetualMarketSnapshot getBtcPerpetualMarketSnapshot()
    {
        requireConfigured();
        BtcPerpetualMarketSnapshot snapshot = liveMarket.get().snapshot();
        if (snapshot == null)
        {
            throw new MarketDataSynchronizingException(
                "Live BTCUSDT perpetual market data is still synchronizing"
            );
        }

        Instant staleBefore = clock.instant().minus(maxStaleness);
        if (snapshot.lastPriceTime().isBefore(staleBefore)
            || snapshot.bookTickerTime().isBefore(staleBefore)
            || snapshot.markPriceTime().isBefore(staleBefore))
        {
            throw new MarketDataSynchronizingException(
                "Live BTCUSDT perpetual market data is stale"
            );
        }
        return snapshot;
    }

    public void updateLastPrice(BigDecimal price, Instant observedAt)
    {
        LastPrice update = new LastPrice(price, observedAt);
        liveMarket.updateAndGet(current -> current.withLastPrice(update));
    }

    public void updateBtcQuote(BtcQuote quote, Instant observedAt)
    {
        BookTicker update = new BookTicker(quote, observedAt);
        liveMarket.updateAndGet(current -> current.withBookTicker(update));
    }

    public void updateMarkPrice(
        BigDecimal markPrice,
        BigDecimal indexPrice,
        BigDecimal fundingRate,
        Instant nextFundingTime,
        Instant observedAt
    )
    {
        MarkPrice update = new MarkPrice(
            markPrice,
            indexPrice,
            fundingRate,
            nextFundingTime,
            observedAt
        );
        liveMarket.updateAndGet(current -> current.withMarkPrice(update));
    }

    public void clearMarketPrices()
    {
        liveMarket.updateAndGet(MarketComponents::withoutMarketPrices);
    }

    public void clearBtcQuote()
    {
        liveMarket.updateAndGet(MarketComponents::withoutBookTicker);
    }

    public void clear()
    {
        liveMarket.set(MarketComponents.empty());
    }

    private void requireConfigured()
    {
        if (clock == null || maxStaleness == null)
        {
            throw new IllegalStateException(
                "This Binance wrapper test double must override its price getter"
            );
        }
    }

    private record LastPrice(BigDecimal price, Instant observedAt)
    {
        private LastPrice
        {
            requirePositive(price, "BTC last price");
            requireTimestamp(observedAt, "BTC last-price time");
        }
    }

    private record BookTicker(BtcQuote quote, Instant observedAt)
    {
        private BookTicker
        {
            Objects.requireNonNull(quote, "BTC quote is required");
            requireTimestamp(observedAt, "BTC book-ticker time");
        }
    }

    private record MarkPrice(
        BigDecimal markPrice,
        BigDecimal indexPrice,
        BigDecimal fundingRate,
        Instant nextFundingTime,
        Instant observedAt
    )
    {
        private MarkPrice
        {
            requirePositive(markPrice, "BTC mark price");
            requirePositive(indexPrice, "BTC index price");
            Objects.requireNonNull(fundingRate, "BTC funding rate is required");
            requireTimestamp(nextFundingTime, "BTC next-funding time");
            requireTimestamp(observedAt, "BTC mark-price time");
        }
    }

    private record MarketComponents(
        LastPrice lastPrice,
        BookTicker bookTicker,
        MarkPrice markPrice
    )
    {
        private static MarketComponents empty()
        {
            return new MarketComponents(null, null, null);
        }

        private MarketComponents withLastPrice(LastPrice update)
        {
            return new MarketComponents(update, bookTicker, markPrice);
        }

        private MarketComponents withBookTicker(BookTicker update)
        {
            return new MarketComponents(lastPrice, update, markPrice);
        }

        private MarketComponents withMarkPrice(MarkPrice update)
        {
            return new MarketComponents(lastPrice, bookTicker, update);
        }

        private MarketComponents withoutMarketPrices()
        {
            return new MarketComponents(null, bookTicker, null);
        }

        private MarketComponents withoutBookTicker()
        {
            return new MarketComponents(lastPrice, null, markPrice);
        }

        private BtcPerpetualMarketSnapshot snapshot()
        {
            if (lastPrice == null || bookTicker == null || markPrice == null)
            {
                return null;
            }
            return new BtcPerpetualMarketSnapshot(
                lastPrice.price(),
                lastPrice.observedAt(),
                bookTicker.quote().bidPrice(),
                bookTicker.quote().askPrice(),
                bookTicker.observedAt(),
                markPrice.markPrice(),
                markPrice.indexPrice(),
                markPrice.fundingRate(),
                markPrice.nextFundingTime(),
                markPrice.observedAt()
            );
        }
    }

    private static void requirePositive(BigDecimal value, String label)
    {
        if (value == null || value.signum() <= 0)
        {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }

    private static void requireTimestamp(Instant value, String label)
    {
        if (value == null || !value.isAfter(Instant.EPOCH))
        {
            throw new IllegalArgumentException(
                label + " must be after the Unix epoch"
            );
        }
    }
}
