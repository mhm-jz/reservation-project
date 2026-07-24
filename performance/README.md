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

- Java 17+
- Docker and Docker Compose
- MySQL
- Redis
- k6
- Maven or Maven Wrapper
- Reservation Service running on port `8080`

Check the environment:

```bash
java -version
docker --version
k6 version
./mvnw -version
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
./mvnw -pl reservation-service spring-boot:run
```

The seeder is duplicate-safe and skips an already complete dataset. It must not be enabled for normal benchmark runs.

## Start the application without rerunning the seeder

```bash
APP_PERFORMANCE_SEEDING_ENABLED=false \
./mvnw -pl reservation-service spring-boot:run
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

Verify the application:

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
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

Generated files:

```text
results-mixed-distributed-vus-20.json
results-mixed-distributed-vus-50.json
results-mixed-distributed-vus-100.json
results-mixed-distributed-vus-200.json
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

Generated files:

```text
results-mixed-hotspot-vus-20.json
results-mixed-hotspot-vus-50.json
results-mixed-hotspot-vus-100.json
results-mixed-hotspot-vus-200.json
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
2026-07-24
```

These are the verified results after making `GET /api/slots` public and removing login/JWT work from the read-only runners.

## First-page public read

| VU | Avg | P95 | P99 | Max |
|---:|---:|---:|---:|---:|
| 20 | 12.52 ms | 16.56 ms | 20.59 ms | 170.59 ms |
| 50 | 9.17 ms | 15.51 ms | 22.37 ms | 80.29 ms |
| 100 | 7.78 ms | 13.73 ms | 18.42 ms | 45.89 ms |
| 200 | 4.46 ms | 9.01 ms | 13.11 ms | 38.50 ms |

At `200 VUs`:

```text
Measured page requests: 114,416
Measured page request rate: 1,586.77 req/s
Page error rate: 0%
HTTP failure rate: 0%
```

Result:

```text
All response checks passed.
All configured thresholds passed.
The public first-page Redis path remained fast at every tested VU level.
```

The lower average at higher VU levels is likely influenced by a fully warmed cache, JVM/JIT warm-up, and local connection reuse. It must not be interpreted as a general rule that higher load improves latency.

---

## Deep-cursor public read

| VU | Avg | P95 | P99 | Max |
|---:|---:|---:|---:|---:|
| 20 | 17.85 ms | 26.97 ms | 33.56 ms | 43.09 ms |
| 50 | 12.80 ms | 21.95 ms | 29.11 ms | 49.88 ms |
| 100 | 9.86 ms | 18.79 ms | 36.29 ms | 165.00 ms |
| 200 | 33.81 ms | 72.48 ms | 116.41 ms | 342.25 ms |

At `200 VUs`:

```text
Measured page requests: 89,401
Measured page request rate: 1,239.23 req/s
Page error rate: 0%
HTTP failure rate: 0%
```

Result:

```text
All response and pagination checks passed.
P95 and P99 thresholds passed at every level.
Cursor-cache bypass remained intact.
```

The maximum at `200 VUs` reached `342.25 ms`, so these results do not support a claim that every individual request always remains below `200 ms`. The important configured P95/P99 targets still passed.

---

## Mixed distributed

| VU | Journey/s | HTTP req/s | Browse P95 | Browse P99 | Reserve P95 | Cancel P95 | Conflict rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 20 | 168.18 | 209.38 | 27.45 ms | 42.69 ms | 15.22 ms | 9.59 ms | 0.24% |
| 50 | 440.03 | 551.76 | 6.91 ms | 16.21 ms | 9.36 ms | 9.02 ms | 0.10% |
| 100 | 820.40 | 1,028.05 | 7.11 ms | 29.88 ms | 10.51 ms | 9.13 ms | 0.26% |
| 200 | 1,381.02 | 1,723.88 | 31.05 ms | 38.73 ms | 22.84 ms | 23.69 ms | 1.23% |

### Distributed 200-VU operation counts

```text
Journeys: 108,613
HTTP requests: 135,578
Reservation attempts: 21,529
Successful reservations: 21,264
Expected conflicts: 265
Cancellation attempts: 5,236
Successful cancellations: 5,236
```

Result:

```text
Browse errors: 0%
Reservation technical errors: 0%
Cancellation errors: 0%
HTTP failure rate: 0%
All configured thresholds passed.
All attempted cancellations succeeded.
```

This is the closest tested scenario to a normal high-throughput workload. At `200 VUs`, the system processed approximately `1,724 HTTP requests/s` while browse P95 remained approximately `31 ms`.

---

## Mixed hotspot

| VU | Journey/s | HTTP req/s | Browse P95 | Browse P99 | Reserve P95 | Cancel P95 | Conflict rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 20 | 167.56 | 209.89 | 48.14 ms | 70.71 ms | 10.57 ms | 8.33 ms | 5.33% |
| 50 | 406.95 | 507.40 | 44.27 ms | 52.31 ms | 7.22 ms | 7.06 ms | 6.36% |
| 100 | 469.12 | 578.84 | 157.71 ms | 176.99 ms | 72.01 ms | 66.60 ms | 32.19% |
| 200 | 335.31 | 410.91 | 366.72 ms | 538.33 ms | 265.35 ms | 271.34 ms | 66.22% |

