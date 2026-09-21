# Redis In-Memory Store & Fast Data Module (`redis`)

## Overview
The `redis` module explores in-memory distributed data structures, concurrency primitives, real-time messaging, and low-latency cache patterns using **Spring Data Redis** and **Jedis / Lettuce**.

---

## Technical Capabilities Tested & Validated

### 1. Distributed Locking with Atomic Lua Release
- **Acquire**: `SET lock:order:1001 <uniqueToken> NX PX 5000` (acquires lock only if key does not exist, with automatic 5-second TTL safety guard).
- **Release**: Uses an atomic Lua script to verify token ownership before deleting:
  ```lua
  if redis.call('get', KEYS[1]) == ARGV[1] then
      return redis.call('del', KEYS[1])
  else
      return 0
  end
  ```
- **Validation**:
  - Prevents race conditions and prevents releasing locks acquired by other processes if a task exceeds its lease timeout.

### 2. High-Precision Sliding-Window Rate Limiter via Sorted Sets (`ZSet`)
- **Problem**: Fixed-window counters allow double-limit bursts across window boundaries.
- **Solution**: Uses Redis Sorted Sets where each request timestamp is stored as both score and member:
  1. `ZREMRANGEBYSCORE key 0 (now - windowMs)` (removes expired timestamps).
  2. `ZCARD key` (counts active requests in sliding window).
  3. If count < limit: `ZADD key now now` and `EXPIRE key windowSeconds`.
- **Validation**:
  - Validates that requests exceeding 10 req/second are rejected, and capacity immediately reopens once the sliding window rolls forward.

### 3. Cache-Aside with Automatic JSON TTL & Eviction
- Serializes complex market quote objects into JSON strings with explicit TTL.
- **Validation**:
  - Confirms sub-millisecond cache hits and verifies eviction after TTL expiration.

### 4. Append-Only Redis Streams (`XADD` / `XREVRANGE`)
- Ingests high-throughput trade events into Redis Streams (`XADD`) with millisecond timestamps.
- **Validation**:
  - Reads chronological event slices with `XREVRANGE` and `XREADGROUP`.

---

## How to Run the Tests

Runs against an official `redis:7.2-alpine` Testcontainer:

```bash
mvn test -pl redis
```
