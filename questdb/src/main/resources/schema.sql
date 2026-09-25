CREATE TABLE IF NOT EXISTS market_quotes (
    symbol SYMBOL CAPACITY 128 NOCACHE INDEX,
    bid DOUBLE,
    ask DOUBLE,
    last_price DOUBLE,
    volume LONG,
    timestamp TIMESTAMP
) TIMESTAMP(timestamp) PARTITION BY DAY BYPASS WAL;

CREATE TABLE IF NOT EXISTS trade_executions (
    trade_id VARCHAR,
    symbol SYMBOL CAPACITY 128 NOCACHE INDEX,
    price DOUBLE,
    quantity LONG,
    side VARCHAR,
    timestamp TIMESTAMP
) TIMESTAMP(timestamp) PARTITION BY DAY BYPASS WAL;
