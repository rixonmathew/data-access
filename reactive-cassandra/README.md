# Reactive Cassandra Module (`reactive-cassandra`)

## Overview
The `reactive-cassandra` module explores non-blocking reactive streaming of financial market feeds using **Spring Data Cassandra Reactive** and **Project Reactor** (`Flux` / `Mono`).

---

## Technical Capabilities Tested & Validated

### 1. Reactive Tick Streaming (`Flux`)
- Subscribes to market quote streams (`ReactiveCassandraRepository`), filtering and aggregating ticks without blocking worker threads.
- **Validation**:
  - Emits and collects market tick batches asynchronously with `StepVerifier`.

### 2. Time-Window Reactive Slicing
- Queries non-blocking time-window ranges:
  ```java
  Flux<MarketTickRecord> findByKeyTickerAndKeyTimestampBetween(String ticker, Instant start, Instant end);
  ```
- **Validation**:
  - Validates that ticks outside the requested window are excluded reactively at the driver level.

### 3. Native Per-Row Time-To-Live (TTL) Automatic Tombstoning
- **Scenario**: Ephemeral order-book quote snapshots should expire after a brief time window (e.g. 2 seconds) to avoid disk bloat, without requiring background cleanup cron jobs.
- **Implementation**:
  ```java
  reactiveCassandraTemplate.insert(quote, InsertOptions.builder().ttl(Duration.ofSeconds(2)).build())
  ```
- **Validation**:
  - Quote is visible immediately after insert (`quoteExists == true`).
  - After sleeping past the 2-second TTL, Cassandra natively expires and tombstones the record (`quoteExists == false`).

---

## Schema CQL

```cql
CREATE TABLE IF NOT EXISTS market_ticks (
    ticker text,
    tick_time timestamp,
    tick_id text,
    bid_price decimal,
    ask_price decimal,
    bid_size decimal,
    ask_size decimal,
    PRIMARY KEY (ticker, tick_time, tick_id)
) WITH CLUSTERING ORDER BY (tick_time DESC);

CREATE TABLE IF NOT EXISTS hot_market_quotes (
    ticker text,
    quote_id text,
    bid_price decimal,
    ask_price decimal,
    PRIMARY KEY (ticker, quote_id)
);
```

---

## How to Run the Tests

Runs against a live `cassandra:4.1` Testcontainer:

```bash
mvn test -pl reactive-cassandra
```
