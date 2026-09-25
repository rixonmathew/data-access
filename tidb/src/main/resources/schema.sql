CREATE TABLE IF NOT EXISTS orders (
    order_id VARCHAR(64) NOT NULL PRIMARY KEY,
    account_id VARCHAR(64) NOT NULL,
    symbol VARCHAR(16) NOT NULL,
    side VARCHAR(8) NOT NULL,
    order_type VARCHAR(16) NOT NULL,
    price DECIMAL(18, 4) NOT NULL,
    quantity BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_account_symbol (account_id, symbol),
    KEY idx_symbol_status (symbol, status)
);
