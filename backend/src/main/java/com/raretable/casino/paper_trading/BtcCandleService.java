package com.raretable.casino.paper_trading;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;

@Service
final class BtcCandleService implements CandleHistoryQuery
{
    static final String SYMBOL = "BTCUSDT";
    static final String INTERVAL = "1h";
    static final Duration INTERVAL_DURATION = Duration.ofHours(1);

    private final MarketCandleRepository repository;
    private final BinanceKlineSource binance;
    private final MarketDataProperties properties;
    private final Clock clock;
    private final AtomicBoolean initialSyncComplete = new AtomicBoolean();
    private BtcCandlesResponse historyCache;

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
    }

    @Override
    public BtcCandlesResponse getBtcCandles(
        Long before,
        Integer requestedLimit
    )
    {
        int limit = resolveLimit(requestedLimit);
        Instant beforeInstant = resolveBefore(before);

        if (!initialSyncComplete.get())
        {
            throw new MarketDataSynchronizingException();
        }

        if (beforeInstant == null && limit == properties.historyLimit())
        {
            return getCachedLatestPage(limit);
        }
        return loadPage(beforeInstant, limit);
    }

    private synchronized BtcCandlesResponse getCachedLatestPage(int limit)
    {
        if (historyCache == null)
        {
            historyCache = loadPage(null, limit);
        }
        return historyCache;
    }

    private BtcCandlesResponse loadPage(Instant before, int limit)
    {
        int queryLimit = limit + 1;
        List<StoredCandle> storedCandles = before == null
            ? repository.findLatest(SYMBOL, INTERVAL, queryLimit)
            : repository.findBefore(
                SYMBOL,
                INTERVAL,
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
            INTERVAL,
            candles,
            hasMore,
            nextBefore
        );
    }

    synchronized void reconcile()
    {
        Instant now = clock.instant();
        Instant firstOpenCandle = now.truncatedTo(ChronoUnit.HOURS);
        Instant cursor = repository.findLatestOpenTime(SYMBOL, INTERVAL)
            .orElse(properties.initialOpenTime());

        while (cursor.isBefore(firstOpenCandle))
        {
            List<BinanceKline> page = binance.getBtcOneHourKlines(
                cursor,
                BinanceRestKlineClient.MAX_PAGE_SIZE
            );

            if (page.isEmpty())
            {
                throw new IllegalStateException(
                    "Binance returned no klines before the current hour"
                );
            }

            validateAscendingPage(page, cursor);

            List<StoredCandle> closedCandles = new ArrayList<>(page.size());
            for (BinanceKline kline : page)
            {
                if (kline.closeTime().isBefore(now))
                {
                    closedCandles.add(toStored(kline));
                }
            }
            repository.upsertAll(closedCandles);
            if (!closedCandles.isEmpty())
            {
                historyCache = null;
            }

            Instant nextCursor = page.getLast()
                .openTime()
                .plus(INTERVAL_DURATION);
            if (!nextCursor.isAfter(cursor))
            {
                throw new IllegalStateException(
                    "Binance kline cursor did not advance"
                );
            }
            cursor = nextCursor;

            if (page.size() < BinanceRestKlineClient.MAX_PAGE_SIZE
                && cursor.isBefore(firstOpenCandle))
            {
                throw new IllegalStateException(
                    "Binance kline history ended before the current hour"
                );
            }
        }

        initialSyncComplete.set(true);
    }

    synchronized void storeClosed(LiveBtcCandle candle)
    {
        if (!candle.closed())
        {
            throw new IllegalArgumentException("Only closed candles are persistent");
        }
        if (!SYMBOL.equals(candle.symbol()) || !INTERVAL.equals(candle.interval()))
        {
            throw new IllegalArgumentException("Unexpected market candle");
        }

        Instant openTime = Instant.ofEpochSecond(candle.time());
        Optional<Instant> latest = repository.findLatestOpenTime(SYMBOL, INTERVAL);

        if (latest.isEmpty()
            || openTime.isAfter(latest.get().plus(INTERVAL_DURATION)))
        {
            reconcile();
        }

        repository.upsertAll(List.of(new StoredCandle(
            candle.symbol(),
            candle.interval(),
            openTime,
            candle.open(),
            candle.high(),
            candle.low(),
            candle.close(),
            candle.volume()
        )));
        historyCache = null;
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

    private static StoredCandle toStored(BinanceKline kline)
    {
        return new StoredCandle(
            SYMBOL,
            INTERVAL,
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
}
