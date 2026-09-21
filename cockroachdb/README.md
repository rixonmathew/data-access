# CockroachDB Distributed SQL Module (`cockroachdb`)

## Overview
The `cockroachdb` module demonstrates distributed ACID transaction semantics, multi-region write contention, and automatic serialization retry handling using **CockroachDB v23.2**.

---

## Technical Capabilities Tested & Validated

### 1. Distributed Write Contention & Serialization Failures
- **Architecture**: CockroachDB uses Multi-Version Concurrency Control (MVCC) and consensus Raft leases to guarantee strict Serializability (`SERIALIZABLE` isolation level).
- **Contention Scenario**: When multiple concurrent worker threads attempt to modify the same account balances simultaneously, CockroachDB rejects the lagging transactions with SQLState `40001` (`TransactionRetryWithProtoRefreshError` / `WriteTooOldError`).
- **Spring Retry Recovery**:
  ```java
  @Retryable(
      retryFor = { SQLException.class, ConcurrencyFailureException.class },
      maxAttempts = 30,
      backoff = @Backoff(delay = 50, maxDelay = 500, multiplier = 1.5, random = true)
  )
  @Transactional(isolation = Isolation.SERIALIZABLE)
  public void transferWithRetry(String fromAccount, String toAccount, BigDecimal amount)
  ```
- **Validation**:
  - Spawns concurrent threads executing 20 competing transfers on a single pair of accounts.
  - Verifies that all 20 transfers eventually succeed after transient retry attempts, with 100% conservation of total funds.

### 2. Follower Reads (`AS OF SYSTEM TIME`)
- Enables read queries to be served by local follower replicas rather than the leaseholder:
  ```sql
  SELECT * FROM accounts AS OF SYSTEM TIME INTERVAL '-5 seconds' WHERE account_number = ?;
  ```
- **Validation**:
  - Validates low-latency consistent reads from historical snapshots.

---

## How to Run the Tests

Runs against a live `cockroachdb/cockroach:v23.2.0` Testcontainer:

```bash
mvn test -pl cockroachdb
```
