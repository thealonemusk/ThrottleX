# ThrottleX — Build & Test Commands

> **Prerequisite**: Java 17+ and Maven 3.9+. Your current JDK is Java 11; install JDK 17 from
> https://adoptium.net/ and set `JAVA_HOME` before running local Maven commands.

---

## Local Development (Maven)

### Compile
```bash
mvn compile
```

### Run all tests
```bash
mvn test
```

### Run a specific test class
```bash
mvn test -Dtest=TokenBucketLimiterTest
mvn test -Dtest=SlidingWindowLimiterTest
```

### Package (build fat JAR, skip tests)
```bash
mvn package -DskipTests
```

### Package (build fat JAR, run tests)
```bash
mvn package
```

### Run the application locally
```bash
mvn spring-boot:run
```

### Clean build artifacts
```bash
mvn clean
```

### Clean + package in one shot
```bash
mvn clean package -DskipTests
```

---

## Docker

### Start everything (MySQL + app) — first time or after code changes
```bash
docker compose up --build
```

### Start in detached mode
```bash
docker compose up --build -d
```

### Stop all containers
```bash
docker compose down
```

### Stop and remove volumes (wipe MySQL data)
```bash
docker compose down -v
```

### Rebuild only the app image
```bash
docker compose build throttlex-app
```

### View live logs
```bash
docker compose logs -f throttlex-app
```

### Check container health status
```bash
docker compose ps
```

---

## Admin API — Quick Test Commands (curl)

### Health check
```bash
curl http://localhost:8080/admin/status
```

### View all metrics
```bash
curl http://localhost:8080/admin/metrics
```

### View metrics for a specific key
```bash
curl http://localhost:8080/admin/metrics/192.168.1.1
```

### Reset counters for a key
```bash
curl -X POST http://localhost:8080/admin/reset/192.168.1.1
```

### List all policies
```bash
curl http://localhost:8080/admin/policies
```

### Create a Token Bucket policy
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

### Create a Sliding Window policy
```bash
curl -X POST http://localhost:8080/admin/policies \
  -H "Content-Type: application/json" \
  -d '{
    "key": "api-route-/search",
    "type": "SLIDING_WINDOW",
    "capacity": 50,
    "refillRate": 0,
    "windowSeconds": 30
  }'
```

### Update a policy
```bash
curl -X PUT http://localhost:8080/admin/policies/user-123 \
  -H "Content-Type: application/json" \
  -d '{
    "type": "TOKEN_BUCKET",
    "capacity": 200,
    "refillRate": 20,
    "windowSeconds": 60
  }'
```

### Delete a policy
```bash
curl -X DELETE http://localhost:8080/admin/policies/user-123
```

---
