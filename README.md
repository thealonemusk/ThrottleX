# ThrottleX 🚦

> A production-ready, pluggable rate-limiting engine built with Spring Boot + MySQL.
> Inspired by API gateways like Kong, Envoy, and NGINX.

---

## Features

| Feature | Details |
|---|---|
| **Token Bucket** | Drift-safe refill, pessimistic DB lock, atomic decrement |
| **Sliding Window** | MySQL request-log table, composite index, transactional |
| **Per-key policies** | Store per-IP / per-user / per-route config via Admin API |
| **MySQL-backed** | JPA with HikariCP pool (20 max connections) |
| **Embeddable middleware** | Drop-in `OncePerRequestFilter` for any Spring Boot app |
| **Admin REST API** | 9 endpoints — policy CRUD, metrics, counter reset |
| **Global exception handling** | Standardized `{ status, error, message, timestamp }` responses |
| **Dockerized** | Multi-stage image + health checks on both app and MySQL |

---

## Tech Stack

- **Java 17** · Spring Boot 3.2
- **MySQL 8** · Spring Data JPA · HikariCP
- **Lombok** · Caffeine Cache
- **Docker / Docker Compose**
- **Maven**

---

## Project Structure

```
src/main/java/com/throttlex/
├── ThrottleXApplication.java
├── config/
│   ├── ThrottleXConfig.java        # Filter registration
│   └── ThrottleXProperties.java    # @ConfigurationProperties
├── controller/
│   └── AdminController.java        # 9 admin REST endpoints
├── dto/
│   ├── ErrorResponse.java
│   ├── MetricsResponse.java
│   └── PolicyRequest.java
├── exception/
│   ├── GlobalExceptionHandler.java # @RestControllerAdvice
│   ├── PolicyNotFoundException.java
│   └── RateLimitExceededException.java
├── limiter/
│   ├── Limiter.java                # Interface
│   ├── LimiterFactory.java         # Routes to correct algorithm
│   ├── TokenBucketLimiter.java
│   └── SlidingWindowLimiter.java
├── middleware/
│   └── ThrottleXFilter.java        # OncePerRequestFilter
├── model/
│   ├── Policy.java                 # Domain object
│   ├── PolicyEntity.java           # JPA → throttlex_policy
│   ├── UsageRecord.java            # JPA → throttlex_usage
│   └── SlidingWindowRecord.java    # JPA → throttlex_sw_log
├── persistence/
│   ├── PolicyRepository.java
│   ├── SlidingWindowRepository.java
│   └── UsageRepository.java
└── service/
    ├── PolicyService.java          # Policy CRUD
    └── ThrottleXService.java       # Core check + key extraction
```

---

## MySQL Schema

Three tables are auto-created by JPA (`ddl-auto: update`):

```sql
-- Token-bucket live state
throttlex_usage        → idx_usage_key_id (unique), idx_usage_last_refill

-- Sliding-window request log (one row per allowed request)
throttlex_sw_log       → idx_sw_key_time (key_id, request_time)

-- Per-key rate-limit configuration
throttlex_policy       → idx_policy_key (unique)
```

---

## Admin API

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/admin/status` | Health + version |
| `GET` | `/admin/metrics` | All-key usage stats |
| `GET` | `/admin/metrics/{key}` | Per-key token/window metrics |
| `POST` | `/admin/reset/{key}` | Reset counters for a key |
| `GET` | `/admin/policies` | List all policies |
| `GET` | `/admin/policies/{key}` | Get policy by key |
| `POST` | `/admin/policies` | Create policy |
| `PUT` | `/admin/policies/{key}` | Update policy |
| `DELETE` | `/admin/policies/{key}` | Delete policy |

**Create a policy (example):**

```bash
curl -X POST http://localhost:8080/admin/policies \
  -H "Content-Type: application/json" \
  -d '{
    "key": "user-123",
    "type": "TOKEN_BUCKET",
    "capacity": 100,
    "refillRate": 10,
    "windowSeconds": 60
  }'
```

---

## Running with Docker

```bash
# Start MySQL + app (builds image automatically)
docker compose up --build

# Verify app is healthy
curl http://localhost:8080/admin/status
```

Both containers have health checks configured. The app waits for MySQL to be healthy before starting.

---

## Running Locally

> Requires Java 17+ and a running MySQL instance.

```bash
# 1. Start MySQL via Docker
docker compose up mysql -d

# 2. Run the app
mvn spring-boot:run
```

Default datasource config (`application.yml`):
- URL: `jdbc:mysql://localhost:3306/throttlex`
- Username: `root` / Password: `password`

---

## Configuration

All defaults are in `application.yml` and can be overridden via environment variables:

| Property | Default | Description |
|---|---|---|
| `throttlex.default-capacity` | `100` | Tokens per bucket (no policy configured) |
| `throttlex.default-refill-rate` | `10` | Tokens refilled per second |
| `throttlex.default-window-seconds` | `60` | Sliding window size in seconds |

---

## Testing

```bash
mvn test                                      # All tests
mvn test -Dtest=TokenBucketLimiterTest        # Token bucket unit test
mvn test -Dtest=SlidingWindowLimiterTest      # Sliding window unit test
```

---

## Build

```bash
mvn clean package -DskipTests    # Build fat JAR
docker compose up --build        # Build + run in Docker
```

See [`COMMANDS.md`](COMMANDS.md) for the full reference of build, test, and API commands.
