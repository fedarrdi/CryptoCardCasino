CREATE TABLE paper_perpetual_rule_versions
(
    id BIGINT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    symbol VARCHAR(20) NOT NULL,
    product_type VARCHAR(30) NOT NULL,
    effective_from TIMESTAMP WITH TIME ZONE NOT NULL,
    effective_until TIMESTAMP WITH TIME ZONE,
    taker_fee_rate NUMERIC(18, 12) NOT NULL,
    liquidation_fee_rate NUMERIC(18, 12) NOT NULL,
    liquidation_mode VARCHAR(10) NOT NULL,
    negative_balance_policy VARCHAR(20) NOT NULL,
    stop_trigger_price_type VARCHAR(10) NOT NULL,
    CONSTRAINT paper_perpetual_rules_symbol
        CHECK (symbol = 'BTCUSDT'),
    CONSTRAINT paper_perpetual_rules_product
        CHECK (product_type = 'USD_M_PERPETUAL'),
    CONSTRAINT paper_perpetual_rules_dates
        CHECK (
            effective_until IS NULL
            OR effective_until > effective_from
        ),
    CONSTRAINT paper_perpetual_rules_taker_fee
        CHECK (taker_fee_rate >= 0 AND taker_fee_rate < 1),
    CONSTRAINT paper_perpetual_rules_liquidation_fee
        CHECK (liquidation_fee_rate >= 0 AND liquidation_fee_rate < 1),
    CONSTRAINT paper_perpetual_rules_liquidation_mode
        CHECK (liquidation_mode = 'FULL'),
    CONSTRAINT paper_perpetual_rules_negative_balance
        CHECK (negative_balance_policy = 'FLOOR_ZERO'),
    CONSTRAINT paper_perpetual_rules_trigger_price
        CHECK (stop_trigger_price_type = 'LAST')
);

CREATE UNIQUE INDEX paper_perpetual_active_rule_index
    ON paper_perpetual_rule_versions (symbol)
    WHERE effective_until IS NULL;

CREATE TABLE paper_maintenance_margin_tiers
(
    rule_version_id BIGINT NOT NULL
        REFERENCES paper_perpetual_rule_versions (id),
    tier SMALLINT NOT NULL,
    notional_floor NUMERIC(38, 8) NOT NULL,
    notional_cap NUMERIC(38, 8),
    max_leverage SMALLINT NOT NULL,
    maintenance_margin_rate NUMERIC(18, 12) NOT NULL,
    maintenance_amount_usd NUMERIC(38, 8) NOT NULL,
    PRIMARY KEY (rule_version_id, tier),
    CONSTRAINT paper_maintenance_tier_positive
        CHECK (tier > 0),
    CONSTRAINT paper_maintenance_floor_nonnegative
        CHECK (notional_floor >= 0),
    CONSTRAINT paper_maintenance_cap
        CHECK (
            notional_cap IS NULL
            OR notional_cap > notional_floor
        ),
    CONSTRAINT paper_maintenance_leverage
        CHECK (max_leverage BETWEEN 1 AND 100),
    CONSTRAINT paper_maintenance_rate
        CHECK (
            maintenance_margin_rate > 0
            AND maintenance_margin_rate < 1
        ),
    CONSTRAINT paper_maintenance_amount_nonnegative
        CHECK (maintenance_amount_usd >= 0)
);

-- RARETABLE_BTCUSDT_V1 is an explicit paper-product policy. Binance's
-- account-specific commission and leverage-bracket endpoints are signed
-- USER_DATA endpoints, so this version is intentionally immutable rather
-- than being presented as a user's live Binance account schedule.
INSERT INTO paper_perpetual_rule_versions
(
    id,
    code,
    symbol,
    product_type,
    effective_from,
    taker_fee_rate,
    liquidation_fee_rate,
    liquidation_mode,
    negative_balance_policy,
    stop_trigger_price_type
)
VALUES
(
    1,
    'RARETABLE_BTCUSDT_V1',
    'BTCUSDT',
    'USD_M_PERPETUAL',
    '2026-01-01T00:00:00Z',
    0.000400000000,
    0.012500000000,
    'FULL',
    'FLOOR_ZERO',
    'LAST'
);

INSERT INTO paper_maintenance_margin_tiers
(
    rule_version_id,
    tier,
    notional_floor,
    notional_cap,
    max_leverage,
    maintenance_margin_rate,
    maintenance_amount_usd
)
VALUES
    (1, 1, 0.00000000, 500000.00000000, 100, 0.004000000000, 0.00000000),
    (1, 2, 500000.00000000, 5000000.00000000, 50, 0.005000000000, 500.00000000),
    (1, 3, 5000000.00000000, 25000000.00000000, 20, 0.010000000000, 25500.00000000),
    (1, 4, 25000000.00000000, NULL, 10, 0.025000000000, 400500.00000000);

