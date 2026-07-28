package com.raretable.casino.paper_trading.trading;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.raretable.casino.paper_trading.market_data.BinanceFundingRate;

@Repository
public class JdbcPaperFundingRepository
{
    private final JdbcClient jdbcClient;

    JdbcPaperFundingRepository(JdbcClient jdbcClient)
    {
        this.jdbcClient = jdbcClient;
    }

    public Optional<Instant> findEarliestFundingEligibility()
    {
        return jdbcClient.sql("""
                SELECT funding_eligible_from
                FROM paper_trades
                WHERE funding_eligible_from IS NOT NULL
                ORDER BY funding_eligible_from
                LIMIT 1
                """)
            .query((resultSet, rowNumber) ->
                resultSet.getTimestamp("funding_eligible_from").toInstant()
            )
            .optional();
    }

    public Optional<Instant> findLatestFundingTime()
    {
        return jdbcClient.sql("""
                SELECT funding_time
                FROM paper_funding_events
                ORDER BY funding_time DESC
                LIMIT 1
                """)
            .query((resultSet, rowNumber) ->
                resultSet.getTimestamp("funding_time").toInstant()
            )
            .optional();
    }

    public PaperFundingEvent store(
        BinanceFundingRate funding,
        Instant ingestedAt
    )
    {
        UUID id = UUID.randomUUID();
        int inserted = jdbcClient.sql("""
                INSERT INTO paper_funding_events
                (
                    id, symbol, funding_time, rate_type,
                    funding_rate, mark_price, ingested_at
                )
                VALUES
                (
                    :id, :symbol, :fundingTime, :rateType,
                    :fundingRate, :markPrice, :ingestedAt
                )
                ON CONFLICT (symbol, funding_time, rate_type) DO NOTHING
                """)
            .param("id", id)
            .param("symbol", funding.symbol())
            .param("fundingTime", Timestamp.from(funding.fundingTime()))
            .param("rateType", funding.rateType())
            .param("fundingRate", funding.fundingRate())
            .param("markPrice", funding.markPrice())
            .param("ingestedAt", Timestamp.from(ingestedAt))
            .update();
        if (inserted == 1)
        {
            return new PaperFundingEvent(
                id,
                funding.symbol(),
                funding.fundingTime(),
                funding.rateType(),
                funding.fundingRate(),
                funding.markPrice()
            );
        }

        PaperFundingEvent existing = find(
            funding.symbol(),
            funding.fundingTime(),
            funding.rateType()
        ).orElseThrow(() -> new IllegalStateException(
            "Conflicting paper funding event could not be read"
        ));
        requireSame(existing, funding);
        return existing;
    }

    public List<PaperFundingEvent> findPendingEvents()
    {
        return jdbcClient.sql("""
                SELECT event.id, event.symbol, event.funding_time,
                       event.rate_type, event.funding_rate, event.mark_price
                FROM paper_funding_events AS event
                WHERE EXISTS
                (
                    SELECT 1
                    FROM paper_trades AS trade
                    WHERE trade.funding_eligible_from IS NOT NULL
                      AND trade.funding_eligible_from <= event.funding_time
                      AND (
                          trade.closed_at IS NULL
                          OR trade.closed_at > event.funding_time
                      )
                      AND NOT EXISTS
                      (
                          SELECT 1
                          FROM paper_trade_funding_settlements AS settlement
                          WHERE settlement.funding_event_id = event.id
                            AND settlement.trade_id = trade.id
                      )
                )
                ORDER BY event.funding_time, event.id
                """)
            .query(JdbcPaperFundingRepository::mapEvent)
            .list();
    }

    public List<UUID> findEligibleUserIds(PaperFundingEvent event)
    {
        return jdbcClient.sql("""
                SELECT DISTINCT trade.user_id
                FROM paper_trades AS trade
                WHERE trade.funding_eligible_from IS NOT NULL
                  AND trade.funding_eligible_from <= :fundingTime
                  AND (
                      trade.closed_at IS NULL
                      OR trade.closed_at > :fundingTime
                  )
                  AND NOT EXISTS
                  (
                      SELECT 1
                      FROM paper_trade_funding_settlements AS settlement
                      WHERE settlement.funding_event_id = :eventId
                        AND settlement.trade_id = trade.id
                  )
                ORDER BY trade.user_id
                """)
            .param("fundingTime", Timestamp.from(event.fundingTime()))
            .param("eventId", event.id())
            .query(UUID.class)
            .list();
    }

    public boolean insertSettlement(
        PaperFundingEvent event,
        PaperTrade trade,
        BigDecimal notional,
        BigDecimal amount,
        Instant settledAt
    )
    {
        int inserted = jdbcClient.sql("""
                INSERT INTO paper_trade_funding_settlements
                (
                    funding_event_id, trade_id, user_id, quantity,
                    notional_usd, amount_usd, settled_at
                )
                VALUES
                (
                    :eventId, :tradeId, :userId, :quantity,
                    :notional, :amount, :settledAt
                )
                ON CONFLICT (funding_event_id, trade_id) DO NOTHING
                """)
            .param("eventId", event.id())
            .param("tradeId", trade.id())
            .param("userId", trade.userId())
            .param("quantity", trade.quantity())
            .param("notional", notional)
            .param("amount", amount)
            .param("settledAt", Timestamp.from(settledAt))
            .update();
        return inserted == 1;
    }

    private Optional<PaperFundingEvent> find(
        String symbol,
        Instant fundingTime,
        String rateType
    )
    {
        return jdbcClient.sql("""
                SELECT id, symbol, funding_time, rate_type,
                       funding_rate, mark_price
                FROM paper_funding_events
                WHERE symbol = :symbol
                  AND funding_time = :fundingTime
                  AND rate_type = :rateType
                """)
            .param("symbol", symbol)
            .param("fundingTime", Timestamp.from(fundingTime))
            .param("rateType", rateType)
            .query(JdbcPaperFundingRepository::mapEvent)
            .optional();
    }

    private static PaperFundingEvent mapEvent(
        java.sql.ResultSet resultSet,
        int rowNumber
    ) throws java.sql.SQLException
    {
        return new PaperFundingEvent(
            resultSet.getObject("id", UUID.class),
            resultSet.getString("symbol"),
            resultSet.getTimestamp("funding_time").toInstant(),
            resultSet.getString("rate_type"),
            resultSet.getBigDecimal("funding_rate"),
            resultSet.getBigDecimal("mark_price")
        );
    }

    private static void requireSame(
        PaperFundingEvent existing,
        BinanceFundingRate funding
    )
    {
        if (existing.fundingRate().compareTo(funding.fundingRate()) != 0
            || existing.markPrice().compareTo(funding.markPrice()) != 0)
        {
            throw new IllegalStateException(
                "Binance funding history changed for an ingested settlement"
            );
        }
    }
}
