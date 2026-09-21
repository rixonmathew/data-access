# Trino & Spark Distributed Query Module (`trinospark`)

## Overview
The `trinospark` module models distributed query engine integration patterns (Trino, Apache Spark SQL) and collection transformation operations over financial trades and orders.

---

## Technical Capabilities Tested & Validated

* Multi-dimensional stream grouping and transformation pipelines.
* Grouping by state and settlement date with parallel stream collection.
* Analytical SQL dialect support for distributed query engines.

---

## How to Run the Tests

```bash
mvn test -pl trinospark
```