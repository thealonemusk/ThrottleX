# ThrottleX — Low Level Design (LLD)

> **Purpose:** Detailed design for implementing the ThrottleX rate-limiting service. This document bridges the HLD to working code and deployment.

---

## Table of Contents

1. Goals & Constraints
2. Component LLDs
   * Filter / Middleware
   * ThrottleX Service
   * Limiter Implementations (Token Bucket, Sliding Window)
   * Persistence Layer (MySQL)
   * Admin APIs
3. Data Model (DDL)
4. API Specification (Endpoints)
5. Concurrency & Correctness
6. Performance Optimizations
7. Configuration & Runtime Behavior
8. Observability & Metrics
9. Deployment Topology & Infra
10. Testing Strategy
11. Rollout & Migration

---

## 1. Goals & Constraints

* **Latency:** keep added latency ≤ 1–3 ms for token-bucket path.
* **Throughput:** 10k+ RPS per instance target (MySQL-backed baseline).
* **Consistency:** strong consistency per key; correctness preferred over loose availability.
* **Extensibility:** add new limiters (Redis/Lua) with minimal code change.
* **Embeddable:** support library-mode and service-mode.

---

## 2. Component LLDs

### 2.1 ThrottleX Filter (Middleware)

**Location:** `com.throttlex.middleware.ThrottleXFilter`

**Responsibilities:**

* Extract key(s): IP, API key header `X-API-Key`, authenticated user id, and route path.
* Normalize key ordering for multi-key policies (e.g., `user:123|route:/v1/pay`).
* Fetch policy metadata (local cache; fallback to DB).
* Call `ThrottleXService.checkAndConsume(key, policy)`.
* Add Response headers on success/failure: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `Retry-After` (RFC style).

**Implementation Notes:**

* Extend `OncePerRequestFilter`.
* Short-circuit and return 429 on denial.
* Use a tiny local LRU cache (Caffeine) for policy metadata.

### 2.2 ThrottleX Service

**Location:** `com.throttlex.service.ThrottleXService`

**Responsibilities:**

* Resolve or create `UsageRecord` via `UsageRepository`.
* Compute chosen limiter and call `LimiterFactory`.
* Handle persistence of updates with minimal DB round-trips.
* Emit metrics for success/blocked operations.

**Signature:**

```java
boolean checkAndConsume(String key, Policy policy);
```

**Optimizations:**

* Read-Modify-Write in one DB transaction using `@Transactional` with `PESSIMISTIC_WRITE` lock.
* Batch update for high-throughput endpoints (future).

### 2.3 Limiter Implementations

#### TokenBucketLimiter

* Inputs: `UsageRecord`, `capacity`, `refillRatePerSecond`.
* Logic:
  1. Compute `elapsedSec = (now - lastRefill) / 1000`.
  2. `tokensToAdd = elapsedSec * refillRate`.
  3. `tokens = min(capacity, tokens + tokensToAdd)`.
  4. If tokens >= 1 → tokens--, persist, return allowed.
  5. Else return denied.
* Persist using the same DB transaction that locked the record.
* Return additional metadata: remaining tokens, reset time.

#### SlidingWindowLimiter

* Two design options:
  1. **MySQL-based approximate sliding window** — store time-bucketed counters (e.g., 1-sec buckets) in a `usage_buckets` table and query SUM last N seconds. More storage but relational-friendly.
  2. **Redis** + sorted-set TTL approach (preferred for accuracy/perf): store timestamps in a sorted-set and remove older entries via ZREMRANGEBYSCORE + ZCARD atomic pair. Use Lua to make atomic.

**MySQL approach details:**

* Table `usage_buckets(keyId, bucket_ts, count)` (bucket_ts = epoch seconds).
* On request: increment bucket row `ON DUPLICATE KEY UPDATE count = count + 1`, then SELECT SUM(count) for last `windowSeconds`.
* Use `AUTO_INCREMENT` primary key or composite PK `(keyId, bucket_ts)` for fast `INSERT ... ON DUPLICATE KEY`.

### 2.4 Persistence Layer (MySQL)

**Tables:**

1. `throttlex_usage` — token-bucket state
2. `throttlex_buckets` — sliding-window counters (if using MySQL)
3. `throttlex_policies` — optional per-route/user policies

**throttlex_usage (DDL)**

```sql
CREATE TABLE throttlex_usage (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  key_id VARCHAR(255) NOT NULL,
  tokens BIGINT NOT NULL,
  last_refill BIGINT NOT NULL,
  CONSTRAINT uq_key UNIQUE (key_id)
) ENGINE=InnoDB;
```

**throttlex_buckets (DDL)**

```sql
CREATE TABLE throttlex_buckets (
  key_id VARCHAR(255) NOT NULL,
  bucket_ts BIGINT NOT NULL,
  count INT NOT NULL,
  PRIMARY KEY (key_id, bucket_ts)
) ENGINE=InnoDB;
```

**Indexes:** unique index on `key_id` for `usage`, composite PK for buckets.

### 2.5 Admin APIs

