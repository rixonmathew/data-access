# Commons Module (`commons`)

## Overview
The `commons` module contains the **unified domain model** and synthetic data generators shared across all database modules in the repository.

---

## Domain Models

* **Accounts & Balances**: `Account` with account type, balance, currency, version, and status.
* **Order Management**: `Order` with ticker, side (`BUY`/`SELL`), order type (`MARKET`/`LIMIT`), quantity, and execution status.
* **Trade Executions**: `TradeExecution` representing atomic fills and settlements.
* **Market Quotes & Ticks**: `MarketQuote` and `MarketTickRecord` representing high-frequency price feeds.
* **Counterparty Exposure**: `CounterpartyExposure` and `RiskLimit` for graph risk analysis.
* **Instruments**: `Instrument` with ticker, ISIN, asset class, and metadata.

---

## Data Utilities

* [`DataGeneratorUtils`](file:///Users/rixonmathew/workspace/github/rixonmathew/data-access/commons/src/main/java/com/rixon/model/util/DataGeneratorUtils.java): Deterministic synthetic generators for accounts, orders, trades, instruments, and counterparty relationships.
