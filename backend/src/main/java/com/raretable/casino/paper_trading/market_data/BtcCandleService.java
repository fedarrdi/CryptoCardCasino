package com.raretable.casino.paper_trading.market_data;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;

import com.raretable.casino.paper_trading.api.BtcCandle;
import com.raretable.casino.paper_trading.api.BtcCandlesResponse;
import com.raretable.casino.paper_trading.market_data.binance.MarketDataProperties;
import com.raretable.casino.paper_trading.market_data.persistence.MarketCandleRepository;
import com.raretable.casino.paper_trading.market_data.persistence.StoredCandle;

@Service
public final class BtcCandleService implements CandleHistoryQuery
{
    public static final String SYMBOL = "BTCUSDT";
    public static final String PRODUCT_TYPE = "USD_M_PERPETUAL";
    static final String INTERVAL = BtcCandleInterval.ONE_HOUR.value();

    private final MarketCandleRepository repository;
    private final BinanceKlineSource binance;
    private final MarketDataProperties properties;
    private final Clock clock;
    private final Map<BtcCandleInterval, IntervalState> intervalStates;

    BtcCandleService(
        MarketCandleRepository repository,
        BinanceKlineSource binance,
        MarketDataProperties properties,
        Clock clock
    )
    {
        this.repository = repository;
        this.binance = binance;
        this.properties = properties;
        this.clock = clock;

        intervalStates = new EnumMap<>(BtcCandleInterval.class);
        for (BtcCandleInterval interval : BtcCandleInterval.values())
        {
            intervalStates.put(interval, new IntervalState());
        }
    }

    @Override
    public BtcCandlesResponse getBtcCandles(
        BtcCandleInterval interval,
        Long before,
        Integer requestedLimit
    )
    {
        int limit = resolveLimit(requestedLimit);
        Instant beforeInstant = resolveBefore(before);
        IntervalState state = state(interval);

        if (!state.initialSyncComplete.get())
        {
            throw new MarketDataSynchronizingException();
        }

        if (beforeInstant == null && limit == properties.historyLimit())
        {
            return getCachedLatestPage(interval, state, limit);
        }
        return loadPage(interval, beforeInstant, limit);
    }

    private BtcCandlesResponse getCachedLatestPage(
        BtcCandleInterval interval,
        IntervalState state,
        int limit
    )
    {
        synchronized (state.reconciliationLock)
        {
            synchronized (state.cacheLock)
            {
                if (state.historyCache == null)
                {
                    state.historyCache = loadPage(interval, null, limit);
                }
                return state.historyCache;
            }
        }
    }

    private BtcCandlesResponse loadPage(
        BtcCandleInterval interval,
        Instant before,
        int limit
    )
    {
        int queryLimit = limit + 1;
        List<StoredCandle> storedCandles = before == null
            ? repository.findLatest(SYMBOL, interval.value(), queryLimit)
            : repository.findBefore(
                SYMBOL,
                interval.value(),
                before,
                queryLimit
            );
        boolean hasMore = storedCandles.size() > limit;
        int firstResultIndex = hasMore ? 1 : 0;
        List<BtcCandle> candles = storedCandles
            .subList(firstResultIndex, storedCandles.size())
            .stream()
            .map(StoredCandle::toResponse)
            .toList();
        Long nextBefore = hasMore ? candles.getFirst().time() : null;
        return new BtcCandlesResponse(
            SYMBOL,
            PRODUCT_TYPE,
            interval.value(),
            candles,
            hasMore,
            nextBefore
        );
    }

    public void reconcileAll()
    {
        for (BtcCandleInterval interval : BtcCandleInterval.values())
        {
            reconcile(interval);
        }
    }

    void reconcile()
    {
        reconcile(BtcCandleInterval.ONE_HOUR);
    }

    void reconcile(BtcCandleInterval interval)
    {
        IntervalState state = state(interval);
        synchronized (state.reconciliationLock)
        {
            reconcileLocked(interval, state);
        }
    }

