package com.raretable.casino.paper_trading;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcMarketCandleRepository implements MarketCandleRepository
{
    private static final String UPSERT_SQL = """
        INSERT INTO market_candles
            (symbol, candle_interval, open_time, open, high, low, close, volume)
        VALUES
            (:symbol, :interval, :openTime, :open, :high, :low, :close, :volume)
        ON CONFLICT (symbol, candle_interval, open_time)
        DO UPDATE SET
            open = EXCLUDED.open,
            high = EXCLUDED.high,
            low = EXCLUDED.low,
            close = EXCLUDED.close,
            volume = EXCLUDED.volume
        """;

    private final JdbcClient jdbcClient;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    JdbcMarketCandleRepository(
        JdbcClient jdbcClient,
        NamedParameterJdbcTemplate namedJdbcTemplate
    )
    {
        this.jdbcClient = jdbcClient;
        this.namedJdbcTemplate = namedJdbcTemplate;
    }

    @Override
    public Optional<Instant> findLatestOpenTime(String symbol, String interval)
    {
        return jdbcClient.sql("""
                SELECT open_time
                FROM market_candles
                WHERE symbol = :symbol AND candle_interval = :interval
                ORDER BY open_time DESC
                LIMIT 1
                """)
            .param("symbol", symbol)
            .param("interval", interval)
            .query((resultSet, rowNumber) ->
                resultSet.getTimestamp("open_time").toInstant()
            )
            .optional();
    }

    @Override
    public List<StoredCandle> findLatest(
        String symbol,
        String interval,
        int limit
    )
    {
        return jdbcClient.sql("""
                SELECT symbol, candle_interval, open_time,
                       open, high, low, close, volume
                FROM
                (
                    SELECT symbol, candle_interval, open_time,
                           open, high, low, close, volume
                    FROM market_candles
                    WHERE symbol = :symbol AND candle_interval = :interval
                    ORDER BY open_time DESC
                    LIMIT :limit
                ) latest
                ORDER BY open_time ASC
                """)
            .param("symbol", symbol)
            .param("interval", interval)
            .param("limit", limit)
            .query((resultSet, rowNumber) -> new StoredCandle(
                resultSet.getString("symbol"),
                resultSet.getString("candle_interval"),
                resultSet.getTimestamp("open_time").toInstant(),
                resultSet.getBigDecimal("open"),
                resultSet.getBigDecimal("high"),
                resultSet.getBigDecimal("low"),
                resultSet.getBigDecimal("close"),
                resultSet.getBigDecimal("volume")
            ))
            .list();
    }

    @Override
    public List<StoredCandle> findBefore(
        String symbol,
        String interval,
        Instant before,
        int limit
    )
    {
        return jdbcClient.sql("""
                SELECT symbol, candle_interval, open_time,
                       open, high, low, close, volume
                FROM
                (
                    SELECT symbol, candle_interval, open_time,
                           open, high, low, close, volume
                    FROM market_candles
                    WHERE symbol = :symbol
                      AND candle_interval = :interval
                      AND open_time < :before
                    ORDER BY open_time DESC
                    LIMIT :limit
                ) preceding
                ORDER BY open_time ASC
                """)
            .param("symbol", symbol)
            .param("interval", interval)
            .param("before", Timestamp.from(before))
            .param("limit", limit)
            .query((resultSet, rowNumber) -> new StoredCandle(
                resultSet.getString("symbol"),
                resultSet.getString("candle_interval"),
                resultSet.getTimestamp("open_time").toInstant(),
                resultSet.getBigDecimal("open"),
                resultSet.getBigDecimal("high"),
                resultSet.getBigDecimal("low"),
                resultSet.getBigDecimal("close"),
                resultSet.getBigDecimal("volume")
            ))
            .list();
    }

    @Override
    @Transactional
    public void upsertAll(List<StoredCandle> candles)
    {
        if (candles.isEmpty())
        {
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, ?>[] parameters = candles.stream()
            .map(candle -> Map.<String, Object>of(
                "symbol", candle.symbol(),
                "interval", candle.interval(),
                "openTime", Timestamp.from(candle.openTime()),
                "open", candle.open(),
                "high", candle.high(),
                "low", candle.low(),
                "close", candle.close(),
                "volume", candle.volume()
            ))
            .toArray(Map[]::new);

        namedJdbcTemplate.batchUpdate(UPSERT_SQL, parameters);
    }
}
