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

**Implemented design: MySQL request-log table**

* Table `throttlex_sw_log(id, key_id, request_time)` stores one row per allowed request.
* On each request:
  1. Delete expired rows: `DELETE WHERE key_id = ? AND request_time < now - windowMs` (keeps table lean).
  2. Count rows in window: `SELECT COUNT(*) WHERE key_id = ? AND request_time >= windowStart`.
  3. If count >= capacity → deny.
  4. Else insert a new row and return allowed.
* Composite index on `(key_id, request_time)` makes count and delete queries O(log n).
* All three steps run inside a single `@Transactional` method.

### 2.4 Persistence Layer (MySQL)

**Tables (actually implemented):**

1. `throttlex_usage` — token-bucket state (`key_id`, `tokens`, `last_refill`)
2. `throttlex_sw_log` — sliding-window request log (`key_id`, `request_time`)
3. `throttlex_policy` — per-key algorithm / capacity config

**throttlex_usage (DDL)**

```sql
CREATE TABLE throttlex_usage (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  key_id      VARCHAR(255) NOT NULL,
  tokens      BIGINT       NOT NULL,
  last_refill BIGINT       NOT NULL,
  UNIQUE INDEX idx_usage_key_id    (key_id),
         INDEX idx_usage_last_refill (last_refill)
) ENGINE=InnoDB;
```

**throttlex_sw_log (DDL)**

```sql
CREATE TABLE throttlex_sw_log (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  key_id       VARCHAR(255) NOT NULL,
  request_time BIGINT       NOT NULL,
  INDEX idx_sw_key_time (key_id, request_time)
) ENGINE=InnoDB;
```

**throttlex_policy (DDL)**

```sql
CREATE TABLE throttlex_policy (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  policy_key     VARCHAR(255) NOT NULL,
  type           VARCHAR(50)  NOT NULL,  -- TOKEN_BUCKET | SLIDING_WINDOW
  capacity       BIGINT       NOT NULL,
  refill_rate    BIGINT       NOT NULL,
  window_seconds BIGINT       NOT NULL,
  UNIQUE INDEX idx_policy_key (policy_key)
) ENGINE=InnoDB;
```

### 2.5 Admin APIs

| Method | Path | Description |
|---|---|---|
| GET | `/admin/status` | Service health + version |
| GET | `/admin/metrics` | All-key usage stats |
| GET | `/admin/metrics/{key}` | Per-key token/window metrics |
| POST | `/admin/reset/{key}` | Reset counters for a key |
| GET | `/admin/policies` | List all configured policies |
| GET | `/admin/policies/{key}` | Get policy by key |
| POST | `/admin/policies` | Create a new rate-limit policy |
| PUT | `/admin/policies/{key}` | Update existing policy |
| DELETE | `/admin/policies/{key}` | Delete a policy |

All error responses follow a standardized schema: `{ status, error, message, timestamp }`.

**Auth:** admin endpoints should be protected via API key or OAuth; enforce IP allowlist (future work).

---

## 3. Data Model (Detailed)

**UsageRecord** (`throttlex_usage`) — token-bucket live state

```java
@Entity @Table(name = "throttlex_usage", indexes = {
  @Index(name = "idx_usage_key_id",     columnList = "key_id",     unique = true),
  @Index(name = "idx_usage_last_refill", columnList = "last_refill")
})
public class UsageRecord {
  private Long   id;
  private String keyId;
  private long   tokens;     // remaining tokens
  private long   lastRefill; // epoch millis
}
```

**SlidingWindowRecord** (`throttlex_sw_log`) — one row per allowed request

```java
@Entity @Table(name = "throttlex_sw_log", indexes = {
  @Index(name = "idx_sw_key_time", columnList = "key_id, request_time")
})
public class SlidingWindowRecord {
  private Long   id;
  private String keyId;
  private long   requestTime; // epoch millis
}
```

**PolicyEntity** (`throttlex_policy`) — persisted rate-limit configuration

```java
@Entity @Table(name = "throttlex_policy", indexes = {
  @Index(name = "idx_policy_key", columnList = "policy_key", unique = true)
})
public class PolicyEntity {
  private Long            id;
  private String          policyKey;
  private Policy.PolicyType type;      // TOKEN_BUCKET | SLIDING_WINDOW
  private long            capacity;
  private long            refillRate;    // tokens/sec (token-bucket)
  private long            windowSeconds; // window size (sliding-window)
}
```

