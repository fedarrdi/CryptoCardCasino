CREATE TABLE paper_trading_accounts
(
    user_id UUID PRIMARY KEY
        REFERENCES users (id) ON DELETE CASCADE,
    balance_usd NUMERIC(38, 8) NOT NULL DEFAULT 10000.00000000,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO paper_trading_accounts (user_id)
SELECT id
FROM users;

CREATE FUNCTION create_paper_trading_account_for_user()
RETURNS TRIGGER
LANGUAGE plpgsql
AS
$$
BEGIN
    INSERT INTO paper_trading_accounts (user_id)
    VALUES (NEW.id);
    RETURN NEW;
END;
$$;

CREATE TRIGGER users_create_paper_trading_account
AFTER INSERT ON users
FOR EACH ROW
EXECUTE FUNCTION create_paper_trading_account_for_user();

CREATE TABLE paper_trades
(
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL
        REFERENCES paper_trading_accounts (user_id) ON DELETE CASCADE,
    client_order_id UUID NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    order_type VARCHAR(10) NOT NULL,
    margin_mode VARCHAR(10) NOT NULL,
    side VARCHAR(5) NOT NULL,
    status VARCHAR(6) NOT NULL,
    leverage SMALLINT NOT NULL,
    margin_usd NUMERIC(38, 8) NOT NULL,
    notional_usd NUMERIC(38, 8) NOT NULL,
    quantity NUMERIC(38, 12) NOT NULL,
    entry_price NUMERIC(38, 12) NOT NULL,
    exit_price NUMERIC(38, 12),
    stop_loss NUMERIC(38, 12),
    take_profit NUMERIC(38, 12),
    risk_control_version BIGINT NOT NULL DEFAULT 0,
    realized_pnl NUMERIC(38, 8),
    close_reason VARCHAR(20),
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT paper_trades_symbol CHECK (symbol = 'BTCUSDT'),
    CONSTRAINT paper_trades_order_type CHECK (order_type = 'MARKET'),
    CONSTRAINT paper_trades_margin_mode CHECK (margin_mode = 'CROSS'),
    CONSTRAINT paper_trades_side CHECK (side IN ('LONG', 'SHORT')),
    CONSTRAINT paper_trades_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT paper_trades_leverage CHECK (leverage BETWEEN 1 AND 100),
    CONSTRAINT paper_trades_margin_positive CHECK (margin_usd > 0),
    CONSTRAINT paper_trades_notional_positive CHECK (notional_usd > 0),
    CONSTRAINT paper_trades_notional_matches_margin
        CHECK (notional_usd = margin_usd * leverage),
    CONSTRAINT paper_trades_quantity_positive CHECK (quantity > 0),
    CONSTRAINT paper_trades_entry_price_positive CHECK (entry_price > 0),
    CONSTRAINT paper_trades_exit_price_positive
        CHECK (exit_price IS NULL OR exit_price > 0),
    CONSTRAINT paper_trades_stop_loss_positive
        CHECK (stop_loss IS NULL OR stop_loss > 0),
    CONSTRAINT paper_trades_take_profit_positive
        CHECK (take_profit IS NULL OR take_profit > 0),
    CONSTRAINT paper_trades_risk_control_version
        CHECK (risk_control_version >= 0),
    CONSTRAINT paper_trades_close_reason
        CHECK (
            close_reason IS NULL
            OR close_reason IN ('USER', 'STOP_LOSS', 'TAKE_PROFIT')
        ),
    CONSTRAINT paper_trades_state
        CHECK (
            (
                status = 'OPEN'
                AND exit_price IS NULL
                AND realized_pnl IS NULL
                AND close_reason IS NULL
                AND closed_at IS NULL
            )
            OR
            (
                status = 'CLOSED'
                AND exit_price IS NOT NULL
                AND realized_pnl IS NOT NULL
                AND close_reason IS NOT NULL
                AND closed_at IS NOT NULL
            )
        )
);

CREATE UNIQUE INDEX paper_trades_user_client_order_index
    ON paper_trades (user_id, client_order_id);

CREATE INDEX paper_trades_user_status_opened_index
    ON paper_trades (user_id, status, opened_at DESC, id);

CREATE INDEX paper_trades_user_closed_history_index
    ON paper_trades (user_id, closed_at DESC, id)
    WHERE status = 'CLOSED';

CREATE INDEX paper_trades_open_risk_controls_index
    ON paper_trades (side, stop_loss, take_profit)
    WHERE status = 'OPEN'
      AND (stop_loss IS NOT NULL OR take_profit IS NOT NULL);

CREATE TABLE paper_trade_risk_controls
(
    trade_id UUID NOT NULL
        REFERENCES paper_trades (id) ON DELETE CASCADE,
    revision BIGINT NOT NULL,
    stop_loss NUMERIC(38, 12),
    take_profit NUMERIC(38, 12),
    effective_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (trade_id, revision),
    CONSTRAINT paper_trade_risk_revision_nonnegative
        CHECK (revision >= 0),
    CONSTRAINT paper_trade_risk_stop_loss_positive
        CHECK (stop_loss IS NULL OR stop_loss > 0),
    CONSTRAINT paper_trade_risk_take_profit_positive
        CHECK (take_profit IS NULL OR take_profit > 0)
);

CREATE INDEX paper_trade_risk_controls_effective_index
    ON paper_trade_risk_controls
        (trade_id, effective_at DESC, revision DESC);
