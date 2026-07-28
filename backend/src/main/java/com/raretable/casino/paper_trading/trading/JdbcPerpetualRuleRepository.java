package com.raretable.casino.paper_trading.trading;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPerpetualRuleRepository
{
    private final JdbcClient jdbcClient;

    JdbcPerpetualRuleRepository(JdbcClient jdbcClient)
    {
        this.jdbcClient = jdbcClient;
    }

    public PerpetualRuleVersion findActive(String symbol, Instant at)
    {
        RuleRow rule = jdbcClient.sql("""
                SELECT id, code, symbol, product_type, effective_from,
                       taker_fee_rate, liquidation_fee_rate,
                       liquidation_mode, negative_balance_policy,
                       stop_trigger_price_type
                FROM paper_perpetual_rule_versions
                WHERE symbol = :symbol
                  AND effective_from <= :at
                  AND (effective_until IS NULL OR effective_until > :at)
                """)
            .param("symbol", symbol)
            .param("at", Timestamp.from(at))
            .query((resultSet, rowNumber) -> new RuleRow(
                resultSet.getLong("id"),
                resultSet.getString("code"),
                resultSet.getString("symbol"),
                resultSet.getString("product_type"),
                resultSet.getTimestamp("effective_from").toInstant(),
                resultSet.getBigDecimal("taker_fee_rate"),
                resultSet.getBigDecimal("liquidation_fee_rate"),
                resultSet.getString("liquidation_mode"),
                resultSet.getString("negative_balance_policy"),
                resultSet.getString("stop_trigger_price_type")
            ))
            .single();
        List<MaintenanceMarginTier> tiers = jdbcClient.sql("""
                SELECT tier, notional_floor, notional_cap, max_leverage,
                       maintenance_margin_rate, maintenance_amount_usd
                FROM paper_maintenance_margin_tiers
                WHERE rule_version_id = :ruleVersionId
                ORDER BY tier
                """)
            .param("ruleVersionId", rule.id())
            .query((resultSet, rowNumber) -> new MaintenanceMarginTier(
                resultSet.getInt("tier"),
                resultSet.getBigDecimal("notional_floor"),
                resultSet.getBigDecimal("notional_cap"),
                resultSet.getInt("max_leverage"),
                resultSet.getBigDecimal("maintenance_margin_rate"),
                resultSet.getBigDecimal("maintenance_amount_usd")
            ))
            .list();
        return new PerpetualRuleVersion(
            rule.id(),
            rule.code(),
            rule.symbol(),
            rule.productType(),
            rule.effectiveFrom(),
            rule.takerFeeRate(),
            rule.liquidationFeeRate(),
            rule.liquidationMode(),
            rule.negativeBalancePolicy(),
            rule.stopTriggerPriceType(),
            tiers
        );
    }

    private record RuleRow(
        long id,
        String code,
        String symbol,
        String productType,
        Instant effectiveFrom,
        java.math.BigDecimal takerFeeRate,
        java.math.BigDecimal liquidationFeeRate,
        String liquidationMode,
        String negativeBalancePolicy,
        String stopTriggerPriceType
    )
    {
    }
}
