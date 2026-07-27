package com.raretable.casino.paper_trading.trading;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPaperTradingRepository
{
    private static final String TRADE_COLUMNS = """
        id, user_id, client_order_id, symbol, order_type, margin_mode,
        side, status,
        leverage, margin_usd, notional_usd, quantity, entry_price,
        exit_price, stop_loss, take_profit, risk_control_version,
        realized_pnl, close_reason, opened_at, closed_at
        """;

    private final JdbcClient jdbcClient;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    JdbcPaperTradingRepository(
        JdbcClient jdbcClient,
        NamedParameterJdbcTemplate namedJdbcTemplate
    )
    {
        this.jdbcClient = jdbcClient;
        this.namedJdbcTemplate = namedJdbcTemplate;
    }

    public PaperTradingAccount getAccount(UUID userId)
    {
        return accountQuery(false, userId);
    }

    public PaperTradingAccount lockAccount(UUID userId)
    {
        return accountQuery(true, userId);
    }

    public void updateBalance(UUID userId, BigDecimal balance, Instant updatedAt)
    {
        int updated = jdbcClient.sql("""
                UPDATE paper_trading_accounts
                SET balance_usd = :balance, updated_at = :updatedAt
                WHERE user_id = :userId
                """)
            .param("balance", balance)
            .param("updatedAt", Timestamp.from(updatedAt))
            .param("userId", userId)
            .update();
        requireOneRow(updated, "Paper-trading account balance was not updated");
    }

    public List<PaperTrade> findOpenTrades(UUID userId)
    {
        return jdbcClient.sql("""
                SELECT %s
                FROM paper_trades
                WHERE user_id = :userId AND status = 'OPEN'
                ORDER BY opened_at DESC, id
                """.formatted(TRADE_COLUMNS))
            .param("userId", userId)
            .query(JdbcPaperTradingRepository::mapTrade)
            .list();
    }

    public List<PaperTrade> findClosedTrades(UUID userId, int limit)
    {
        return jdbcClient.sql("""
                SELECT %s
                FROM paper_trades
                WHERE user_id = :userId AND status = 'CLOSED'
                ORDER BY closed_at DESC, id
                LIMIT :limit
                """.formatted(TRADE_COLUMNS))
            .param("userId", userId)
            .param("limit", limit)
            .query(JdbcPaperTradingRepository::mapTrade)
            .list();
    }

    public Optional<PaperTrade> findTrade(UUID tradeId)
    {
        return tradeQuery("""
            SELECT %s
            FROM paper_trades
            WHERE id = :tradeId
            """, tradeId, null);
    }

    public Optional<PaperTrade> findByClientOrderId(
        UUID userId,
        UUID clientOrderId
    )
    {
        return jdbcClient.sql("""
                SELECT %s
                FROM paper_trades
                WHERE user_id = :userId
                  AND client_order_id = :clientOrderId
                """.formatted(TRADE_COLUMNS))
            .param("userId", userId)
            .param("clientOrderId", clientOrderId)
            .query(JdbcPaperTradingRepository::mapTrade)
            .optional();
    }

    public Optional<PaperTrade> lockTrade(UUID tradeId, UUID userId)
    {
        return tradeQuery("""
            SELECT %s
            FROM paper_trades
            WHERE id = :tradeId AND user_id = :userId
            FOR UPDATE
            """, tradeId, userId);
    }

    public void insert(PaperTrade trade)
    {
        MapSqlParameterSource parameters = tradeParameters(trade);
        int inserted = namedJdbcTemplate.update("""
            INSERT INTO paper_trades
            (
                id, user_id, client_order_id, symbol, order_type,
                margin_mode, side, status,
                leverage, margin_usd, notional_usd, quantity, entry_price,
                exit_price, stop_loss, take_profit, risk_control_version,
                realized_pnl, close_reason, opened_at, closed_at
            )
            VALUES
            (
                :id, :userId, :clientOrderId, :symbol, :orderType,
                :marginMode, :side, :status,
                :leverage, :marginUsd, :notionalUsd, :quantity, :entryPrice,
                :exitPrice, :stopLoss, :takeProfit, :riskControlVersion,
                :realizedPnl, :closeReason, :openedAt, :closedAt
            )
            """, parameters);
        requireOneRow(inserted, "Paper trade was not created");
        insertRiskControlRevision(
            trade.id(),
            trade.riskControlVersion(),
            trade.stopLoss(),
            trade.takeProfit(),
            trade.openedAt()
        );
    }

    public void updateRiskControls(
        PaperTrade trade,
        BigDecimal stopLoss,
        BigDecimal takeProfit,
        Instant effectiveAt
    )
    {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("tradeId", trade.id())
            .addValue("stopLoss", stopLoss)
            .addValue("takeProfit", takeProfit);
        int updated = namedJdbcTemplate.update("""
            UPDATE paper_trades
            SET stop_loss = :stopLoss,
                take_profit = :takeProfit,
                risk_control_version = risk_control_version + 1
            WHERE id = :tradeId AND status = 'OPEN'
            """, parameters);
        requireOneRow(updated, "Open paper trade risk controls were not updated");
        insertRiskControlRevision(
            trade.id(),
            trade.riskControlVersion() + 1,
            stopLoss,
            takeProfit,
            effectiveAt
        );
    }

    public void close(
        UUID tradeId,
        BigDecimal exitPrice,
        BigDecimal realizedPnl,
        TradeCloseReason closeReason,
        Instant closedAt
    )
    {
        int updated = jdbcClient.sql("""
                UPDATE paper_trades
                SET status = 'CLOSED',
                    exit_price = :exitPrice,
                    realized_pnl = :realizedPnl,
                    close_reason = :closeReason,
                    closed_at = :closedAt
                WHERE id = :tradeId AND status = 'OPEN'
                """)
            .param("exitPrice", exitPrice)
            .param("realizedPnl", realizedPnl)
            .param("closeReason", closeReason.name())
            .param("closedAt", Timestamp.from(closedAt))
            .param("tradeId", tradeId)
            .update();
        requireOneRow(updated, "Open paper trade was not closed");
    }

    public List<TriggeredPaperTrade> findTriggeredTrades(
        BigDecimal observedPrice,
        Instant observedAt
    )
    {
        return jdbcClient.sql("""
                SELECT trade.id,
                       trade.user_id,
                       controls.revision AS risk_control_version,
                       CASE
                           WHEN
                               (
                                   trade.side = 'LONG'
                                   AND controls.stop_loss IS NOT NULL
                                   AND :price <= controls.stop_loss
                               )
                               OR
                               (
                                   trade.side = 'SHORT'
                                   AND controls.stop_loss IS NOT NULL
                                   AND :price >= controls.stop_loss
                               )
                           THEN 'STOP_LOSS'
                           ELSE 'TAKE_PROFIT'
                       END AS trigger_reason
                FROM paper_trades AS trade
                JOIN LATERAL
                (
                    SELECT revision, stop_loss, take_profit
                    FROM paper_trade_risk_controls AS history
                    WHERE history.trade_id = trade.id
                      AND history.effective_at <= :observedAt
                    ORDER BY history.effective_at DESC, revision DESC
                    LIMIT 1
                ) AS controls ON TRUE
                WHERE trade.status = 'OPEN'
                  AND trade.opened_at <= :observedAt
                  AND
                  (
                      (
                          trade.side = 'LONG'
                          AND
                          (
                              (
                                  controls.stop_loss IS NOT NULL
                                  AND :price <= controls.stop_loss
                              )
                              OR
                              (
                                  controls.take_profit IS NOT NULL
                                  AND :price >= controls.take_profit
                              )
                          )
                      )
                      OR
                      (
                          trade.side = 'SHORT'
                          AND
                          (
                              (
                                  controls.stop_loss IS NOT NULL
                                  AND :price >= controls.stop_loss
                              )
                              OR
                              (
                                  controls.take_profit IS NOT NULL
                                  AND :price <= controls.take_profit
                              )
                          )
                      )
                  )
                ORDER BY trade.user_id, trade.opened_at, trade.id
                """)
            .param("price", observedPrice)
            .param("observedAt", Timestamp.from(observedAt))
            .query((resultSet, rowNumber) -> new TriggeredPaperTrade(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("user_id", UUID.class),
                TradeCloseReason.valueOf(
                    resultSet.getString("trigger_reason")
                ),
                resultSet.getLong("risk_control_version")
            ))
            .list();
    }

    private void insertRiskControlRevision(
        UUID tradeId,
        long revision,
        BigDecimal stopLoss,
        BigDecimal takeProfit,
        Instant effectiveAt
    )
    {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("tradeId", tradeId)
            .addValue("revision", revision)
            .addValue("stopLoss", stopLoss)
            .addValue("takeProfit", takeProfit)
            .addValue("effectiveAt", Timestamp.from(effectiveAt));
        int inserted = namedJdbcTemplate.update("""
            INSERT INTO paper_trade_risk_controls
                (trade_id, revision, stop_loss, take_profit, effective_at)
            VALUES
                (:tradeId, :revision, :stopLoss, :takeProfit, :effectiveAt)
            """, parameters);
        requireOneRow(
            inserted,
            "Paper-trade risk-control revision was not created"
        );
    }

    private PaperTradingAccount accountQuery(boolean lock, UUID userId)
    {
        String lockClause = lock ? " FOR UPDATE" : "";
        return jdbcClient.sql("""
                SELECT user_id, balance_usd
                FROM paper_trading_accounts
                WHERE user_id = :userId
                """.stripTrailing() + lockClause)
            .param("userId", userId)
            .query((resultSet, rowNumber) -> new PaperTradingAccount(
                resultSet.getObject("user_id", UUID.class),
                resultSet.getBigDecimal("balance_usd")
            ))
            .single();
    }

    private Optional<PaperTrade> tradeQuery(
        String sql,
        UUID tradeId,
        UUID userId
    )
    {
        JdbcClient.StatementSpec statement = jdbcClient
            .sql(sql.formatted(TRADE_COLUMNS))
            .param("tradeId", tradeId);
        if (userId != null)
        {
            statement = statement.param("userId", userId);
        }
        return statement
            .query(JdbcPaperTradingRepository::mapTrade)
            .optional();
    }

    private static PaperTrade mapTrade(
        ResultSet resultSet,
        int rowNumber
    ) throws SQLException
    {
        Timestamp closedAt = resultSet.getTimestamp("closed_at");
        String closeReason = resultSet.getString("close_reason");
        return new PaperTrade(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("user_id", UUID.class),
            resultSet.getObject("client_order_id", UUID.class),
            resultSet.getString("symbol"),
            resultSet.getString("order_type"),
            resultSet.getString("margin_mode"),
            TradeSide.valueOf(resultSet.getString("side")),
            TradeStatus.valueOf(resultSet.getString("status")),
            resultSet.getInt("leverage"),
            resultSet.getBigDecimal("margin_usd"),
            resultSet.getBigDecimal("notional_usd"),
            resultSet.getBigDecimal("quantity"),
            resultSet.getBigDecimal("entry_price"),
            resultSet.getBigDecimal("exit_price"),
            resultSet.getBigDecimal("stop_loss"),
            resultSet.getBigDecimal("take_profit"),
            resultSet.getLong("risk_control_version"),
            resultSet.getBigDecimal("realized_pnl"),
            closeReason == null ? null : TradeCloseReason.valueOf(closeReason),
            resultSet.getTimestamp("opened_at").toInstant(),
            closedAt == null ? null : closedAt.toInstant()
        );
    }

    private static MapSqlParameterSource tradeParameters(PaperTrade trade)
    {
        return new MapSqlParameterSource(Map.ofEntries(
            Map.entry("id", trade.id()),
            Map.entry("userId", trade.userId()),
            Map.entry("clientOrderId", trade.clientOrderId()),
            Map.entry("symbol", trade.symbol()),
            Map.entry("orderType", trade.orderType()),
            Map.entry("marginMode", trade.marginMode()),
            Map.entry("side", trade.side().name()),
            Map.entry("status", trade.status().name()),
            Map.entry("leverage", trade.leverage()),
            Map.entry("marginUsd", trade.marginUsd()),
            Map.entry("notionalUsd", trade.notionalUsd()),
            Map.entry("quantity", trade.quantity()),
            Map.entry("entryPrice", trade.entryPrice()),
            Map.entry("riskControlVersion", trade.riskControlVersion()),
            Map.entry("openedAt", Timestamp.from(trade.openedAt()))
        ))
            .addValue("exitPrice", trade.exitPrice())
            .addValue("stopLoss", trade.stopLoss())
            .addValue("takeProfit", trade.takeProfit())
            .addValue("realizedPnl", trade.realizedPnl())
            .addValue(
                "closeReason",
                trade.closeReason() == null ? null : trade.closeReason().name()
            )
            .addValue(
                "closedAt",
                trade.closedAt() == null
                    ? null
                    : Timestamp.from(trade.closedAt())
            );
    }

    private static void requireOneRow(int affectedRows, String message)
    {
        if (affectedRows != 1)
        {
            throw new IllegalStateException(message);
        }
    }
}
