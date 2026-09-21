CREATE KEYSPACE IF NOT EXISTS reactive_trading_store
WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };

CREATE TABLE IF NOT EXISTS reactive_trading_store.market_ticks (
    ticker text,
    bucket_hour text,
    tick_timestamp timestamp,
    tick_id text,
    bid decimal,
    ask decimal,
    last_price decimal,
    volume decimal,
    PRIMARY KEY ((ticker, bucket_hour), tick_timestamp, tick_id)
) WITH CLUSTERING ORDER BY (tick_timestamp DESC, tick_id ASC);

CREATE TABLE IF NOT EXISTS reactive_trading_store.hot_market_quotes (
    ticker text PRIMARY KEY,
    bid decimal,
    ask decimal,
    last_price decimal,
    updated_at timestamp
);