ALTER TABLE paper_trades
    ADD COLUMN rule_version_id BIGINT
        REFERENCES paper_perpetual_rule_versions (id),
    ADD COLUMN funding_eligible_from TIMESTAMP WITH TIME ZONE,
    ADD COLUMN entry_fee_rate NUMERIC(18, 12) NOT NULL DEFAULT 0,
    ADD COLUMN exit_fee_rate NUMERIC(18, 12),
    ADD COLUMN entry_fee NUMERIC(38, 8) NOT NULL DEFAULT 0,
    ADD COLUMN exit_fee NUMERIC(38, 8) NOT NULL DEFAULT 0,
    ADD COLUMN liquidation_fee NUMERIC(38, 8) NOT NULL DEFAULT 0,
    ADD COLUMN funding_pnl NUMERIC(38, 8) NOT NULL DEFAULT 0,
    ADD COLUMN gross_realized_pnl NUMERIC(38, 8);

UPDATE paper_trades
SET rule_version_id = 1,
    funding_eligible_from = CASE
        WHEN status = 'OPEN' THEN CURRENT_TIMESTAMP
        ELSE NULL
    END,
    entry_fee_rate = CASE
        WHEN status = 'OPEN' THEN 0.000400000000
        ELSE 0
    END,
    exit_fee_rate = CASE
        WHEN status = 'CLOSED' THEN 0
        ELSE NULL
    END,
    gross_realized_pnl = CASE
        WHEN status = 'CLOSED' THEN realized_pnl
        ELSE NULL
    END;

ALTER TABLE paper_trades
    ALTER COLUMN rule_version_id SET NOT NULL,
    ALTER COLUMN entry_fee_rate DROP DEFAULT,
    ALTER COLUMN entry_fee DROP DEFAULT,
    ALTER COLUMN exit_fee DROP DEFAULT,
    ALTER COLUMN liquidation_fee DROP DEFAULT,
    ALTER COLUMN funding_pnl DROP DEFAULT,
    DROP CONSTRAINT paper_trades_close_reason,
    ADD CONSTRAINT paper_trades_close_reason
        CHECK (
            close_reason IS NULL
            OR close_reason IN (
                'USER',
                'STOP_LOSS',
                'TAKE_PROFIT',
                'LIQUIDATION'
            )
        ),
    ADD CONSTRAINT paper_trades_entry_fee_rate
        CHECK (entry_fee_rate >= 0 AND entry_fee_rate < 1),
    ADD CONSTRAINT paper_trades_exit_fee_rate
        CHECK (
            exit_fee_rate IS NULL
            OR (exit_fee_rate >= 0 AND exit_fee_rate < 1)
        ),
    ADD CONSTRAINT paper_trades_entry_fee_nonnegative
        CHECK (entry_fee >= 0),
    ADD CONSTRAINT paper_trades_exit_fee_nonnegative
        CHECK (exit_fee >= 0),
    ADD CONSTRAINT paper_trades_liquidation_fee_nonnegative
        CHECK (liquidation_fee >= 0),
    ADD CONSTRAINT paper_trades_gross_realized_state
        CHECK (
            (status = 'OPEN' AND gross_realized_pnl IS NULL)
            OR
            (status = 'CLOSED' AND gross_realized_pnl IS NOT NULL)
        ),
    ADD CONSTRAINT paper_trades_fee_state
        CHECK (
            (
                status = 'OPEN'
                AND exit_fee_rate IS NULL
                AND exit_fee = 0
                AND liquidation_fee = 0
            )
            OR
            (
                status = 'CLOSED'
                AND exit_fee_rate IS NOT NULL
                AND (
                    (
                        close_reason = 'LIQUIDATION'
                        AND liquidation_fee >= 0
                    )
                    OR
                    (
                        close_reason <> 'LIQUIDATION'
                        AND liquidation_fee = 0
                    )
                )
            )
        );

CREATE TABLE paper_account_ledger
(
    sequence BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL
        REFERENCES paper_trading_accounts (user_id) ON DELETE CASCADE,
    trade_id UUID
        REFERENCES paper_trades (id) ON DELETE SET NULL,
    event_type VARCHAR(30) NOT NULL,
    amount_usd NUMERIC(38, 8) NOT NULL,
    balance_after_usd NUMERIC(38, 8) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    CONSTRAINT paper_account_ledger_event_type
        CHECK (
            event_type IN (
                'ACCOUNT_OPENED',
                'MIGRATION_BALANCE',
                'ENTRY_FEE',
                'FUNDING',
                'REALIZED_PNL',
                'EXIT_FEE',
                'LIQUIDATION_FEE',
                'INSURANCE_CREDIT'
            )
        )
);

