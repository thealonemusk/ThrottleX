# ThrottleX — Rate Limiting Test Guide

ThrottleX is a plug-in rate-limiting service. Every incoming HTTP request is intercepted by `ThrottleXFilter`, which extracts the client key (IP address) and enforces the applicable policy.

---

## Prerequisites

| Requirement | Details |
|---|---|
| Java 11+ | Must be on PATH |
| Maven | `mvn` on PATH |
| MySQL 8 | Via Docker (see below) or locally on port `3306` |
| Docker (optional) | For the all-in-one compose setup |

---

## 1. Start the Service

### Option A — Docker (recommended)
```bash
docker-compose up --build
```
Both MySQL and the Spring Boot app start together. App is ready when you see:
```
Started ThrottleXApplication in X seconds
```

### Option B — Local (MySQL must already be running)
```bash
mvn spring-boot:run
```

### Verify startup
```powershell
Invoke-RestMethod http://localhost:8080/admin/status
```
**Expected:**
```json
{ "service": "ThrottleX", "status": "UP", "version": "1.0.0" }
```

---

## 2. Detect Your Client Key

> ⚠️ On Windows, `localhost` resolves to the **IPv6 loopback** `0:0:0:0:0:0:0:1`, not `127.0.0.1`.
> ThrottleX extracts the key from `X-Forwarded-For` or `RemoteAddr`, so you must check which key is actually being recorded.

```powershell
Invoke-RestMethod http://localhost:8080/admin/metrics | Select-Object key, currentTokens, capacity, status
```

Note the value in the `key` column — use **that exact string** in all policy commands below.

---

## 3. Create a Rate Limit Policy

Set a policy with a **low capacity** (e.g. 5) so you can hit the limit quickly.

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/admin/policies" `
  -Method Post `
  -ContentType "application/json" `
  -Body '{"key":"0:0:0:0:0:0:0:1","type":"TOKEN_BUCKET","capacity":5,"refillRate":1,"windowSeconds":60}'
```

| Field | Meaning |
|---|---|
| `key` | Client identifier (IP address) |
| `type` | `TOKEN_BUCKET` or `SLIDING_WINDOW` |
| `capacity` | Max requests allowed |
| `refillRate` | Tokens added per second (Token Bucket only) |
| `windowSeconds` | Time window in seconds (Sliding Window only) |

> If a policy already exists for that key, use `PUT` to update it instead:
> ```powershell
> Invoke-RestMethod -Uri "http://localhost:8080/admin/policies/0:0:0:0:0:0:0:1" `
>   -Method Put -ContentType "application/json" `
>   -Body '{"key":"0:0:0:0:0:0:0:1","type":"TOKEN_BUCKET","capacity":5,"refillRate":1,"windowSeconds":60}'
> ```

---

## 4. Reset the Key (Clean Slate)

Always reset before a test run to clear residual token counts from previous requests:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/admin/reset/0:0:0:0:0:0:0:1" -Method Post
```

---

## 5. Burst Test — Verify the 429

Fire 7 requests in quick succession. With `capacity=5`, requests 6 and 7 should be blocked.

```powershell
1..7 | ForEach-Object {
    $n = $_
    try {
        $r = Invoke-WebRequest "http://localhost:8080/admin/status" -UseBasicParsing
        Write-Host "Request ${n}: $($r.StatusCode)"
    } catch {
        Write-Host "Request ${n}: $($_.Exception.Response.StatusCode.value__)"
    }
}
```

**Expected output:**
```
Request 1: 200
Request 2: 200
Request 3: 200
Request 4: 200
Request 5: 200
Request 6: 429  ← Rate limit enforced
Request 7: 429
```

The `429` response body will contain: `Too Many Requests (ThrottleX)`

---

## 6. Inspect Metrics

Check the live token count after the burst test:

```powershell
# All keys
Invoke-RestMethod http://localhost:8080/admin/metrics

# Specific key
Invoke-RestMethod "http://localhost:8080/admin/metrics/0:0:0:0:0:0:0:1"
```

**Example response:**
```json
{
  "key": "0:0:0:0:0:0:0:1",
  "algorithm": "TOKEN_BUCKET",
  "currentTokens": 0,
  "capacity": 5,
  "windowSeconds": 60,
  "windowRequestCount": 5,
  "status": "THROTTLED"
}
```

---

## 7. Test Sliding Window Algorithm

```powershell
# Update the policy to SLIDING_WINDOW
Invoke-RestMethod -Uri "http://localhost:8080/admin/policies/0:0:0:0:0:0:0:1" `
  -Method Put -ContentType "application/json" `
  -Body '{"key":"0:0:0:0:0:0:0:1","type":"SLIDING_WINDOW","capacity":3,"refillRate":0,"windowSeconds":30}'

# Reset counter
Invoke-RestMethod -Uri "http://localhost:8080/admin/reset/0:0:0:0:0:0:0:1" -Method Post

# Burst test — expect 429 from request 4 onwards
1..5 | ForEach-Object {
    $n = $_
    try {
        $r = Invoke-WebRequest "http://localhost:8080/admin/status" -UseBasicParsing
        Write-Host "Request ${n}: $($r.StatusCode)"
    } catch {
        Write-Host "Request ${n}: $($_.Exception.Response.StatusCode.value__)"
    }
}
```

---

## 8. Test Multi-Client Isolation

Use `X-Forwarded-For` to simulate a different client. It should have its own independent counter:

```powershell
Invoke-WebRequest "http://localhost:8080/admin/status" `
  -Headers @{"X-Forwarded-For"="10.0.0.1"} -UseBasicParsing | Select-Object StatusCode
```

Even when `0:0:0:0:0:0:0:1` is throttled, `10.0.0.1` should return `200`.

---

## 9. Admin API Reference

| Purpose | Method | Endpoint |
|---|---|---|
| Health / status | `GET` | `/admin/status` |
| List all policies | `GET` | `/admin/policies` |
| Get policy by key | `GET` | `/admin/policies/{key}` |
| Create policy | `POST` | `/admin/policies` |
| Update policy | `PUT` | `/admin/policies/{key}` |
| Delete policy | `DELETE` | `/admin/policies/{key}` |
| All metrics | `GET` | `/admin/metrics` |
| Metrics by key | `GET` | `/admin/metrics/{key}` |
| Reset key counter | `POST` | `/admin/reset/{key}` |

---

## 10. Default Behaviour (No Policy Configured)

If no policy exists for a key, ThrottleX falls back to:

| Setting | Default Value |
|---|---|
| Algorithm | `TOKEN_BUCKET` |
| Capacity | `100` |
| Refill Rate | `10` tokens/sec |
| Window | `60` seconds |

To trigger the default limit without a custom policy, you would need to send **100+ rapid requests**.
