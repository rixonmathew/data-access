# Apache Ignite In-Memory Computing Module (`apache-ignite`)

## Overview
The `apache-ignite` module demonstrates **Apache Ignite**, a distributed database, caching, and in-memory computing platform designed for high-performance transactions and real-time processing.

---

## Technical Capabilities Tested & Validated

* Distributed in-memory key-value caching.
* Controller and service layer integration with Spring Boot.
* In-memory retrieval and cache persistence logic.
* **SQL over the cache** (`PersonServiceSqlIntegrationTest`): `findByLastName` runs `SELECT ... WHERE lastName = ?` on a real embedded Ignite node, using indexed `@QuerySqlField` columns.

### SQL engine: Calcite, not H2
The node runs Ignite's **Calcite-based SQL engine** (`ignite-calcite`, set as the default with `CalciteQueryEngineConfiguration`). The older H2 engine from `ignite-indexing` required H2 1.4.197, which has two critical advisories (GHSA-h376-j262-vhq6, GHSA-45hx-wfhj-473x), so neither `ignite-indexing` nor H2 is on the classpath.

### Java 25
Ignite 2.x needs a list of `--add-opens` flags on JDK 17 and later. The Surefire `argLine` in `pom.xml` carries the list from the Ignite docs; pass the same flags to run the app itself.

---

## How to Run the Tests

```bash
mvn test -pl apache-ignite
```
