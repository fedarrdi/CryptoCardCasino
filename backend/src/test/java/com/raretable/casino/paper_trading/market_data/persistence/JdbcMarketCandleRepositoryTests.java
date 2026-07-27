package com.raretable.casino.paper_trading.market_data.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.raretable.casino.InfrastructureTestConfiguration;

@SpringBootTest
@ActiveProfiles("test")
@Import(InfrastructureTestConfiguration.class)
@Transactional
class JdbcMarketCandleRepositoryTests
{
    @Autowired
    private MarketCandleRepository repository;

    @Test
    void upsertsIdempotentlyAndReturnsLatestCandlesAscending()
    {
        Instant first = Instant.parse("2026-07-27T06:00:00Z");
        Instant second = Instant.parse("2026-07-27T07:00:00Z");
        Instant third = Instant.parse("2026-07-27T08:00:00Z");

        repository.upsertAll(List.of(
            candle(first, "65000.10"),
            candle(second, "65100.20"),
            candle(third, "65200.30"),
            candle("ETHUSDT", "1h", second, "3200.10"),
            candle("BTCUSDT", "5m", second, "65125.20")
        ));
        repository.upsertAll(List.of(candle(second, "65150.25")));

        assertEquals(
            third,
            repository.findLatestOpenTime("BTCUSDT", "1h").orElseThrow()
        );

        List<StoredCandle> latest = repository.findLatest(
            "BTCUSDT",
            "1h",
            2
        );
        assertEquals(List.of(second, third), latest.stream()
            .map(StoredCandle::openTime)
            .toList());
        assertEquals(
            0,
            latest.getFirst().close().compareTo(new BigDecimal("65150.25"))
        );
        assertEquals(
            3,
            repository.findLatest("BTCUSDT", "1h", 10).size()
        );

        List<StoredCandle> preceding = repository.findBefore(
            "BTCUSDT",
            "1h",
            third,
            10
        );
        assertEquals(
            List.of(first, second),
            preceding.stream().map(StoredCandle::openTime).toList()
        );
        assertEquals(
            List.of(second),
            repository.findBefore("BTCUSDT", "1h", third, 1)
                .stream()
                .map(StoredCandle::openTime)
                .toList()
        );
    }

    private static StoredCandle candle(Instant openTime, String close)
    {
        return candle("BTCUSDT", "1h", openTime, close);
    }

    private static StoredCandle candle(
        String symbol,
        String interval,
        Instant openTime,
        String close
    )
    {
        BigDecimal closingPrice = new BigDecimal(close);
        return new StoredCandle(
            symbol,
            interval,
            openTime,
            closingPrice,
            closingPrice,
            closingPrice,
            closingPrice,
            new BigDecimal("123.45")
        );
    }
}
