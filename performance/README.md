# Performance and Load Testing

This document describes the load-test setup, execution commands, metrics, and verified local benchmark results for the Reservation Platform.

The performance suite validates:

- public `GET /api/slots` reads over a dataset containing more than one million slot records
- Redis-backed first-page reads
- deep cursor/keyset pagination against MySQL
- concurrent browse, reserve, and cancel flows
- expected reservation conflicts under contention
- cache invalidation behavior during write operations
- reservation and cancellation correctness under concurrent load

> The numbers in this document were collected in a local Docker-based environment. They demonstrate the behavior of the tested implementation and are not production capacity guarantees.

---

## API authentication model

`GET /api/slots` is intentionally public.

The read-only k6 scenarios:

- do not call the login endpoint
- do not generate or extract a JWT
- do not send an `Authorization` header
- bypass JWT parsing in `JwtAuthenticationFilter`

The protected operations remain authenticated:

```http
POST /api/reservations
DELETE /api/reservations/{id}
GET /api/auth/me
```

The mixed scenarios still authenticate deterministic performance users because their reserve and cancel steps require JWT authentication.

OpenAPI/Swagger represents `GET /api/slots` with an empty security requirement and does not advertise a `401` response for that operation. Global bearer authentication remains available for protected endpoints.

---

## Test assets

```text
performance/
├── README.md
├── scripts/
│   ├── slots.js
│   └── reservation-flow.js
├── runners/
│   ├── run-slots-first.sh
│   ├── run-slots-deep.sh
│   ├── run-mixed-hotspot.sh
│   └── run-mixed-distributed.sh
└── results/
    └── .gitkeep
```

Generated JSON, ZIP, log, and temporary result files belong under:

```text
performance/results/
```

They are intentionally excluded from Git.

---

# Environment and dataset

## Verified dataset

```text
10,000 users
1,200,000 slots
360,000 seeded reservations
840,000 initially available slots
```

## Required tools

- Java 21
- Docker and Docker Compose
- MySQL
- Redis
- k6
- Maven
- Reservation Service running on port `8080`

Check the environment:

```bash
java -version
docker --version
k6 version
mvn -version
```

## Start the shared infrastructure

```bash
docker compose up -d reservation-mysql reservation-redis
docker compose ps
```

The application and performance runners use:

```text
Database: reservation_db
MySQL container: reservation-mysql
MySQL host port: 3306
Redis container: reservation-redis
Application port: 8080
```

MySQL data persists in the `reservation_mysql_data` Docker volume.

Do not run the following command unless deleting the persistent benchmark dataset is intentional:

```bash
docker compose down -v
```

## First-time or repair seeding

Performance seeding is disabled by default. Enable it only when initially creating the dataset or safely repairing an incomplete deterministic dataset:

```bash
APP_PERFORMANCE_SEEDING_ENABLED=true \
mvn -pl reservation-service spring-boot:run
```

The seeder is duplicate-safe and skips an already complete dataset. It must not be enabled for normal benchmark runs.

## Start the application without rerunning the seeder

```bash
APP_PERFORMANCE_SEEDING_ENABLED=false \
mvn -pl reservation-service spring-boot:run
```

The default application address is:

```text
http://127.0.0.1:8080
```

---

# Preflight validation

Run from the repository root:

```bash
node --check performance/scripts/slots.js
node --check performance/scripts/reservation-flow.js

k6 inspect performance/scripts/slots.js
k6 inspect performance/scripts/reservation-flow.js

bash -n performance/runners/run-slots-first.sh
bash -n performance/runners/run-slots-deep.sh
bash -n performance/runners/run-mixed-hotspot.sh
bash -n performance/runners/run-mixed-distributed.sh
```

Ensure runner files are executable:

```bash
chmod +x performance/runners/*.sh
```

Verify the application through the public API:

```bash
curl -fsS   "http://127.0.0.1:8080/api/slots?from=2026-06-01T00:00:00&to=2026-07-01T00:00:00&limit=1"
```

Verify Redis:

```bash
docker exec reservation-redis redis-cli PING
```

Expected response:

```text
PONG
```

## Verify the public/protected boundary

A public slot request without a token must return `200`:

```bash
curl -i \
  "http://127.0.0.1:8080/api/slots?from=2026-06-01T00:00:00&to=2026-07-01T00:00:00&limit=20"
```

The JWT filter also skips the exact public operation when an invalid bearer token is present, so this must still return `200`:

