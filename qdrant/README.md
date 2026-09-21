# Qdrant Vector Database Module (`qdrant`)

## Overview
The `qdrant` module demonstrates integration with **Qdrant**, an enterprise-grade vector database built in Rust, optimized for high-dimensional vector search, payload filtering, and neural recommendation systems.

In this module, we model a **Capital Markets Research & Alpha Intelligence** system where financial reports, analyst ratings, and macro briefings are embedded into a dense semantic vector space and queried with hybrid payload filters and vector recommendation algorithms.

---

## Technical Capabilities Tested & Validated

### 1. HNSW Dense Vector Similarity Search (kNN)
- **Metric**: Cosine distance (`Collections.Distance.Cosine`).
- **Use Case**: Quantitative analysts query for semantically similar market reports using query vectors representing concepts such as *"AI semiconductor acceleration and datacenter hyperscaler capex"*.
- **Validation**:
  - Vector search retrieves the closest matching reports with sub-millisecond latency.
  - Returns scores exceeding `0.98` cosine similarity for target semiconductor companies (`NVDA`, `AMD`, `TSM`) while completely separating non-relevant sectors (`JPM`, `GS`).

### 2. Hybrid Payload Metadata Filtering
- **Pattern**: Dense vector similarity search combined with strict relational payload filters in a single traversal pass (avoiding slow post-filtering or pre-filtering compromises).
- **Filter**: `sector = 'SEMICONDUCTORS'` AND `sentiment = 'BULLISH'`.
- **Validation**:
  - `AMD` (which has a high semiconductor vector similarity but `NEUTRAL` sentiment) is strictly filtered out.
  - Only `NVDA` and `TSM` are returned.

### 3. Vector Recommendation Engine (Positive & Negative Anchors)
- **Pattern**: Qdrant's native recommendation engine calculating vectors close to positive anchors and distant from negative anchors.
- **Anchors**:
  - **Positive Anchor**: Point ID 1 (`NVDA` - high hardware & AI score).
  - **Negative Anchor**: Point ID 4 (`JPM` - banking & macro score).
- **Validation**:
  - Recommended items strictly stay within the `SEMICONDUCTORS` sector, actively diverging from financial sector vectors.

---

## Data Model & Payload Schema

```json
{
  "id": 1,
  "vector": [0.95, 0.90, 0.05, 0.10],
  "payload": {
    "ticker": "NVDA",
    "title": "AI GPU datacenter supercycle acceleration",
    "summary": "High margin enterprise inference driving hyperscaler capex",
    "sector": "SEMICONDUCTORS",
    "sentiment": "BULLISH",
    "confidenceScore": 0.96
  }
}
```

* **Vector Dimension 0**: Semiconductor / Hardware
* **Vector Dimension 1**: AI / Datacenter
* **Vector Dimension 2**: Investment Banking / Financials
* **Vector Dimension 3**: Macro / Interest Rates

---

## Architecture & gRPC Client Configuration

Uses the official `io.qdrant:client` library connecting over gRPC port `6334`.

```java
@Bean(destroyMethod = "close")
public QdrantClient qdrantClient() {
    return new QdrantClient(QdrantGrpcClient.newBuilder(host, port, useTls).build());
}
```

---

## How to Run the Tests

The integration test automatically starts a live `qdrant/qdrant:v1.13.4` container via Testcontainers:

```bash
mvn test -pl qdrant
```