    private void reconcileLocked(
        BtcCandleInterval interval,
        IntervalState state
    )
    {
        Instant now = clock.instant();
        Instant cursor = repository.findLatestOpenTime(
            SYMBOL,
            interval.value()
        ).orElseGet(() ->
            properties.initialOpenTime().minus(interval.maximumSpan())
        );

        while (true)
        {
            List<BinanceKline> page = binance.getBtcKlines(
                interval,
                cursor,
                BinanceKlineSource.MAX_PAGE_SIZE
            );

            if (page.isEmpty())
            {
                throw new IllegalStateException(
                    "Binance returned no " + interval.value()
                        + " klines before the current candle"
                );
            }

            validateAscendingPage(page, cursor);

            List<StoredCandle> closedCandles = new ArrayList<>(page.size());
            for (BinanceKline kline : page)
            {
                if (kline.closeTime().isBefore(now))
                {
                    closedCandles.add(toStored(interval, kline));
                }
            }
            storeBatch(state, closedCandles);

            BinanceKline lastKline = page.getLast();
            if (!lastKline.closeTime().isBefore(now))
            {
                state.initialSyncComplete.set(true);
                return;
            }

            Instant nextCursor = lastKline.closeTime().plusMillis(1);
            if (!nextCursor.isAfter(cursor))
            {
                throw new IllegalStateException(
                    "Binance " + interval.value()
                        + " kline cursor did not advance"
                );
            }
            cursor = nextCursor;

            if (page.size() < BinanceKlineSource.MAX_PAGE_SIZE)
            {
                throw new IllegalStateException(
                    "Binance " + interval.value()
                        + " kline history ended before the current candle"
                );
            }
        }
    }

    public void storeClosed(LiveBtcCandle candle)
    {
        if (!candle.closed())
        {
            throw new IllegalArgumentException("Only closed candles are persistent");
        }
        if (!SYMBOL.equals(candle.symbol()))
        {
            throw new IllegalArgumentException("Unexpected market candle");
        }

        BtcCandleInterval interval = BtcCandleInterval.parse(candle.interval());
        IntervalState state = state(interval);
        synchronized (state.reconciliationLock)
        {
            Instant openTime = Instant.ofEpochSecond(candle.time());
            Optional<Instant> latest = repository.findLatestOpenTime(
                SYMBOL,
                interval.value()
            );

            if (latest.isEmpty()
                || openTime.isAfter(
                    interval.nextOpenTime(latest.orElseThrow())
                ))
            {
                reconcileLocked(interval, state);
            }

            storeBatch(state, List.of(new StoredCandle(
                candle.symbol(),
                interval.value(),
                openTime,
                candle.open(),
                candle.high(),
                candle.low(),
                candle.close(),
                candle.volume()
            )));
        }
    }

    private void storeBatch(
        IntervalState state,
        List<StoredCandle> candles
    )
    {
        if (candles.isEmpty())
        {
            return;
        }
        synchronized (state.cacheLock)
        {
            repository.upsertAll(candles);
            state.historyCache = null;
        }
    }

    private static void validateAscendingPage(
        List<BinanceKline> page,
        Instant cursor
    )
    {
        Instant previous = null;
        for (BinanceKline kline : page)
        {
            if (kline.openTime().isBefore(cursor)
                || (previous != null && !kline.openTime().isAfter(previous)))
            {
                throw new IllegalStateException(
                    "Binance klines are not strictly ascending"
                );
            }
            previous = kline.openTime();
        }
    }

    private static StoredCandle toStored(
        BtcCandleInterval interval,
        BinanceKline kline
    )
    {
        return new StoredCandle(
            SYMBOL,
            interval.value(),
            kline.openTime(),
            kline.open(),
            kline.high(),
            kline.low(),
            kline.close(),
            kline.volume()
        );
    }

    private int resolveLimit(Integer requestedLimit)
    {
        int limit = requestedLimit == null
            ? properties.historyLimit()
            : requestedLimit;
        if (limit < 1 || limit > CandleHistoryQuery.MAX_PAGE_SIZE)
        {
            throw new IllegalArgumentException(
                "Candle history limit must be between 1 and "
                    + CandleHistoryQuery.MAX_PAGE_SIZE
            );
        }
        return limit;
    }

    private static Instant resolveBefore(Long before)
    {
        if (before == null)
        {
            return null;
        }
        if (before <= 0)
        {
            throw new IllegalArgumentException(
                "Candle history before cursor must be positive"
            );
        }

        try
        {
            return Instant.ofEpochSecond(before);
        }
        catch (DateTimeException exception)
        {
            throw new IllegalArgumentException(
                "Candle history before cursor is outside the supported range",
                exception
            );
        }
    }

    private IntervalState state(BtcCandleInterval interval)
    {
        if (interval == null)
        {
            throw new IllegalArgumentException(
                "BTC candle interval is required"
            );
        }
        return intervalStates.get(interval);
    }

    private static final class IntervalState
    {
        private final Object reconciliationLock = new Object();
        private final Object cacheLock = new Object();
        private final AtomicBoolean initialSyncComplete = new AtomicBoolean();
        private BtcCandlesResponse historyCache;
    }
}
