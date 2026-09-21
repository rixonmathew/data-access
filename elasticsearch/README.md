# Elasticsearch Distributed Search Module (`elasticsearch`)

## Overview
The `elasticsearch` module demonstrates full-text search, relevance scoring, typo tolerance, and real-time aggregations for market instruments and research documents using the official **Elasticsearch Java Client** (`co.elastic.clients:elasticsearch-java:8.17.4`).

---

## Technical Capabilities Tested & Validated

### 1. Multi-Match BM25 Relevance Search with Field Boosting
- **Scenario**: Traders searching for equities using partial names or ticker prefixes (e.g., query `"NVIDIA"` or `"NVDA"`).
- **Weighting**: Multi-match search across fields with custom boosts:
  - `ticker^3` (Ticker match receives 3x relevance boost).
  - `name^2` (Company name receives 2x relevance boost).
  - `description` (Default 1x boost).
- **Validation**:
  - Searching for `"NVIDIA"` returns `NVDA` at position #1 with top relevance score.

### 2. Typo-Tolerant Fuzzy Search (Levenshtein Distance)
- **Scenario**: Fast typing in high-stress execution desks leads to typos (e.g. searching `"Aplpe"` instead of `"Apple"`).
- **Fuzziness**: `Fuzziness.AUTO` calculates allowable edit distances based on term length.
- **Validation**:
  - Searching for `"Aplpe"` successfully retrieves `AAPL` ("Apple Inc.").

### 3. Real-Time Terms Aggregations by Sector
- **Scenario**: Dynamic breakdown of catalog inventory and trading volume by sector.
- **Aggregation**: Terms aggregation grouped on keyword field `sector`.
- **Validation**:
  - Accurately counts document distributions across sectors (`TECHNOLOGY`, `FINANCIALS`, `HEALTHCARE`).

---

## Index Mapping Schema

```json
{
  "mappings": {
    "properties": {
      "ticker": { "type": "keyword", "fields": { "text": { "type": "text" } } },
      "name": { "type": "text" },
      "sector": { "type": "keyword" },
      "description": { "type": "text" },
      "lastPrice": { "type": "double" }
    }
  }
}
```

---

## How to Run the Tests

Integration tests automatically start an `elasticsearch:7.17.24` Testcontainer:

```bash
mvn test -pl elasticsearch
```