### Hotspot 200-VU operation counts

```text
Journeys: 26,527
HTTP requests: 32,508
Reservation attempts: 5,328
Successful reservations: 1,800
Expected conflicts: 3,528
Cancellation attempts: 453
Successful cancellations: 453
```

Result:

```text
Technical error rate: 0%
HTTP failure rate: 0%
Reservation correctness remained intact.
All attempted cancellations succeeded.
```

At `200 VUs`, the browse thresholds failed:

```text
Browse P95 target: < 250 ms
Observed: 366.72 ms

Browse P99 target: < 500 ms
Observed: 538.33 ms
```

Throughput also fell from approximately `579 HTTP req/s` at `100 VUs` to `411 HTTP req/s` at `200 VUs`. Rising latency together with falling throughput indicates saturation under this artificial worst-case hotspot.

The high conflict rate is expected because all users repeatedly compete for the same limited slot set.

---

# Effect of making GET /api/slots public

The before/after comparison below uses the same `200 VU` read-only scenarios.

## First-page 200 VUs

| Metric | Before: JWT required | After: public, no JWT | Change |
|---|---:|---:|---:|
| Avg | 4.53 ms | 4.46 ms | 1.5% lower |
| P95 | 9.25 ms | 9.01 ms | 2.6% lower |
| P99 | 14.70 ms | 13.11 ms | 10.8% lower |
| Max | 56.23 ms | 38.50 ms | 31.5% lower |
| Measured page req/s | 1,583.51 | 1,586.77 | effectively stable |
| Data sent | 37.79 MB | 16.72 MB | 55.7% lower |

## Deep cursor 200 VUs

| Metric | Before: JWT required | After: public, no JWT | Change |
|---|---:|---:|---:|
| Avg | 53.70 ms | 33.81 ms | 37.0% lower |
| P95 | 113.55 ms | 72.48 ms | 36.2% lower |
| P99 | 174.21 ms | 116.41 ms | 33.2% lower |
| Max | 472.68 ms | 342.25 ms | 27.6% lower |
| Measured page req/s | 1,077.22 | 1,239.23 | 15.0% higher |
| Data sent | 31.39 MB | 19.48 MB | 37.9% lower |

The new read-only exports contain no authentication setup data because they do not perform login or token extraction.

The improvement in data sent is directly consistent with removing login traffic and bearer headers. Latency and throughput also improved or remained stable, but local JVM, cache, connection-pool, MySQL, and machine conditions can influence benchmark variation. Therefore, the full deep-cursor improvement must not be attributed solely to JWT removal.

The mixed hotspot result at `200 VUs` remained effectively unchanged before and after this API-security change. That indicates the hotspot saturation is driven by contention, write operations, cache invalidation, and rebuild pressure rather than JWT parsing on isolated slot reads.

---

# Overall assessment

| Area | Result |
|---|---|
| Public `GET /api/slots` contract | Verified |
| Read-only runners without login/JWT | Verified |
| First-page Redis cache | Passed; strong performance |
| Deep cursor up to 200 VUs | P95/P99 passed; some maximum outliers above 200 ms |
| Mixed distributed up to 200 VUs | Passed; strong and realistic result |
| Mixed hotspot up to 100 VUs | Passed |
| Mixed hotspot at 200 VUs | Saturated; browse thresholds failed |
| Technical errors | 0% in all verified post-change runs |
| Reservation concurrency correctness | Preserved |
| Successful cancellation rate | 100% for attempted cancellations |

A defensible conclusion is:

> On a dataset containing 1.2 million slots, the public first-page, public deep-cursor, and realistic mixed distributed workloads completed at up to 200 concurrent VUs without technical errors. Their key P95/P99 response times remained below 200 ms. The artificial 200-VU hotspot reached saturation because all users repeatedly competed for the same limited slot set, while reservation and cancellation correctness remained intact.

The implementation must not claim that every request in every scenario always stays below `200 ms`, because deep-cursor maximum outliers and the artificial hotspot exceed that value.

---

# Result files

The standard verified suite contains 16 result files:

```text
results-first-vus-20.json
results-first-vus-50.json
results-first-vus-100.json
results-first-vus-200.json

results-deep-vus-20.json
results-deep-vus-50.json
results-deep-vus-100.json
results-deep-vus-200.json

results-mixed-distributed-vus-20.json
results-mixed-distributed-vus-50.json
results-mixed-distributed-vus-100.json
results-mixed-distributed-vus-200.json

results-mixed-hotspot-vus-20.json
results-mixed-hotspot-vus-50.json
results-mixed-hotspot-vus-100.json
results-mixed-hotspot-vus-200.json
```

Raw result files must not be committed.

---

# Sanitizing and packaging results

Read-only result files no longer contain JWT setup data. Mixed-workload exports can still contain `setup_data` with access tokens because those scenarios authenticate users.

Sanitize every result before sharing or archiving it:

```bash
rm -rf performance/results/sanitized
mkdir -p performance/results/sanitized

for file in performance/results/results-*.json; do
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
