CREATE TABLE market_candles
(
    symbol VARCHAR(20) NOT NULL,
    candle_interval VARCHAR(8) NOT NULL,
    open_time TIMESTAMP WITH TIME ZONE NOT NULL,
    open NUMERIC(30, 12) NOT NULL,
    high NUMERIC(30, 12) NOT NULL,
    low NUMERIC(30, 12) NOT NULL,
    close NUMERIC(30, 12) NOT NULL,
    volume NUMERIC(38, 12) NOT NULL,
    PRIMARY KEY (symbol, candle_interval, open_time),
    CONSTRAINT market_candles_open_positive CHECK (open > 0),
    CONSTRAINT market_candles_high_positive CHECK (high > 0),
    CONSTRAINT market_candles_low_positive CHECK (low > 0),
    CONSTRAINT market_candles_close_positive CHECK (close > 0),
    CONSTRAINT market_candles_volume_non_negative CHECK (volume >= 0),
    CONSTRAINT market_candles_price_range
        CHECK (high >= GREATEST(open, close) AND low <= LEAST(open, close))
);
