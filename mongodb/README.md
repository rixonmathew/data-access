# MongoDB Document Store Module (`mongodb`)

## Overview
The `mongodb` module validates MongoDB's rich BSON document data model, polymorphic embedded subdocuments, append-only audit trail patterns, and multi-stage aggregation pipelines for institutional trading.

---

## Technical Capabilities Tested & Validated

### 1. Polymorphic Embedded Execution Strategies
- Orders encapsulate complex execution algorithms directly within the document:
  - `TwapStrategy`: Parameters include `startTime`, `endTime`, `numberOfSlices`, `randomizeIntervals`.
  - `VwapStrategy`: Parameters include `startTime`, `endTime`, `participationRate`, `targetPercentage`.
- **Validation**:
  - Spring Data MongoDB discriminator mappings deserialize polymorphic strategy types correctly into their respective class representations.

### 2. Append-Only Embedded Audit Trails
- Instead of maintaining a separate audit table, status changes and modification events are pushed to an embedded array:
  ```json
  "auditEvents": [
    { "timestamp": "...", "fromStatus": "NEW", "toStatus": "PARTIALLY_FILLED", "reason": "Fill 500 shares" }
  ]
  ```
- **Validation**:
  - Validates atomic `$push` array updates without document locking contention.

### 3. Multi-Stage Aggregation Pipeline
- Computes aggregate trading metrics across all orders:
  `$match (status != CANCELLED) -> $group (by ticker, sum quantity, avg price) -> $project -> $sort (by totalNotional DESC)`.
- **Validation**:
  - Verifies aggregated volume and notional amounts across multiple symbols.

### 4. Multi-Faceted Aggregation (`$facet`)
- Generates multiple distinct analytical reports in a single round-trip database query:
  - Facet 1: Breakdown by Status (count and volume per order state).
  - Facet 2: Breakdown by Execution Strategy Type (`TWAP` vs `VWAP`).
  - Facet 3: Top orders sorted by quantity.
- **Validation**:
  - Asserts that all facets are populated simultaneously.

---

## How to Run the Tests

Runs against a live `mongo:7.0` Testcontainer:

```bash
mvn test -pl mongodb
```
