package com.raretable.casino.paper_trading.market_data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.raretable.casino.paper_trading.api.BtcCandle;
import com.raretable.casino.paper_trading.api.BtcCandlesResponse;
import com.raretable.casino.paper_trading.market_data.binance.MarketDataProperties;
import com.raretable.casino.paper_trading.market_data.persistence.MarketCandleRepository;
import com.raretable.casino.paper_trading.market_data.persistence.StoredCandle;

class BtcCandleServiceTests
{
    private static final Instant FIRST =
        Instant.parse("2026-06-15T00:00:00Z");

    @Test
    void pagesHistoryAndExcludesTheOpenRestCandle()
    {
        Instant currentHour = FIRST.plus(Duration.ofHours(1_001));
        InMemoryRepository repository = new InMemoryRepository();
        RecordingSource source = new RecordingSource();
        source.pages.add(hourlyPage(FIRST, 1_000));
        source.pages.add(List.of(
            kline(FIRST.plus(Duration.ofHours(1_000))),
            kline(FIRST.plus(Duration.ofHours(1_001)))
        ));
        BtcCandleService service = service(
            repository,
            source,
            currentHour.plus(Duration.ofMinutes(30)),
            FIRST,
            2
        );

        assertThrows(
            MarketDataSynchronizingException.class,
            service::getBtcCandles
        );

        service.reconcile();

        assertEquals(
            List.of(
                FIRST.minus(Duration.ofHours(1)),
                FIRST.plus(Duration.ofHours(1_000))
            ),
            source.requestedStarts
        );
        assertEquals(
            List.of(
                BtcCandleInterval.ONE_HOUR,
                BtcCandleInterval.ONE_HOUR
            ),
            source.requestedIntervals
        );
        assertEquals(List.of(1_000, 1_000), source.requestedLimits);
        assertEquals(1_001, repository.candles("1h").size());

        BtcCandlesResponse response = service.getBtcCandles();
        assertEquals("BTCUSDT", response.symbol());
        assertEquals("1h", response.interval());
        assertEquals(2, response.candles().size());
        assertEquals(true, response.hasMore());
        assertEquals(
            FIRST.plus(Duration.ofHours(999)).getEpochSecond(),
            response.candles().get(0).time()
        );
        assertEquals(
            FIRST.plus(Duration.ofHours(999)).getEpochSecond(),
            response.nextBefore()
        );
        assertEquals(
            FIRST.plus(Duration.ofHours(1_000)).getEpochSecond(),
            response.candles().get(1).time()
        );
    }

    @Test
    void returnsExclusiveOlderPagesAndSignalsTheHistoryBoundary()
    {
        InMemoryRepository repository = new InMemoryRepository();
        RecordingSource source = new RecordingSource();
        source.pages.add(hourlyPage(FIRST, 6));
        BtcCandleService service = service(
            repository,
            source,
            FIRST.plus(Duration.ofHours(5)).plus(Duration.ofMinutes(30)),
            FIRST,
            2
        );
        service.reconcile();

        BtcCandlesResponse preceding = service.getBtcCandles(
            FIRST.plus(Duration.ofHours(3)).getEpochSecond(),
            2
        );
        assertEquals(
            List.of(
                FIRST.plus(Duration.ofHours(1)).getEpochSecond(),
                FIRST.plus(Duration.ofHours(2)).getEpochSecond()
            ),
            preceding.candles().stream().map(BtcCandle::time).toList()
        );
        assertEquals(true, preceding.hasMore());
        assertEquals(
            FIRST.plus(Duration.ofHours(1)).getEpochSecond(),
            preceding.nextBefore()
        );

        BtcCandlesResponse latest = service.getBtcCandles();
        assertEquals(
            List.of(
                FIRST.plus(Duration.ofHours(3)).getEpochSecond(),
                FIRST.plus(Duration.ofHours(4)).getEpochSecond()
            ),
            latest.candles().stream().map(BtcCandle::time).toList()
        );
        assertEquals(true, latest.hasMore());
        assertEquals(
            FIRST.plus(Duration.ofHours(3)).getEpochSecond(),
            latest.nextBefore()
        );
        assertEquals(latest, service.getBtcCandles());
        assertEquals(1, repository.findLatestCalls);

        BtcCandlesResponse firstPage = service.getBtcCandles(
            FIRST.plus(Duration.ofHours(2)).getEpochSecond(),
            2
        );
        assertEquals(
            List.of(
                FIRST.getEpochSecond(),
                FIRST.plus(Duration.ofHours(1)).getEpochSecond()
            ),
            firstPage.candles().stream().map(BtcCandle::time).toList()
        );
        assertEquals(false, firstPage.hasMore());
        assertEquals(null, firstPage.nextBefore());

        BtcCandlesResponse exhausted = service.getBtcCandles(
            FIRST.getEpochSecond(),
            2
        );
        assertEquals(List.of(), exhausted.candles());
        assertEquals(false, exhausted.hasMore());
        assertEquals(null, exhausted.nextBefore());
    }

