CREATE KEYSPACE IF NOT EXISTS trading_store
WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };

CREATE TABLE IF NOT EXISTS trading_store.trade_executions (
    ticker text,
    bucket_date date,
    execution_time timestamp,
    execution_id text,
    order_id text,
    account_number text,
    price decimal,
    quantity decimal,
    liquidity_indicator text,
    venue text,
    PRIMARY KEY ((ticker, bucket_date), execution_time, execution_id)
) WITH CLUSTERING ORDER BY (execution_time DESC, execution_id ASC);

CREATE TABLE IF NOT EXISTS trading_store.orders_paxos (
    order_id text PRIMARY KEY,
    account_number text,
    ticker text,
    side text,
    quantity decimal,
    price decimal,
    status text,
    version bigint
);
