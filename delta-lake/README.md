# Delta Lake Lakehouse Storage Module (`delta-lake`)

## Overview
The `delta-lake` module demonstrates **Delta Lake**, the open-source storage framework created by Databricks that brings ACID transactions, time travel, and schema evolution to data lakes on object storage (such as Amazon S3).

This module enables evaluating the core Databricks Lakehouse storage architecture entirely locally and offline, pairing the **Delta Standalone Java Client** (`io.delta:delta-standalone`) with **DuckDB** and **LocalStack S3**.

---

## Technical Capabilities Tested & Validated

### 1. ACID Transactions & Commit Log Structure
- **Storage Layout**:
  - Parquet data files stored in the table root directory (`part-<uuid>.parquet`).
  - Transaction log commits stored in `_delta_log/00000000000000000000.json`, `_delta_log/00000000000000000001.json`, etc.
- **Commit Actions**:
  - `metaData`: Table schema, partitioning, and properties.
  - `add`: Points to new Parquet files with record-count statistics.
  - `commitInfo`: Audit history containing operation name (`CREATE_TABLE`, `WRITE`, `ADD_COLUMNS`), engine string, and commit timestamp.
- **Validation**:
  - Verifies that each batch write increments the table version atomically.

### 2. Delta Lake Time Travel (`VERSION AS OF 0` vs `VERSION AS OF 1`)
- **Problem**: Traditional data lakes overwrite data, making historical point-in-time auditing and reproducibility difficult.
- **Solution**: Delta Lake retains historical data files and transaction logs. Any past version can be queried using `deltaLog.getSnapshotForVersionAsOf(version)`.
- **Validation**:
  - Commits Batch 1 (`Version 0` with 2 trades: `AAPL`, `NVDA`).
  - Commits Batch 2 (`Version 1` with 2 trades: `GOOGL`, `TSLA`).
  - Querying `Snapshot(Version 0)` returns exactly 2 records (`AAPL`, `NVDA`), completely isolated from subsequent commits.
  - Querying `LatestSnapshot(Version 1)` returns all 4 records!

### 3. Schema Evolution (`ADD_COLUMNS`)
- **Scenario**: In Version 2, trading feeds begin delivering an additional attribute: `venue` (e.g. `"NASDAQ"`, `"NYSE"`).
- **Evolution**: Delta Lake evolves the schema metadata (`Operation.Name.ADD_COLUMNS`) without rewriting historical files.
- **Validation**:
  - Latest snapshot schema contains 7 fields (including `venue`), while historical Snapshot 0 retains its original 6-field schema.
  - Newly appended trades contain populated venue attributes, while historical trades resolve `null` for the new column.

### 4. Embedded Vectorized SQL Queries via DuckDB
- Resolves active data files from the Delta Lake snapshot and queries them with DuckDB's vectorized Parquet reader (`read_parquet(['...'])`).

### 5. LocalStack S3 Lakehouse Sync
- Uploads the complete Delta Lake table hierarchy (`_delta_log/*.json` and `part-*.parquet`) to **LocalStack S3** (`s3://lakehouse-market-data/delta/market_trades/`).
- Validates object hierarchy, key paths, and content lengths using the AWS SDK v2 S3 client.

---

## Data Model & Schema

```
StructType:
  - tradeId: String (non-nullable)
  - ticker: String (non-nullable)
  - price: Double (non-nullable)
  - quantity: Long (non-nullable)
  - side: String (non-nullable)
  - executedAt: Long (non-nullable)
  - venue: String (nullable, added in Version 1 / 2 via Schema Evolution)
```

---

## How to Run the Tests

Integration tests automatically spin up **LocalStack 3.4.0** for S3 emulation:

```bash
mvn test -pl delta-lake
```