```bash
curl -i \
  "http://127.0.0.1:8080/api/slots?from=2026-06-01T00:00:00&to=2026-07-01T00:00:00&limit=20" \
  -H "Authorization: Bearer invalid-token"
```

A protected endpoint without a token must return `401`:

```bash
curl -i http://127.0.0.1:8080/api/auth/me
```

---

# Test configuration

## Read-only date range

The first-page and deep-cursor tests use:

```text
FROM=2026-06-01T00:00:00
TO=2026-07-01T00:00:00
LIMIT=100
```

This is a 30-day range.

## Mixed-workload date range

The hotspot and distributed tests use:

```text
FROM=2026-08-01T00:00:00
TO=2026-08-31T00:00:00
LIMIT=10
```

The API accepts a maximum range of 30 days. Do not use `2026-08-01` through `2026-09-01`, because that interval spans 31 days.

## Mixed-workload users

```text
USERNAME_PREFIX=perf-user-
USERNAME_WIDTH=5
USER_PASSWORD=TestPassword123
```

Examples:

```text
perf-user-00001
perf-user-00002
perf-user-00003
```

The values can be overridden through environment variables.

---

# Running the tests

Each runner writes one JSON file per VU level under:

```text
performance/results/
```

The standard VU levels are:

```text
20, 50, 100, 200
```

If no VU argument is provided, the runners use these standard levels.

---

## 1. First-page public read test

This scenario calls:

```http
GET /api/slots?from=...&to=...&limit=100
```

without a cursor and without authentication.

Expected path:

```text
public request without cursor
→ JWT filter bypass
→ Redis per-day head cache
→ return at most 100 items
→ use one extra internal item to determine hasNext
```

Run all standard levels:

```bash
FROM=2026-06-01T00:00:00 \
TO=2026-07-01T00:00:00 \
LIMIT=100 \
TEST_DURATION=60s \
./performance/runners/run-slots-first.sh 20 50 100 200
```

Run selected levels:

```bash
./performance/runners/run-slots-first.sh 100 200
```

Generated files:

```text
results-first-vus-20.json
results-first-vus-50.json
results-first-vus-100.json
results-first-vus-200.json
```

---

## 2. Deep-cursor public read test

This scenario calls public `GET /api/slots` with a cursor and without authentication.

Expected path:

```text
public request with cursor
→ JWT filter bypass
→ Redis head-cache bypass
→ indexed MySQL keyset query
→ ORDER BY start_time, id
→ fetch limit + 1
```

Verified cursor:

```text
eyJzdGFydFRpbWUiOiIyMDI2LTA2LTI4VDIzOjU4OjAwIiwiaWQiOjc4MzM1OX0
```

Run all standard levels:

```bash
FROM=2026-06-01T00:00:00 \
TO=2026-07-01T00:00:00 \
LIMIT=100 \
DEEP_CURSOR=eyJzdGFydFRpbWUiOiIyMDI2LTA2LTI4VDIzOjU4OjAwIiwiaWQiOjc4MzM1OX0 \
TEST_DURATION=60s \
./performance/runners/run-slots-deep.sh 20 50 100 200
```

Generated files:

```text
results-deep-vus-20.json
results-deep-vus-50.json
results-deep-vus-100.json
results-deep-vus-200.json
```

Before each read-only level, the runner clears only these confirmed slot-cache patterns:

```text
slots:version:*
slots:head:*
slots:head-lock:*
```

The deep runner also verifies that cursor-only requests do not populate `slots:head:*` or `slots:head-lock:*`.

---

## 3. Mixed distributed test

Business distribution:

```text
80% browse only
15% browse and reserve
5% browse, reserve, and cancel
```

VUs rotate across separate daily windows. This keeps requests concurrent while reducing artificial contention on one small slot set.

Run:

```bash
ALLOW_PERFORMANCE_DATA_RESET=true \
FROM=2026-08-01T00:00:00 \
TO=2026-08-31T00:00:00 \
LIMIT=10 \
TEST_DURATION=60s \
BROWSE_RATE=80 \
RESERVE_RATE=15 \
CANCEL_RATE=5 \
USER_PASSWORD=TestPassword123 \
./performance/runners/run-mixed-distributed.sh 20 50 100 200
```

Generated files include the workload, idempotency mode, and VU level:

```text
mixed-distributed-idempotency-off-vu100.json
mixed-distributed-idempotency-unique-vu100.json
mixed-distributed-idempotency-retry-rate-0p10-vu100.json
```

