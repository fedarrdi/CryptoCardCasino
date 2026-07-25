CREATE TABLE users
(
    id UUID PRIMARY KEY,
    wallet_address VARCHAR(42) NOT NULL,
    name VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT users_wallet_address_format
        CHECK (wallet_address ~ '^0x[0-9a-fA-F]{40}$')
);

CREATE UNIQUE INDEX users_wallet_address_unique
    ON users (LOWER(wallet_address));
