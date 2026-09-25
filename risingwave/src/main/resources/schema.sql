CREATE TABLE IF NOT EXISTS market_trades (
    trade_id VARCHAR PRIMARY KEY,
    trader_id VARCHAR NOT NULL,
    symbol VARCHAR NOT NULL,
    price DOUBLE PRECISION NOT NULL,
    quantity BIGINT NOT NULL,
    side VARCHAR NOT NULL,
    trade_time TIMESTAMPTZ NOT NULL
);

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_realtime_vwap AS
SELECT symbol,
       count(*) as trade_count,
       sum(quantity) as total_volume,
       sum(price * quantity) / sum(quantity) as vwap
FROM market_trades
GROUP BY symbol;

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_wash_trading_alerts AS
SELECT b.trader_id,
       b.symbol,
       b.trade_id as buy_trade_id,
       s.trade_id as sell_trade_id,
       b.price as buy_price,
       s.price as sell_price,
       b.quantity as volume,
       b.trade_time as buy_time,
       s.trade_time as sell_time
FROM market_trades b
JOIN market_trades s
  ON b.trader_id = s.trader_id
 AND b.symbol = s.symbol
WHERE b.side = 'BUY'
  AND s.side = 'SELL'
  AND s.trade_time >= b.trade_time
  AND s.trade_time <= b.trade_time + INTERVAL '30 SECONDS';
