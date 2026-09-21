# DynamoDB Module (`dynamodb`)

## Overview
The `dynamodb` module models a high-throughput **Order Management & Trade Fill System** using Amazon DynamoDB. It implements the industry-standard **Single-Table Design pattern (Alex DeBrie pattern)** utilizing the **AWS SDK v2 DynamoDB Enhanced Client** (`software.amazon.awssdk:dynamodb-enhanced`).

---

## Technical Capabilities Tested & Validated

### 1. Single-Table Design Architecture
Multiple business entities coexist within a single physical table (`trading_single_table`), partitioned with generic partition (`pk`) and sort (`sk`) keys.

* **Order Metadata Item**:
  * `pk`: `ORDER#<orderId>`
  * `sk`: `METADATA`
  * `gsi1pk`: `ACCOUNT#<accountId>`
  * `gsi1sk`: `ORDER#<createdAt>`
  * Attributes: `ticker`, `side`, `quantity`, `filledQuantity`, `limitPrice`, `status`, `version`, `createdAt`.
* **Execution Fill Item**:
  * `pk`: `ORDER#<orderId>` (same partition key as the parent order!)
  * `sk`: `EXECUTION#<executionId>`
  * `gsi1pk`: `TICKER#<ticker>`
  * `gsi1sk`: `EXECUTION#<executedAt>`
  * Attributes: `ticker`, `executedPrice`, `executedQuantity`, `executedAt`, `liquidity`.

### 2. Single-Roundtrip Aggregate Query
- **Problem**: In traditional databases or naive NoSQL, retrieving an order and its executions requires joins or multiple network requests.
- **Solution**: By sharing partition key `pk = ORDER#<orderId>`, a single query returns the order header AND all of its execution fills simultaneously.
- **Validation**:
  - `tradingService.getOrderAggregate(tableName, orderId)` fetches the full `OrderAggregateDto` (order + 2 execution fills) in one sub-millisecond query.

### 3. Atomic Multi-Item Transactions (`transactWriteItems`)
- When a new trade execution arrives, two operations must happen atomically:
  1. Increment `filledQuantity` on the `SingleTableOrderEntity` and update status (`PARTIALLY_FILLED` or `FILLED`).
  2. Put the new `SingleTableExecutionEntity` record.
- **Validation**:
  - `tradingService.recordExecutionAtomic(tableName, execution)` executes both in a single `TransactWriteItemsEnhancedRequest`.

### 4. Optimistic Locking with `@DynamoDbVersionAttribute`
- Prevents concurrent dirty writes or race conditions when multiple trading threads update the same order header.
- **Validation**:
  - Two readers fetch order copy A and copy B (both version 1).
  - Writer A updates the price -> succeeds, incrementing version to 2.
  - Writer B attempts to update with stale version 1 -> DynamoDB rejects the write, throwing `ConditionalCheckFailedException`.

### 5. Inverted GSI1 Access Patterns
- **Query Orders by Account**: `gsi1pk = ACCOUNT#<accountId>` retrieves all orders for an account sorted chronologically.
- **Query Fills by Ticker**: `gsi1pk = TICKER#<ticker>` retrieves all market executions for a ticker across all orders without full table scans.

---

## How to Run the Tests

Tests run against a local DynamoDB instance powered by **Testcontainers LocalStack 3.4.0**:

```bash
mvn test -pl dynamodb
```
