# Reactive PostgreSQL & pgvector Module (`reactive-postgres`)

## Overview
The `reactive-postgres` module explores non-blocking reactive relational database access and native vector similarity search using **Spring Data R2DBC**, **PostgreSQL 17**, and the **`pgvector`** extension.

It demonstrates both core mission-critical OLTP patterns (atomic multi-account balance transfers, optimistic concurrency control, non-blocking backpressure) and modern **Hybrid Relational + Vector Storage** (HNSW indexing, cosine similarity, relational SQL filtering, and cross-domain JOINs between live orders and financial research embeddings).

---

## Technical Capabilities Tested & Validated

### 1. Atomic Balance Transfers with `@TransactionalOperator`
- Non-blocking programmatic transaction boundaries across multiple reactive operations:
  ```java
  public Mono<Void> executeAtomicTransfer(String fromAcc, String toAcc, BigDecimal amount) {
      return findAndDebit(fromAcc, amount)
              .then(findAndCredit(toAcc, amount))
              .as(transactionalOperator::transactional);
  }
  ```
- **Validation**:
  - Validates full state rollback when simulated failures or insufficient funds trigger an error during debit.

### 2. Optimistic Concurrency with `@Version`
- PostgreSQL entity optimistic locking prevents lost updates without coarse table locks:
  ```java
  @Version
  private Long version;
  ```
- **Validation**:
  - Two parallel Mono streams attempting to update the same record result in an `OptimisticLockingFailureException` on the second transaction.

### 3. Non-Blocking Streaming & Backpressure
- Streams large volumes of historical orders using Project Reactor's `limitRate(25)`:
  ```java
  orderRepository.findByAccountNumber(acc)
          .limitRate(25)
          .take(50);
  ```
- **Validation**:
  - Ensures slow downstream consumers are not overwhelmed by fast database query streams.

### 4. `pgvector` Dense Vector Search & HNSW Indexing
- Stores 4-dimensional latent semantic vectors directly in PostgreSQL:
  ```sql
  CREATE EXTENSION IF NOT EXISTS vector;

  CREATE TABLE market_research_reports (
      id VARCHAR(64) PRIMARY KEY,
      ticker VARCHAR(16) NOT NULL,
      title VARCHAR(255) NOT NULL,
      summary TEXT NOT NULL,
      sector VARCHAR(64) NOT NULL,
      sentiment VARCHAR(16) NOT NULL,
      confidence_score DOUBLE PRECISION NOT NULL,
      embedding vector(4) NOT NULL
  );

  CREATE INDEX idx_research_hnsw ON market_research_reports 
  USING hnsw (embedding vector_cosine_ops) 
  WITH (m = 16, ef_construction = 64);
  ```
- **Validation**:
  - kNN Cosine similarity search using `<=>` operator (returns AI GPU themes matching `NVDA` and `AMD` with similarity > 0.98).
  - kNN Euclidean distance search using `<->` operator for exact matches.

### 5. Hybrid Relational SQL Predicates + Vector Similarity
- **The "Split-Brain" Problem**: Traditional architectures require synchronizing relational databases with external vector stores, risking stale data and two-phase lookups.
- **pgvector Solution**: Filters relational attributes and ranks by dense vector similarity in a **single unified ACID query**:
  ```sql
  SELECT id, ticker, title, summary, sector, sentiment, confidence_score,
         1 - (embedding <=> $1::vector) AS similarity
  FROM market_research_reports
  WHERE sector = $2 AND sentiment = $3
  ORDER BY embedding <=> $1::vector
  LIMIT $4;
  ```
- **Validation**:
  - Evaluates queries for `BULLISH` `Semiconductors`, cleanly pruning non-bullish or out-of-sector tickers (`INTC`, `MSFT`, `XOM`).

### 6. Cross-Domain Relational JOIN with Orders
- Directly joins OLTP trade orders with research report embeddings:
  ```sql
  SELECT o.order_id, o.account_number, o.ticker, o.side, o.quantity, o.price,
         r.title AS report_title, r.sentiment, r.confidence_score,
         1 - (r.embedding <=> $1::vector) AS vector_similarity
  FROM orders o
  JOIN market_research_reports r ON o.ticker = r.ticker
  ORDER BY r.embedding <=> $1::vector
  LIMIT $2;
  ```
- **Validation**:
  - Correlates open client orders with real-time research insights and semantic theme similarity.

---

## How to Run the Tests

Runs against a live `pgvector/pgvector:pg17` Testcontainer:

```bash
mvn test -pl reactive-postgres
```