**Policy** (domain object, not persisted) — passed to limiters

```java
public class Policy {
  private String          key;
  private PolicyType      type;
  private long            capacity;
  private long            refillRate;
  private long            windowSeconds;
}
```

---

## 4. API Specification (Detailed)

### Runtime (applied per incoming request)

* Key extraction order: `X-Forwarded-For` header → `request.getRemoteAddr()`.
* Policy lookup: `PolicyRepository.findByPolicyKey(key)` → fall back to defaults (`capacity=100`, `refillRate=10`, `windowSeconds=60`, `TOKEN_BUCKET`).

### Admin APIs — Request / Response Examples

**POST /admin/policies** (create)

```json
// Request
{ "key": "user-123", "type": "TOKEN_BUCKET", "capacity": 100, "refillRate": 10, "windowSeconds": 60 }
// Response 201
{ "id": 1, "policyKey": "user-123", "type": "TOKEN_BUCKET", "capacity": 100, "refillRate": 10, "windowSeconds": 60 }
```

**GET /admin/metrics/{key}** returns:

```json
{ "key": "user-123", "algorithm": "TOKEN_BUCKET", "currentTokens": 87, "capacity": 100,
  "windowRequestCount": 13, "windowSeconds": 60, "status": "OK" }
```

**Error response (all errors)**:

```json
{ "status": 429, "error": "Too Many Requests", "message": "Rate limit exceeded for key: user-123", "timestamp": "2026-02-22T06:55:00Z" }
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

### Sliding Window (MySQL — implemented)

* All three steps (delete expired → count → insert) execute inside a single `@Transactional` method.
* `deleteExpired` runs first so the count query only scans live rows.
* The composite index `(key_id, request_time)` in `throttlex_sw_log` makes DELETE + COUNT efficient.
* Check-before-insert prevents over-counting: count is read before inserting the new row.

**Redis variant** (future upgrade path):

* Lua script: ZREMRANGEBYSCORE + ZADD + ZCARD — fully atomic in Redis, better for ultra-low latency.

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

# Appendix: Key Code Snippets (actual implementation)

**ThrottleXService.check() — policy resolution + routing**

```java
@Transactional
public boolean check(String key) {
    Policy policy = policyRepository.findByPolicyKey(key)
            .map(policyService::toPolicy)
            .orElseGet(() -> Policy.builder()
                    .type(Policy.PolicyType.TOKEN_BUCKET)
                    .capacity(100).refillRate(10).windowSeconds(60).build());

    UsageRecord record = usageRepository.findByKeyId(key)
            .orElseGet(() -> createAndSave(key, policy));

    boolean allowed = limiterFactory.allow(policy.getType().name(), record, policy);
    usageRepository.save(record);
    return allowed;
}
```

**SlidingWindowLimiter.allow() — MySQL log approach**

```java
@Transactional
public boolean allow(UsageRecord record, Policy policy) {
    long now = System.currentTimeMillis();
    long windowStart = now - policy.getWindowSeconds() * 1000L;

    slidingWindowRepository.deleteExpired(record.getKeyId(), windowStart);
    long count = slidingWindowRepository.countRequestsInWindow(record.getKeyId(), windowStart);

    if (count >= policy.getCapacity()) return false;

    slidingWindowRepository.save(SlidingWindowRecord.builder()
            .keyId(record.getKeyId()).requestTime(now).build());
    return true;
}
```

**UsageRepository — atomic decrement**

```java
@Modifying
@Query("UPDATE UsageRecord r SET r.tokens = r.tokens - 1 WHERE r.keyId = :keyId AND r.tokens > 0")
int decrementToken(@Param("keyId") String keyId);
```

**Redis Sliding Window (future upgrade — Lua)**

```lua
-- KEYS[1]=key, ARGV[1]=now, ARGV[2]=windowSec, ARGV[3]=max
redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1]-ARGV[2])
redis.call('ZADD', KEYS[1], ARGV[1], ARGV[1])
local cnt = redis.call('ZCARD', KEYS[1])
redis.call('EXPIRE', KEYS[1], ARGV[2])
return cnt
```

---

*Document author: Ashutosh Jha*
