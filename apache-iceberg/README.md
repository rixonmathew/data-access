# Apache Iceberg Open Lakehouse Storage Module (`apache-iceberg`)

## Overview
The `apache-iceberg` module demonstrates **Apache Iceberg**, the high-performance open table format for massive analytic datasets. Unlike traditional Hive tables that rely on directory structures, Apache Iceberg tracks individual data files in a hierarchical tree of metadata, enabling true ACID transactions, hidden partitioning, in-place partition evolution, schema evolution with field ID tracking, and time travel.

This module provides a pure Java validation and testbed pairing the official **Apache Iceberg Java Client** (`org.apache.iceberg:iceberg-core`, `iceberg-parquet`, `iceberg-data`) with **DuckDB** and **LocalStack S3**.

---

## Technical Capabilities Tested & Validated

```
                               Iceberg Metadata Architecture
  +-----------------------------------------------------------------------------------+
  |                             v1 / v2 / v3.metadata.json                           |
  | (Table Schema, Current Snapshot ID, Partition Specs, Schemas, Properties)         |
  +-----------------------------------------------------------------------------------+
                                         │
                                         ▼
  +-----------------------------------------------------------------------------------+
  |                       snap-<snapshot_id>-1-<uuid>.avro (Manifest List)             |
  | (Manifest file paths, partition summaries, added/existing/deleted file counts)    |
  +-----------------------------------------------------------------------------------+
                      │                                        │
                      ▼                                        ▼
  +─────────────────────────────────────────+  +─────────────────────────────────────+
  |    <uuid>-m0.avro (Manifest File 1)     |  |    <uuid>-m1.avro (Manifest File 2) |
  | (Data file paths, column min/max bounds)|  | (Data file paths, column bounds)    |
  +─────────────────────────────────────────+  +─────────────────────────────────────+
            │                      │                             │
            ▼                      ▼                             ▼
    part-1.parquet         part-2.parquet                part-3.parquet
    (Spec 0: Unpartitioned) (Spec 1: ticker=NVDA)         (Spec 1: ticker=MSFT, venue)
```

### 1. ACID Transactions & Hierarchical Metadata Tree
- **Hierarchical Layout**:
  - `metadata/v<N>.metadata.json`: Immutable snapshot of table configuration, schemas, partition specs, and snapshots.
  - `metadata/snap-<id>-<attempt>-<uuid>.avro`: Manifest list referencing all active manifests for the snapshot.
  - `metadata/<uuid>-m<idx>.avro`: Manifest files cataloging individual Parquet data files with file-level column metrics.
  - `data/.../*.parquet`: Vectorized columnar data files containing raw row groups.
- **Commit Action**:
  - Validates atomic commits via `table.newAppend().appendFile(...).commit()`.
  - Captures snapshot ID, parent snapshot ID, operation (`append`), manifest counts, and data file additions.

### 2. Hidden Partitioning & Dynamic Predicate Pruning
- **Problem in Hive / Delta**: Traditional engines require query authors to know the physical partition columns and write artificial predicates matching directory structures (e.g. `WHERE date = '...'`).
- **Iceberg Innovation**: Partition transforms are tracked in table metadata. Queries simply filter on logical business columns (e.g. `ticker == 'AAPL'`), and Iceberg automatically prunes non-matching files and manifests using partition summaries and column min/max statistics.
- **Validation**:
  - Executes queries with predicates (`Expressions.equal("ticker", "AAPL")`), proving predicate pushdown and partition pruning without physical directory awareness.

### 3. In-Place Partition Evolution (Zero-Copy)
- **Problem**: In traditional table formats, changing a table's partitioning strategy requires migrating or rewriting the entire dataset.
- **Iceberg Innovation**: Tables maintain a collection of partition specifications (`specId = 0`, `specId = 1`, etc.). Existing data files remain under their original partition spec, while new writes follow the evolved spec.
- **Validation**:
  - Begins with an **unpartitioned** table (`specId = 0`) and commits Batch 1.
  - Evolves the partition spec in-place to partition by `ticker` (`table.updateSpec().addField("ticker").commit()`).
  - Commits Batch 2 into partition directories (`ticker=GOOGL/part-*.parquet`).
  - Executes a unified query across the table: Iceberg seamlessly scans and returns records from both Spec 0 (unpartitioned) and Spec 1 (partitioned) files in a single pass without data migration.

### 4. In-Place Schema Evolution with Unique Field IDs
- **Problem**: Schema changes in legacy formats can cause silent column collisions or corrupted reads if columns are renamed, reordered, or re-added.
- **Iceberg Innovation**: Iceberg schemas assign unique immutable integer field IDs (e.g. `1: tradeId`, `2: ticker`, `7: venue`). Column lookups occur by ID, never by column position or name alone.
- **Validation**:
  - Dynamically adds the `venue` column (`table.updateSchema().addColumn("venue", Types.StringType.get()).commit()`).
  - Commits Batch 3 with populated venues (`"NASDAQ"`, `"NYSE"`).
  - Historical records read from earlier snapshots resolve `null` for `venue`, while new rows resolve their exact values.

### 5. Time Travel Historical Snapshots & Snapshot Rollback
- **Point-in-Time Querying**: Any historical snapshot can be read using `IcebergGenerics.read(table).useSnapshot(snapshotId)`.
  - Snapshot 1 returns exactly 3 trades.
  - Snapshot 2 returns 5 trades.
  - Snapshot 3 returns 7 trades.
- **Snapshot Rollback**:
  - Reverts the table's current snapshot pointer to Snapshot 1 via `table.manageSnapshots().setCurrentSnapshot(snapshot1Id).commit()`.
  - Verifies that subsequent default reads reflect the exact historical state of Snapshot 1.

### 6. Embedded Vectorized SQL Queries via DuckDB
- Resolves all active Parquet files from the Iceberg table scan (`table.newScan().planFiles()`).
- Executes vectorized SQL queries and analytical aggregations over the multi-partition, multi-spec files:
  ```sql
  SELECT count(*) as total_trades, sum(quantity) as total_volume, round(avg(price), 2) as avg_price 
  FROM read_parquet([...], union_by_name=true)
  ```

### 7. LocalStack S3 Cloud Lakehouse Synchronization
- Replicates the complete Iceberg table structure (`metadata/*.metadata.json`, `metadata/*.avro`, and `data/**/*.parquet`) to **LocalStack S3** (`s3://lakehouse-iceberg-data/iceberg-warehouse/finance/market_trades/`).
- Validates object hierarchy and integrity using the AWS SDK v2 S3 client.

---

## Data Model & Schema

```yaml
Base Schema (Schema ID 0):
  - 1: tradeId (String, required)
  - 2: ticker (String, required)
  - 3: price (Double, required)
  - 4: quantity (Long, required)
  - 5: side (String, required)
  - 6: executedAt (Long, required)

Evolved Schema (Schema ID 1):
  - 1: tradeId (String, required)
  - 2: ticker (String, required)
  - 3: price (Double, required)
  - 4: quantity (Long, required)
  - 5: side (String, required)
  - 6: executedAt (Long, required)
  - 7: venue (String, optional, added dynamically)
```

---

## How to Run the Tests

Integration tests automatically spin up **LocalStack 3.4.0** for S3 emulation:

```bash
mvn test -pl apache-iceberg
```