    @Test
    void rejectsInvalidPaginationParameters()
    {
        BtcCandleService service = service(
            new InMemoryRepository(),
            new RecordingSource(),
            FIRST,
            FIRST,
            2_000
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> service.getBtcCandles(null, 0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> service.getBtcCandles(
                null,
                CandleHistoryQuery.MAX_PAGE_SIZE + 1
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> service.getBtcCandles(0L, 1)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> service.getBtcCandles(Long.MAX_VALUE, 1)
        );
    }

    @Test
    void reconcilesFromLatestStoredCandleWithOneHourOverlap()
    {
        InMemoryRepository repository = new InMemoryRepository();
        repository.upsertAll(List.of(stored(FIRST)));
        RecordingSource source = new RecordingSource();
        source.pages.add(List.of(
            kline(FIRST, "65050.15"),
            kline(FIRST.plus(Duration.ofHours(1))),
            kline(FIRST.plus(Duration.ofHours(2))),
            kline(FIRST.plus(Duration.ofHours(3)))
        ));
        BtcCandleService service = service(
            repository,
            source,
            FIRST.plus(Duration.ofHours(3)).plus(Duration.ofMinutes(5)),
            FIRST,
            2_000
        );

        service.reconcile();

        assertEquals(
            List.of(FIRST),
            source.requestedStarts
        );
        assertEquals(3, repository.candles("1h").size());
        assertEquals(
            0,
            repository.candles("1h").get(FIRST).close()
                .compareTo(new BigDecimal("65050.15"))
        );
        assertEquals(
            FIRST.plus(Duration.ofHours(2)),
            repository.findLatestOpenTime("BTCUSDT", "1h").orElseThrow()
        );
    }

    @Test
    void synchronizesAndCachesEachIntervalIndependently()
    {
        InMemoryRepository repository = new InMemoryRepository();
        RecordingSource source = new RecordingSource();
        source.pages.add(List.of(
            kline(FIRST),
            kline(FIRST.plus(Duration.ofHours(1))),
            kline(FIRST.plus(Duration.ofHours(2)))
        ));
        source.pages.add(List.of(
            kline(FIRST, Duration.ofHours(2)),
            kline(FIRST.plus(Duration.ofHours(2)), Duration.ofHours(2))
        ));
        BtcCandleService service = service(
            repository,
            source,
            FIRST.plus(Duration.ofHours(2)).plus(Duration.ofMinutes(30)),
            FIRST,
            2
        );

        service.reconcile(BtcCandleInterval.ONE_HOUR);

        assertEquals(
            "1h",
            service.getBtcCandles(
                BtcCandleInterval.ONE_HOUR,
                null,
                null
            ).interval()
        );
        assertThrows(
            MarketDataSynchronizingException.class,
            () -> service.getBtcCandles(
                BtcCandleInterval.TWO_HOURS,
                null,
                null
            )
        );

        service.reconcile(BtcCandleInterval.TWO_HOURS);

        BtcCandlesResponse twoHours = service.getBtcCandles(
            BtcCandleInterval.TWO_HOURS,
            null,
            null
        );
        assertEquals("2h", twoHours.interval());
        assertEquals(
            List.of(
                BtcCandleInterval.ONE_HOUR,
                BtcCandleInterval.TWO_HOURS
            ),
            source.requestedIntervals
        );
        assertEquals(2, repository.candles("1h").size());
        assertEquals(1, repository.candles("2h").size());
        service.getBtcCandles(BtcCandleInterval.ONE_HOUR, null, null);
        service.getBtcCandles(BtcCandleInterval.TWO_HOURS, null, null);
        assertEquals(2, repository.findLatestCalls);
    }

    @Test
    void monthlyBackfillLooksBehindListingAndPagesFromCloseTime()
    {
        Instant firstMonth = Instant.parse("1900-01-01T00:00:00Z");
        List<BinanceKline> firstPage = monthlyPage(firstMonth, 1_000);
        Instant currentMonth = firstPage.getLast()
            .closeTime()
            .plusMillis(1);
        InMemoryRepository repository = new InMemoryRepository();
        RecordingSource source = new RecordingSource();
        source.pages.add(firstPage);
        source.pages.add(List.of(monthlyKline(currentMonth)));
        BtcCandleService service = service(
            repository,
            source,
            currentMonth.plus(Duration.ofDays(1)),
            firstMonth,
            2_000
        );

        service.reconcile(BtcCandleInterval.ONE_MONTH);

        assertEquals(
            List.of(
                firstMonth.minus(Duration.ofDays(31)),
                currentMonth
            ),
            source.requestedStarts
        );
        assertEquals(
            List.of(
                BtcCandleInterval.ONE_MONTH,
                BtcCandleInterval.ONE_MONTH
            ),
            source.requestedIntervals
        );
        assertEquals(1_000, repository.candles("1M").size());
    }

    @Test
    void acceptsAdjacentMonthlyCloseAndRepairsACalendarMonthGap()
    {
        Instant february = Instant.parse("2024-02-01T00:00:00Z");
        Instant march = Instant.parse("2024-03-01T00:00:00Z");
        Instant april = Instant.parse("2024-04-01T00:00:00Z");
        Instant may = Instant.parse("2024-05-01T00:00:00Z");
        Instant june = Instant.parse("2024-06-01T00:00:00Z");
        InMemoryRepository repository = new InMemoryRepository();
        repository.upsertAll(List.of(stored(
            BtcCandleInterval.ONE_MONTH,
            monthlyKline(february)
        )));
        RecordingSource source = new RecordingSource();
        source.pages.add(List.of(
            monthlyKline(march),
            monthlyKline(april),
            monthlyKline(may),
            monthlyKline(june)
        ));
        BtcCandleService service = service(
            repository,
            source,
            june.plus(Duration.ofDays(1)),
            february,
            2_000
        );

        service.storeClosed(liveClosed(
            BtcCandleInterval.ONE_MONTH,
            monthlyKline(march)
        ));
        assertEquals(List.of(), source.requestedStarts);

        service.storeClosed(liveClosed(
            BtcCandleInterval.ONE_MONTH,
            monthlyKline(may)
        ));

        assertEquals(List.of(march), source.requestedStarts);
        assertEquals(
            List.of(BtcCandleInterval.ONE_MONTH),
            source.requestedIntervals
        );
        assertEquals(
            List.of(february, march, april, may),
            repository.candles("1M").keySet().stream().toList()
        );
    }

    private static BtcCandleService service(
        MarketCandleRepository repository,
        BinanceKlineSource source,
        Instant now,
        Instant initialOpenTime,
        int historyLimit
    )
    {
        MarketDataProperties properties = new MarketDataProperties(
            true,
            URI.create("https://api.binance.com"),
            URI.create(
                "wss://stream.binance.com:9443/ws/btcusdt@kline_1h"
            ),
            initialOpenTime,
            Duration.ofMinutes(1),
            Duration.ofSeconds(5),
            historyLimit
        );
        return new BtcCandleService(
            repository,
            source,
            properties,
            Clock.fixed(now, ZoneOffset.UTC)
        );
    }

    private static List<BinanceKline> hourlyPage(Instant first, int count)
    {
        List<BinanceKline> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++)
        {
            result.add(kline(first.plus(Duration.ofHours(index))));
        }
        return result;
    }

    private static BinanceKline kline(Instant openTime)
    {
        return kline(openTime, "65300.40");
    }

    private static BinanceKline kline(Instant openTime, String close)
    {
        return new BinanceKline(
            openTime,
            openTime.plus(Duration.ofHours(1)).minusMillis(1),
            new BigDecimal("65000.10"),
            new BigDecimal("65500.20"),
            new BigDecimal("64900.30"),
            new BigDecimal(close),
            new BigDecimal("123.45")
        );
    }

    private static BinanceKline kline(
        Instant openTime,
        Duration duration
    )
    {
        return new BinanceKline(
            openTime,
            openTime.plus(duration).minusMillis(1),
            new BigDecimal("65000.10"),
            new BigDecimal("65500.20"),
            new BigDecimal("64900.30"),
            new BigDecimal("65300.40"),
            new BigDecimal("123.45")
        );
    }

    private static List<BinanceKline> monthlyPage(
        Instant first,
        int count
    )
    {
        List<BinanceKline> result = new ArrayList<>(count);
        ZonedDateTime open = first.atZone(ZoneOffset.UTC);
        for (int index = 0; index < count; index++)
        {
            result.add(monthlyKline(open.plusMonths(index).toInstant()));
        }
        return result;
    }

    private static BinanceKline monthlyKline(Instant openTime)
    {
        Instant closeTime = openTime.atZone(ZoneOffset.UTC)
            .plusMonths(1)
            .toInstant()
            .minusMillis(1);
        return new BinanceKline(
            openTime,
            closeTime,
            new BigDecimal("65000.10"),
            new BigDecimal("65500.20"),
            new BigDecimal("64900.30"),
            new BigDecimal("65300.40"),
            new BigDecimal("123.45")
        );
    }

    private static StoredCandle stored(Instant openTime)
    {
        BinanceKline kline = kline(openTime);
        return stored(BtcCandleInterval.ONE_HOUR, kline);
    }

    private static StoredCandle stored(
        BtcCandleInterval interval,
        BinanceKline kline
    )
    {
        return new StoredCandle(
            "BTCUSDT",
            interval.value(),
            kline.openTime(),
            kline.open(),
            kline.high(),
            kline.low(),
            kline.close(),
            kline.volume()
        );
    }

    private static LiveBtcCandle liveClosed(
        BtcCandleInterval interval,
        BinanceKline kline
    )
    {
        return new LiveBtcCandle(
            "BTCUSDT",
            interval.value(),
            kline.openTime().getEpochSecond(),
            kline.open(),
            kline.high(),
            kline.low(),
            kline.close(),
            kline.volume(),
            true
        );
    }

    private static final class RecordingSource implements BinanceKlineSource
    {
        private final List<List<BinanceKline>> pages = new ArrayList<>();
        private final List<BtcCandleInterval> requestedIntervals =
            new ArrayList<>();
        private final List<Instant> requestedStarts = new ArrayList<>();
        private final List<Integer> requestedLimits = new ArrayList<>();

        @Override
        public List<BinanceKline> getBtcKlines(
            BtcCandleInterval interval,
            Instant startTime,
            int limit
        )
        {
            requestedIntervals.add(interval);
            requestedStarts.add(startTime);
            requestedLimits.add(limit);
            return pages.get(requestedStarts.size() - 1);
        }
    }

    private static final class InMemoryRepository
        implements MarketCandleRepository
    {
        private final Map<String, Map<Instant, StoredCandle>> candles =
            new TreeMap<>();
        private int findLatestCalls;

        private Map<Instant, StoredCandle> candles(String interval)
        {
            return candles.computeIfAbsent(interval, ignored -> new TreeMap<>());
        }

        @Override
        public Optional<Instant> findLatestOpenTime(
            String symbol,
            String interval
        )
        {
            return candles(interval).keySet().stream()
                .max(Comparator.naturalOrder());
        }

        @Override
        public List<StoredCandle> findLatest(
            String symbol,
            String interval,
            int limit
        )
        {
            findLatestCalls++;
            return candles(interval).values().stream()
                .sorted(Comparator.comparing(StoredCandle::openTime).reversed())
                .limit(limit)
                .sorted(Comparator.comparing(StoredCandle::openTime))
                .toList();
        }

        @Override
        public List<StoredCandle> findBefore(
            String symbol,
            String interval,
            Instant before,
            int limit
        )
        {
            return candles(interval).values().stream()
                .filter(candle -> candle.openTime().isBefore(before))
                .sorted(Comparator.comparing(StoredCandle::openTime).reversed())
                .limit(limit)
                .sorted(Comparator.comparing(StoredCandle::openTime))
                .toList();
        }

        @Override
        public void upsertAll(List<StoredCandle> newCandles)
        {
            newCandles.forEach(candle ->
                candles(candle.interval()).put(candle.openTime(), candle)
            );
        }
    }
}
