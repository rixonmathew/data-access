CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;

CREATE TABLE IF NOT EXISTS market_ticks (
    time TIMESTAMPTZ NOT NULL,
    symbol TEXT NOT NULL,
    price DOUBLE PRECISION NOT NULL,
    volume BIGINT NOT NULL
);

SELECT create_hypertable('market_ticks', 'time', if_not_exists => TRUE);

CREATE MATERIALIZED VIEW IF NOT EXISTS ohlcv_1m
WITH (timescaledb.continuous) AS
SELECT time_bucket('1 minute', time) AS bucket,
       symbol,
       first(price, time) as open,
       max(price) as high,
       min(price) as low,
       last(price, time) as close,
       sum(volume) as volume
FROM market_ticks
GROUP BY bucket, symbol
WITH NO DATA;