When a threshold fails, the runner preserves the previous successful result and writes:

```text
mixed-distributed-idempotency-off-vu200-threshold-failed.json
```

---

## 4. Mixed hotspot test

The business ratios are the same as the distributed scenario, but all VUs compete over the same broad range and the same first-page slot set.

This scenario intentionally creates:

- high reservation contention
- expected HTTP `409` conflicts
- repeated invalidation of the same daily cache
- a worst-case hotspot for concurrency and cache rebuild behavior

Run:

```bash
ALLOW_PERFORMANCE_DATA_RESET=true \
FROM=2026-08-01T00:00:00 \
TO=2026-08-31T00:00:00 \
LIMIT=10 \
TEST_DURATION=60s \
BROWSE_RATE=80 \
RESERVE_RATE=15 \
CANCEL_RATE=5 \
USER_PASSWORD=TestPassword123 \
./performance/runners/run-mixed-hotspot.sh 20 50 100 200
```

Generated files include the workload, idempotency mode, and VU level:

```text
mixed-hotspot-idempotency-off-vu100.json
mixed-hotspot-idempotency-unique-vu100.json
mixed-hotspot-idempotency-retry-rate-0p10-vu100.json
```

When a threshold fails, the runner preserves the previous successful result and writes:

```text
mixed-hotspot-idempotency-off-vu200-threshold-failed.json
```

## Mixed-runner cleanup behavior

Mixed runners modify database rows, so they refuse to start unless:

```text
ALLOW_PERFORMANCE_DATA_RESET=true
```

Before each VU level, cleanup:

1. selects only reservations owned by users matching `USERNAME_PREFIX`
2. limits selection to slots inside `FROM`/`TO`
3. excludes deterministic seeded-baseline reservations
4. deletes only selected performance reservations
5. restores only their slots when no reservation remains
6. clears only the three slot-cache Redis patterns documented above

Seeded baseline reservations and non-performance user data are preserved.

Redis keys are collected with `SCAN` and removed in configurable `UNLINK` batches instead of starting one Docker process per key:

```text
REDIS_UNLINK_BATCH_SIZE=500
```

Every k6 run first writes to a temporary file. A successful result is moved to the normal filename. Exit code `99` is treated as a threshold failure and stored using the `-threshold-failed.json` suffix, while a previous successful result is preserved.

Use the optional final cleanup when required:

```bash
CLEANUP_AFTER=true \
ALLOW_PERFORMANCE_DATA_RESET=true \
./performance/runners/run-mixed-hotspot.sh 200
```

---

# Metrics and thresholds

## Read-only thresholds

```text
Page duration P95 < 150 ms
Page duration P99 < 200 ms
Page error rate < 1%
HTTP failure rate < 1%
```

## Mixed-workload thresholds

```text
Browse P95 < 250 ms
Browse P99 < 500 ms
Reservation P95 < 500 ms
Reservation P99 < 750 ms
Cancellation P95 < 500 ms
Cancellation P99 < 750 ms
Journey P95 < 1000 ms
Journey P99 < 1500 ms
Technical error rates < 1%
```

## Expected conflicts

A supported reservation response with:

```text
HTTP 409
```

is an expected business conflict when another concurrent user has already reserved the selected slot.

Expected conflicts are tracked separately and are not technical failures.

## VU behavior

Within one VU, a mixed journey is sequential:

```text
Browse → optional Reserve → optional Cancel
```

Different VUs execute concurrently. `200 VUs` does not mean exactly `200 requests per second`; the request rate depends on latency, think time, and the selected business path.

---

# Verified benchmark results

Benchmark date:

```text
2026-07-25
```

The current reviewed result package contains 18 JSON exports:

```text
6 read-only results:
- first page: 50, 100, and 200 VUs
- deep cursor: 50, 100, and 200 VUs

12 mixed results:
- distributed and hotspot
- idempotency off, unique, and retry
- 100 and 200 VUs
```

All read-only results passed their configured thresholds. All distributed mixed results passed. Every hotspot result at 100 VUs passed. All three hotspot variants at 200 VUs crossed only the browse latency thresholds.

## First-page public read

| VU | Avg | P95 | P99 | Max | Page req/s | Errors |
|---:|---:|---:|---:|---:|---:|---:|
| 50 | 10.87 ms | 21.54 ms | 40.15 ms | 107.26 ms | 372.22 | 0% |
| 100 | 8.04 ms | 17.56 ms | 23.33 ms | 41.34 ms | 765.80 | 0% |
| 200 | 6.56 ms | 14.85 ms | 24.98 ms | 54.40 ms | 1,552.97 | 0% |

