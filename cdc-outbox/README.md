# Transactional Outbox & Change Data Capture Module (`cdc-outbox`)

## Overview
The `cdc-outbox` module explores the **Transactional Outbox Pattern** and **Change Data Capture (CDC)** to eliminate the classic **Dual-Write Problem** in distributed financial architectures.

By pairing **PostgreSQL 17** (via Spring Data R2DBC) and **Redpanda / Apache Kafka** with **Idempotent Consumers**, this module guarantees **atomic event publishing** and **exactly-once business processing semantics** across asynchronous event streams.

---

## Technical Capabilities Tested & Validated

```
                                 Transactional Outbox & CDC Flow
  +-----------------------------------------------------------------------------------------+
  |                               PostgreSQL (Local ACID Boundary)                          |
  |                                                                                         |
  |   +───────────────────────────────+           +─────────────────────────────────────+   |
  |   |           orders              |           |            outbox_events            |   |
  |   | (order_id, ticker, qty, price)|           | (event_id, payload, status='PENDING'|   |
  |   +───────────────────────────────+           +─────────────────────────────────────+   |
  |                  ▲                                               ▲                      |
  |                  │                        Atomic Commit          │                      |
  |                  +───────────────────────────────────────────────+                      |
  +--------------------------------------------------┬--------------------------------------+
                                                     │
                                                     ▼ Polling Relay (SKIP LOCKED)
                                          +─────────────────────+
                                          |  OutboxRelayService |
                                          +──────────┬──────────+
                                                     │
                                                     ▼ Dispatches Message
                                          +─────────────────────+
                                          |   Redpanda / Kafka  |
                                          | market.orders.events|
                                          +──────────┬──────────+
                                                     │
                                                     ▼ Streams to Consumer
                                    +─────────────────────────────────+
                                    |     IdempotentOrderConsumer     |
                                    |  (Deduplication + OLAP Metrics) |
                                    +─────────────────────────────────+
```

### 1. Dual-Write Prevention via Atomic Outbox Transactions
- **The Problem**: If an application writes to a database and publishes to a message broker in separate steps, network partitions, broker downtimes, or app crashes create inconsistencies (e.g. order saved but event lost, or event sent but database rolls back).
- **The Outbox Solution**: Order creation and outbox event logging occur inside the **same database transaction** using `@TransactionalOperator`:
  ```sql
  INSERT INTO orders (...) VALUES (...);
  INSERT INTO outbox_events (event_id, aggregate_type, aggregate_id, event_type, payload, status)
  VALUES ($1, 'Order', $2, 'ORDER_CREATED', $3::jsonb, 'PENDING');
  ```
- **Validation**:
  - Validates that successful placements persist both the order and a `PENDING` outbox record.
  - Validates that any simulated failure triggers a full rollback—persisting 0 phantom orders and 0 phantom outbox records.

### 2. High-Performance Outbox Relay (`SKIP LOCKED`)
- Selects pending outbox records concurrently without lock contention:
  ```sql
  SELECT event_id, aggregate_type, aggregate_id, event_type, payload::text, status
  FROM outbox_events
  WHERE status = 'PENDING'
  ORDER BY created_at ASC
  FOR UPDATE SKIP LOCKED;
  ```
- Dispatches messages to the Redpanda/Kafka broker topic `market.orders.events`.
- Transitions outbox event state to `PUBLISHED` with `published_at = CURRENT_TIMESTAMP`.

### 3. Idempotent Consumer & Deduplication Log
- **The Problem**: Distributed messaging protocols offer **at-least-once** delivery. Network retries, worker restarts, or delayed acks inevitably produce duplicate messages.
- **The Idempotent Solution**: Downstream consumers track processed `event_id` keys in an `idempotent_consumer_log` table:
  ```sql
  INSERT INTO idempotent_consumer_log (event_id, consumer_name)
  VALUES ($1, $2)
  ON CONFLICT (event_id) DO NOTHING;
  ```
- **Validation**:
  - Replaying the identical event payload multiple times results in exactly 1 business execution. Subsequent attempts are safely skipped and reported as deduplicated.

### 4. Real-Time Streaming OLAP Aggregation
- As outbox events flow from Redpanda into the consumer, real-time volume metrics (`volumeByTicker`) and execution statistics are aggregated concurrently.

---

## Data Model & Schema

```sql
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(64) UNIQUE NOT NULL,
    account_number VARCHAR(64) NOT NULL,
    ticker VARCHAR(16) NOT NULL,
    side VARCHAR(16) NOT NULL,
    order_type VARCHAR(16) NOT NULL,
    price NUMERIC(19, 4),
    quantity NUMERIC(19, 4) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE outbox_events (
    event_id VARCHAR(64) PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE idempotent_consumer_log (
    event_id VARCHAR(64) PRIMARY KEY,
    consumer_name VARCHAR(64) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

---

## How to Run the Tests

Runs against live **PostgreSQL** and **Redpanda** Testcontainers:

```bash
mvn test -pl cdc-outbox
```
