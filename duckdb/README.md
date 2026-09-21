# DuckDB Embedded OLAP & S3 Lakehouse Module (`duckdb`)

## Overview
The `duckdb` module demonstrates **DuckDB**, an in-process vectorized analytical SQL database management system. It acts as an embedded query engine executing analytical queries directly against **Apache Parquet files stored in Amazon S3** (emulated via LocalStack).

---

## Technical Capabilities Tested & Validated

### 1. Direct Analytical Querying on Remote S3 Parquet Files
- DuckDB connects to S3 via its HTTP/S3 filesystem extensions and queries remote Parquet files without downloading them first:
  ```sql
  SELECT department, COUNT(*) as count 
  FROM read_parquet('s3://my-bucket/employees.parquet')
  GROUP BY department 
  ORDER BY count DESC;
  ```
- **Validation**:
  - Validates projection pushdown and column pruning directly over the network.

### 2. Format & Query Performance Comparison (Parquet vs DuckDB Format)
- Benchmarks query execution times between compressed columnar Parquet files and DuckDB native database files.
- **Validation**:
  - `FileQueryServiceIntegrationTest` benchmarks queries across 10 runs and validates data consistency and latency differences.

---

## How to Run the Tests

Runs against a live `localstack/localstack:3.4.0` Testcontainer:

```bash
mvn test -pl duckdb
```
