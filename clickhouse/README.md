# ClickHouse Columnar OLAP Module (`clickhouse`)

## Overview
The `clickhouse` module evaluates **ClickHouse**, an open-source columnar database management system engineered for real-time analytical reporting (OLAP) at petabyte scale.

In this module, we model a **High-Frequency Market Tick Ingestion & Candlestick Aggregation Engine** capable of ingesting high volumes of trade events and computing statistical execution metrics on the fly.

---

## Technical Capabilities Tested & Validated

### 1. Vectorized Batch Ingestion into `MergeTree`
- **Engine**: `MergeTree` partitioned by month (`PARTITION BY toYYYYMM(event_time)`) and sorted by ticker and timestamp (`ORDER BY (ticker, event_time, trade_id)`).
- **Batching**: Prepared vectorized batch inserts of market execution ticks.
- **Validation**:
  - Ingests 500+ tick batches with sub-5ms latency and verifies accurate row counts.

### 2. Statistical Quantile Calculations (`p50`, `p95`, `p99`)
- **Functions**: ClickHouse's native `quantile(0.50)(price)`, `quantile(0.95)(price)`, and `quantile(0.99)(price)`.
- **Validation**:
  - Validates latency and execution price distributions for algorithmic execution quality analysis.

### 3. Real-Time OHLCV Candlestick Aggregation via `argMin` / `argMax`
- Traditional SQL requires complex subqueries or self-joins to find the first price (Open) and last price (Close) within a time window.
- ClickHouse provides vector state combinators:
  - `argMin(last_price, event_time)` -> Finds the price at the minimum timestamp (Open).
  - `max(last_price)` -> High price.
  - `min(last_price)` -> Low price.
  - `argMax(last_price, event_time)` -> Finds the price at the maximum timestamp (Close).
  - `sum(quantity)` -> Total Volume.
  - `sum(last_price * quantity) / sum(quantity)` -> Volume Weighted Average Price (VWAP).
- **Validation**:
  - Ingests time-sequenced ticks with synthetic prices ($150.00 -> $155.00 -> $148.00 -> $153.50).
  - Asserts Open = $150.00, High = $155.00, Low = $148.00, Close = $153.50, and accurate VWAP calculations.

---

## Schema DDL

```sql
CREATE TABLE IF NOT EXISTS market_ticks (
    trade_id UUID,
    ticker LowCardinality(String),
    last_price Decimal64(4),
    quantity Decimal64(4),
    side LowCardinality(String),
    event_time DateTime64(3, 'UTC')
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_time)
ORDER BY (ticker, event_time, trade_id);
```

---

## How to Run the Tests

Runs against a live `clickhouse/clickhouse-server:24.3-alpine` Testcontainer:

```bash
mvn test -pl clickhouse
```
