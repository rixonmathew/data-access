# Oracle Enterprise AI & R2DBC Module (`oracle26ai`)

## Overview
The `oracle26ai` module demonstrates reactive non-blocking connectivity to Oracle Database using **Oracle R2DBC** (`com.oracle.database.r2dbc:oracle-r2dbc`) and Spring Data R2DBC.

---

## Technical Capabilities Tested & Validated

### 1. Non-Blocking Instrument Streaming
- Queries and streams financial instrument records from Oracle Database using Project Reactor (`Flux<Instrument>`).
- Exposes Server-Sent Events (SSE) endpoints (`/instruments/stream`).

### 2. Spring Data R2DBC & Wallet Authentication
- Demonstrates connecting securely via Oracle Cloud Wallets (`oracle.net.tns_admin`) using `OSActiveProfilesResolver` to detect active local/cloud profiles dynamically.

---

## How to Run the Tests

```bash
mvn test -pl oracle26ai
```