Result:

```text
P95 < 150 ms: passed at every tested level
P99 < 200 ms: passed at every tested level
HTTP failures: 0
Response/check failures: 0
```

The first-page Redis path remained stable and fast at 200 VUs.

## Deep-cursor public read

| VU | Avg | P95 | P99 | Max | Page req/s | Errors |
|---:|---:|---:|---:|---:|---:|---:|
| 50 | 12.58 ms | 19.04 ms | 25.60 ms | 65.34 ms | 368.12 | 0% |
| 100 | 11.17 ms | 18.33 ms | 30.66 ms | 133.36 ms | 746.68 | 0% |
| 200 | 54.03 ms | 98.01 ms | 142.38 ms | 369.32 ms | 1,077.45 | 0% |

Result:

```text
P95 < 150 ms: passed at every tested level
P99 < 200 ms: passed at every tested level
HTTP failures: 0
Response/check failures: 0
```

The `200 VU` maximum reached `369.32 ms`. Therefore, the results support the configured percentile targets but do not prove that every individual request always remains below `200 ms`. Adding a strict `max<200` threshold would make this specific run fail.

## Mixed distributed

### Idempotency off

| VU | HTTP req/s | Browse P95 | Browse P99 | Reserve P95 | Cancel P95 | Conflict rate |
|---:|---:|---:|---:|---:|---:|---:|
| 100 | 991.73 | 16.30 ms | 36.50 ms | 16.85 ms | 14.17 ms | 0.53% |
| 200 | 1,533.56 | 49.92 ms | 103.67 ms | 49.75 ms | 49.94 ms | 2.61% |

### Unique idempotency keys

| VU | HTTP req/s | Browse P95 | Browse P99 | Reserve P95 | Cancel P95 | Conflict rate |
|---:|---:|---:|---:|---:|---:|---:|
| 100 | 992.10 | 13.04 ms | 33.61 ms | 19.09 ms | 14.02 ms | 0.51% |
| 200 | 1,456.25 | 68.98 ms | 115.20 ms | 62.78 ms | 59.49 ms | 4.05% |

### Retry idempotency mode

| VU | HTTP req/s | Browse P95 | Browse P99 | Reserve P95 | Cancel P95 | Conflict rate |
|---:|---:|---:|---:|---:|---:|---:|
| 100 | 993.41 | 20.19 ms | 38.83 ms | 24.84 ms | 17.15 ms | 0.72% |
| 200 | 1,181.44 | 158.16 ms | 224.95 ms | 148.17 ms | 137.13 ms | 5.82% |

All configured distributed thresholds passed. Across all six runs:

```text
HTTP failure rate: 0%
Browse technical error rate: 0%
Reservation technical error rate: 0%
Cancellation error rate: 0%
Failed response checks: 0
All attempted cancellations succeeded
```

The non-idempotent 200-VU distributed run reached approximately `1,534 HTTP req/s`. The unique-key mode remained close to that throughput. Retry mode performs extra replay requests and showed higher latency at 200 VUs, while still passing the mixed-workload thresholds.

## Idempotency replay correctness

| Workload | VU | Attempts | Successful | Failed | Mismatches | Replay P95 | Replay P99 |
|---|---:|---:|---:|---:|---:|---:|---:|
| Distributed | 100 | 1,078 | 1,078 | 0 | 0 | 10.94 ms | 21.13 ms |
| Distributed | 200 | 1,432 | 1,432 | 0 | 0 | 131.60 ms | 202.60 ms |
| Hotspot | 100 | 395 | 395 | 0 | 0 | 68.66 ms | 88.37 ms |
| Hotspot | 200 | 183 | 183 | 0 | 0 | 276.15 ms | 485.80 ms |

Every replay returned the expected original response snapshot. No replay failure, response mismatch, or cleanup failure was recorded.

## Mixed hotspot

### 100 VUs

| Idempotency | HTTP req/s | Browse P95 | Browse P99 | Reserve P95 | Cancel P95 | Conflict rate |
|---|---:|---:|---:|---:|---:|---:|
| Off | 496.81 | 181.66 ms | 221.37 ms | 90.91 ms | 88.51 ms | 35.21% |
| Unique | 515.80 | 175.78 ms | 220.95 ms | 91.64 ms | 83.13 ms | 36.07% |
| Retry | 534.74 | 168.27 ms | 194.34 ms | 84.30 ms | 78.00 ms | 35.97% |

