ALTER TABLE market_candles
    ADD COLUMN product_type VARCHAR(32) NOT NULL DEFAULT 'SPOT';

ALTER TABLE market_candles
    DROP CONSTRAINT market_candles_pkey;

ALTER TABLE market_candles
    ADD CONSTRAINT market_candles_product_type_valid
        CHECK (product_type IN ('SPOT', 'USD_M_PERPETUAL')),
    ADD CONSTRAINT market_candles_pkey
        PRIMARY KEY (product_type, symbol, candle_interval, open_time);

ALTER TABLE market_candles
    ALTER COLUMN product_type DROP DEFAULT;
