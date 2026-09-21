# Neo4j Graph Database Module (`neo4j`)

## Overview
The `neo4j` module models **Counterparty Credit Risk & Financial Systemic Contagion** using **Neo4j 5** and **Spring Data Neo4j (SDN)** with Cypher query language.

---

## Technical Capabilities Tested & Validated

### 1. Financial Contagion Graph Model
* **Nodes**:
  * `AccountNode`: Represents market institutions, clearing brokers, hedge funds, and prime banks.
* **Relationships**:
  * `EXPOSURE`: Directed credit risk relationships (`(lender)-[:EXPOSURE {exposureAmount, riskWeight}]->(borrower)`).

### 2. Cyclic Contagion Ring Detection
- **Scenario**: In financial crises (e.g. Lehman 2008), hidden circular exposure loops (`Bank A -> Fund B -> Broker C -> Bank A`) cause chain defaults.
- **Cypher Query**:
  ```cypher
  MATCH path = (a:AccountNode)-[:EXPOSURE*2..6]->(a)
  RETURN path
  ```
- **Validation**:
  - Detects cyclic loops in the counterparty graph and extracts participating institutions.

### 3. Shortest Contagion Path Finding
- **Cypher Query**:
  ```cypher
  MATCH (src:AccountNode {accountNumber: $fromAccount}), (dst:AccountNode {accountNumber: $toAccount}),
        p = shortestPath((src)-[:EXPOSURE*..10]->(dst))
  RETURN p
  ```
- **Validation**:
  - Computes the minimum number of counterparty hops through which insolvency would cascade from a distressed fund to a prime bank.

### 4. Downstream Blast Radius Traversal
- **Scenario**: When an institution defaults, calculate the total downstream monetary exposure within a specified depth limit.
- **Cypher Query**:
  ```cypher
  MATCH (root:AccountNode {accountNumber: $account})-[:EXPOSURE*1..3]->(downstream:AccountNode)
  RETURN DISTINCT downstream.accountNumber
  ```
- **Validation**:
  - Validates full blast-radius identification across 3 levels of counterparty depth.

---

## How to Run the Tests

Runs against a live `neo4j:5.13.0` Testcontainer:

```bash
mvn test -pl neo4j
```