All configured thresholds passed at 100 VUs.

### 200 VUs

| Idempotency | HTTP req/s | Browse P95 | Browse P99 | Reserve P95 | Cancel P95 | Conflict rate |
|---|---:|---:|---:|---:|---:|---:|
| Off | 410.34 | 429.60 ms | 591.19 ms | 377.32 ms | 370.55 ms | 66.52% |
| Unique | 410.86 | 407.98 ms | 574.03 ms | 327.00 ms | 302.90 ms | 66.62% |
| Retry | 413.22 | 396.05 ms | 579.69 ms | 310.42 ms | 379.37 ms | 66.61% |

All three 200-VU hotspot runs crossed:

```text
Browse P95 < 250 ms
Browse P99 < 500 ms
```

No technical errors or failed checks occurred. Reservation, cancellation, journey, and error-rate thresholds remained valid. The approximately `66.6%` expected-conflict rate confirms that this workload generated severe competition over the same slot set.

The combination of increasing latency and lower throughput compared with 100 VUs indicates saturation under the deliberately artificial hotspot workload. This does not indicate a race-condition failure: one user wins a contested slot and the remaining requests receive supported business conflicts.

# Overall assessment

| Area | Result |
|---|---|
| First-page reads through Redis | Passed through 200 VUs |
| Deep keyset/cursor reads | P95/P99 passed through 200 VUs |
| Deep cursor strict maximum below 200 ms | Not demonstrated |
| Distributed mixed workload | All modes passed through 200 VUs |
| Hotspot mixed workload | Passed at 100 VUs; browse saturated at 200 VUs |
| Technical error rates | 0% in every reviewed result |
| Response checks | No failures |
| Reservation concurrency correctness | Preserved |
| Cancellation correctness | Every attempted cancellation succeeded |
| Idempotency replay | 100% successful; no mismatches |

A defensible conclusion is:

> On the reviewed local dataset containing 1.2 million slots, first-page Redis reads, deep cursor reads, and all distributed mixed workloads passed their configured P95/P99 and correctness thresholds through 200 concurrent VUs without technical errors. The intentionally adversarial hotspot remained correct but crossed browse-latency thresholds at 200 VUs because repeated writes and cache invalidations concentrated on the same slot set.

The implementation must not claim that every individual request in every scenario is always below `200 ms`, because the deep-cursor maximum and 200-VU hotspot results exceed that value.

---

# Result files

The reviewed suite contains 18 result files:

```text
results-first-vus-50.json
results-first-vus-100.json
results-first-vus-200.json

results-deep-vus-50.json
results-deep-vus-100.json
results-deep-vus-200.json

mixed-distributed-idempotency-off-vu100.json
mixed-distributed-idempotency-off-vu200.json
mixed-distributed-idempotency-unique-vu100.json
mixed-distributed-idempotency-unique-vu200.json
mixed-distributed-idempotency-retry-rate-0p10-vu100.json
mixed-distributed-idempotency-retry-rate-0p10-vu200.json

mixed-hotspot-idempotency-off-vu100.json
mixed-hotspot-idempotency-off-vu200-threshold-failed.json
mixed-hotspot-idempotency-unique-vu100.json
mixed-hotspot-idempotency-unique-vu200-threshold-failed.json
mixed-hotspot-idempotency-retry-rate-0p10-vu100.json
mixed-hotspot-idempotency-retry-rate-0p10-vu200-threshold-failed.json
```

Raw result files must not be committed.

---

# Sanitizing and packaging results

Read-only result files no longer contain JWT setup data. Mixed-workload exports can still contain `setup_data` with access tokens because those scenarios authenticate users.

Sanitize every result before sharing or archiving it:

```bash
rm -rf performance/results/sanitized
mkdir -p performance/results/sanitized

for file in performance/results/*.json; do
  jq 'del(.setup_data)' \
    "$file" \
    > "performance/results/sanitized/$(basename "$file")"
done
```

Create an archive:

```bash
(
  cd performance/results/sanitized
  zip -r ../performance-results-$(date +%Y%m%d-%H%M%S).zip ./*.json
)
```

Check the archive:

```bash
unzip -l performance/results/performance-results-*.zip
```

Generated JSON and ZIP files must remain excluded from Git.