* `GET /admin/status` — health
* `GET /admin/policy/{key}` — show effective policy
* `POST /admin/policy` — add/update policy
* `GET /admin/usage/{key}` — return current tokens, lastRefill, recent buckets

**Auth:** admin endpoints should be protected via API key or OAuth; enforce IP allowlist.

---

## 3. Data Model (Detailed)

**UsageRecord Java class**

```java
public class UsageRecord {
  private Long id;
  private String keyId;
  private long tokens;        // remaining tokens
  private long lastRefill;    // epoch millis
}
```

**Policy model (Java)**

```java
public class Policy {
  String type; // "token-bucket" | "sliding-window"
  int capacity;
  int refillRate;
  int windowSeconds; // for sliding
}
```

---

## 4. API Specification (Detailed)

### Runtime (applied per incoming request)

* Authenticated request header lookup order: `X-API-Key`, `Authorization`, `X-User-Id`.
* Route derived as `HTTP_METHOD + path_template` (e.g., `GET /v1/pay`).

### Admin APIs (JSON)

**POST /admin/policy**

```json
{ "key": "route:/v1/pay", "type": "token-bucket", "capacity": 100, "refillRate": 10 }
```

**GET /admin/usage/{key}** returns:

```json
{ "key": "user:123", "tokens": 42, "lastRefill": 1690000000000 }
```

---

## 5. Concurrency & Correctness

### Token Bucket (MySQL)

* Use a single `SELECT ... FOR UPDATE`/`@Lock(LockModeType.PESSIMISTIC_WRITE)` to lock the row.
* Transaction scope:
  * Begin transaction
  * SELECT usage WITH PESSIMISTIC_WRITE
  * Compute & update tokens
  * Commit

This guarantees linearizability per key.

**Optimization:** Avoid locking when short-circuit possible (e.g., tokens are full and refill timestamp unchanged) — risky; prefer correctness initially.

### Sliding Window (MySQL)

* Use `INSERT ... ON DUPLICATE KEY UPDATE` for bucket increments — this is atomic.
* After increment, `SELECT SUM(count)` for window; if count > limit → deny and optionally revert increment (or leave and apply penalty). Better: check then increment within a transaction.

**Redis variant** (recommended for production):

* Use a Lua script that performs ZREMRANGEBYSCORE + ZADD + ZCARD and returns ZCARD. Atomic in Redis.

---

## 6. Performance Optimizations

1. **Local cache for policies** (Caffeine) with TTL 1–5s.
2. **Connection pooling** (HikariCP) for DB.
3. **Prepared statements and batch updates** where possible.
4. **Use Redis for ultra-low-latency counters** ; MySQL for cold storage/analytics.
5. **Sharding keys** across DB instances if write throughput is bottleneck.
6. **Bulk polling** for metrics, avoid per-request metric pushes.

---

## 7. Configuration & Runtime Behavior

* `application.yml` with defaults and environment overrides.
* Support dynamic overrides through admin API — update in DB and refresh local cache.
* Support runtime mode flag: `throttlex.mode = service | embedded`.

---

## 8. Observability & Metrics

* Expose Prometheus metrics (Micrometer):
  * `throttlex_allowed_total` (counter)
  * `throttlex_blocked_total` (counter)
  * `throttlex_latency_ms` (histogram)
  * `throttlex_tokens_remaining{policy=..}` (gauge per-key optional)
* Add structured logs with request-id and keyId.
* Trace requests with OpenTelemetry if enabled.

---

## 9. Deployment Topology & Infra

* Docker image: `throttlex:VERSION`.
* Kubernetes Deployment with HPA based on CPU/throughput.
* MySQL managed (RDS / Cloud SQL) with Multi-AZ.
* For Redis variant: Redis Cluster or ElastiCache with sufficient throughput.

Optional: run ThrottleX as a sidecar to services for per-service isolation.

---

## 10. Testing Strategy

* Unit tests for TokenBucket and SlidingWindow logic.
* Integration tests using Testcontainers (MySQL, Redis).
* Load tests with Gatling or k6 to validate RPS and latency.
* Chaos tests to simulate DB failover.

---

## 11. Rollout & Migration

* Start with MySQL-only token-bucket on low-traffic routes.
* Gradually onboard routes; monitor blocked rates.
* Introduce Redis-backed limiter for hotspots and migrate using a feature flag.

---

# Appendix: Important Code Snippets (pseudocode)

**TokenBucket (transactional)**

```java
@Transactional
public boolean checkAndConsume(String key) {
  UsageRecord u = repo.findByKeyIdForUpdate(key).orElse(createDefault(key));
  if (tokenBucket.allow(u, capacity, refill)) {
    repo.save(u);
    return true;
  }
  return false;
}
```

**Redis Sliding Window (Lua)**

```lua
-- KEYS[1] = key, ARGV[1]=now, ARGV[2]=windowSec, ARGV[3]=max
redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1]-ARGV[2])
redis.call('ZADD', KEYS[1], ARGV[1], ARGV[1])
local cnt = redis.call('ZCARD', KEYS[1])
redis.call('EXPIRE', KEYS[1], ARGV[2])
return cnt
```

---

*Document author: Ashutosh Jha*
