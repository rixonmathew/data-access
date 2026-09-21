# Apache Cassandra Wide-Column Store Module (`cassandra`)

## Overview
The `cassandra` module demonstrates Apache Cassandra's distributed wide-column architecture optimized for massive write throughput, time-series partitioning, and Paxos consensus Lightweight Transactions (LWT).

---

## Technical Capabilities Tested & Validated

### 1. Time-Series Composite Key & Clustering Order
- **Partition Key**: `((ticker, bucket_date))` — Localizes daily trades per symbol to the same cluster nodes and partition SSTable.
- **Clustering Key**: `(execution_time, execution_id)` — Sorts executions physically on disk in descending chronological order (`CLUSTERING ORDER BY (execution_time DESC)`).
- **Validation**:
  - Sub-millisecond retrieval of the most recent executions for a ticker.
  - Efficient time-window queries between `startTime` and `endTime` without cross-partition disk scans.

### 2. Paxos Lightweight Transactions (LWT) & Conditional Writes
- **Idempotent Ingestion**: `INSERT ... IF NOT EXISTS` ensures trade fills are never double-processed on network retries.
- **Compare-And-Set (CAS) Status Updates**:
  ```sql
  UPDATE trade_executions SET status = ? WHERE ticker = ? AND bucket_date = ? AND execution_time = ? AND execution_id = ? IF status = ?;
  ```
- **Validation**:
  - CAS transition from `EXECUTED` to `SETTLED` succeeds.
  - Attempting to settle an already settled trade returns `applied = false` via Cassandra's Paxos round, preventing state corruption.

---

## Schema CQL

```cql
CREATE TABLE IF NOT EXISTS trade_executions (
    ticker text,
    bucket_date text,
    execution_time timestamp,
    execution_id text,
    order_id text,
    account_number text,
    price decimal,
    quantity decimal,
    side text,
    status text,
    PRIMARY KEY ((ticker, bucket_date), execution_time, execution_id)
) WITH CLUSTERING ORDER BY (execution_time DESC, execution_id ASC);
```

---

## How to Run the Tests

Runs against an official `cassandra:4.1` Testcontainer:

```bash
mvn test -pl cassandra
```
