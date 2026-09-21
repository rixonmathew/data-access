# Reactive H2 Module (`reactive-h2`)

## Overview
The `reactive-h2` module demonstrates reactive in-memory relational data access using **Spring Data R2DBC** and the H2 R2DBC driver (`io.r2dbc:r2dbc-h2`).

---

## Technical Capabilities Tested & Validated

* Reactive contract management and non-blocking CRUD.
* Automatic schema execution via `schema.sql` on startup.
* Reactive stream verification with `StepVerifier`.

---

## How to Run the Tests

```bash
mvn test -pl reactive-h2
```
