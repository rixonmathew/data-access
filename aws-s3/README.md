# AWS S3 Object Storage Module (`aws-s3`)

## Overview
The `aws-s3` module demonstrates high-throughput object storage operations using the **AWS SDK for Java 2.x** (`software.amazon.awssdk:s3`).

---

## Technical Capabilities Tested & Validated

### 1. High-Throughput Batch Market Data Upload
- Synthesizes market instrument records into temporary CSV files and uploads them into an S3 bucket with path-style addressing (`forcePathStyle = true`).
- Validates performance and execution duration.

### 2. Live LocalStack S3 Integration
- Tests execute against a real LocalStack container (`localstack/localstack:3.4.0`), creating test buckets and verifying uploaded object headers via `HeadObjectRequest`.

---

## How to Run the Tests

```bash
mvn test -pl aws-s3
```