CREATE INDEX paper_account_ledger_user_sequence_index
    ON paper_account_ledger (user_id, sequence);

INSERT INTO paper_account_ledger
(
    id,
    user_id,
    trade_id,
    event_type,
    amount_usd,
    balance_after_usd,
    occurred_at,
    idempotency_key
)
SELECT
    user_id,
    user_id,
    NULL,
    'MIGRATION_BALANCE',
    balance_usd,
    balance_usd,
    updated_at,
    'migration:v5:' || user_id::text
FROM paper_trading_accounts;

INSERT INTO paper_account_ledger
(
    id,
    user_id,
    trade_id,
    event_type,
    amount_usd,
    balance_after_usd,
    occurred_at,
    idempotency_key
)
SELECT
    md5('migration:v5:insurance:' || user_id::text)::uuid,
    user_id,
    NULL,
    'INSURANCE_CREDIT',
    -balance_usd,
    0.00000000,
    CURRENT_TIMESTAMP,
    'migration:v5:insurance:' || user_id::text
FROM paper_trading_accounts
WHERE balance_usd < 0;

UPDATE paper_trading_accounts
SET balance_usd = 0.00000000,
    updated_at = CURRENT_TIMESTAMP
WHERE balance_usd < 0;

CREATE OR REPLACE FUNCTION create_paper_trading_account_for_user()
RETURNS TRIGGER
LANGUAGE plpgsql
AS
$$
BEGIN
    INSERT INTO paper_trading_accounts (user_id)
    VALUES (NEW.id);

    INSERT INTO paper_account_ledger
    (
        id,
        user_id,
        trade_id,
        event_type,
        amount_usd,
        balance_after_usd,
        occurred_at,
        idempotency_key
    )
    VALUES
    (
        NEW.id,
        NEW.id,
        NULL,
        'ACCOUNT_OPENED',
        10000.00000000,
        10000.00000000,
        NEW.created_at,
        'account-opened:' || NEW.id::text
    );
    RETURN NEW;
END;
$$;

CREATE TABLE paper_funding_events
(
    id UUID PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    funding_time TIMESTAMP WITH TIME ZONE NOT NULL,
    rate_type VARCHAR(30) NOT NULL,
    funding_rate NUMERIC(18, 12) NOT NULL,
    mark_price NUMERIC(38, 12) NOT NULL,
    ingested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (symbol, funding_time, rate_type),
    CONSTRAINT paper_funding_symbol CHECK (symbol = 'BTCUSDT'),
    CONSTRAINT paper_funding_mark_positive CHECK (mark_price > 0)
);

CREATE TABLE paper_trade_funding_settlements
(
    funding_event_id UUID NOT NULL
        REFERENCES paper_funding_events (id) ON DELETE CASCADE,
    trade_id UUID NOT NULL
        REFERENCES paper_trades (id) ON DELETE CASCADE,
    user_id UUID NOT NULL
        REFERENCES paper_trading_accounts (user_id) ON DELETE CASCADE,
    quantity NUMERIC(38, 12) NOT NULL,
    notional_usd NUMERIC(38, 8) NOT NULL,
    amount_usd NUMERIC(38, 8) NOT NULL,
    settled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (funding_event_id, trade_id),
    CONSTRAINT paper_funding_settlement_quantity CHECK (quantity > 0),
    CONSTRAINT paper_funding_settlement_notional CHECK (notional_usd > 0)
);

CREATE INDEX paper_funding_settlement_user_index
    ON paper_trade_funding_settlements (user_id, settled_at);

CREATE TABLE paper_liquidation_events
(
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL
        REFERENCES paper_trading_accounts (user_id) ON DELETE CASCADE,
    rule_version_id BIGINT NOT NULL
        REFERENCES paper_perpetual_rule_versions (id),
    mark_price NUMERIC(38, 12) NOT NULL,
    index_price NUMERIC(38, 12) NOT NULL,
    last_price NUMERIC(38, 12) NOT NULL,
    equity_before NUMERIC(38, 8) NOT NULL,
    maintenance_margin_before NUMERIC(38, 8) NOT NULL,
    wallet_after_closes NUMERIC(38, 8) NOT NULL,
    insurance_credit NUMERIC(38, 8) NOT NULL,
    triggered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT paper_liquidation_prices
        CHECK (mark_price > 0 AND index_price > 0 AND last_price > 0),
    CONSTRAINT paper_liquidation_maintenance
        CHECK (maintenance_margin_before >= 0),
    CONSTRAINT paper_liquidation_insurance
        CHECK (insurance_credit >= 0),
    CONSTRAINT paper_liquidation_time_order
        CHECK (completed_at >= triggered_at)
);

CREATE INDEX paper_liquidation_user_time_index
    ON paper_liquidation_events (user_id, triggered_at DESC);
