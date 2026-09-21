# Reactive PostgreSQL Module (`reactive-postgres`)

## Overview
The `reactive-postgres` module explores non-blocking reactive relational database access using **Spring Data R2DBC** and PostgreSQL 18.

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
  - Validates full state rollback when insufficient funds trigger an error during debit.

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

---

## How to Run the Tests

Runs against a live `postgres:18` Testcontainer:

```bash
mvn test -pl reactive-postgres
```
