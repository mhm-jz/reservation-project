# Performance and Load Testing

This document describes the load-test setup, execution commands, metrics, and verified local benchmark results for the Reservation Platform.

The performance suite validates:

- `GET /api/slots` over a dataset with more than one million slot records
- Redis-backed first-page reads
- deep cursor/keyset pagination against MySQL
- concurrent browse, reserve, and cancel flows
- expected reservation conflicts under contention
- cache invalidation behavior during write operations

> The numbers in this document were collected in a local Docker-based environment. They demonstrate the behavior of the current implementation and are not production capacity guarantees.

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

## Dataset

The verified benchmark used:

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
- Reservation Service running on port `8081`

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
```

The application and performance tools share the normal `reservation_db` database
in the `reservation-mysql` container on host port `3306`. MySQL data persists in
the named `reservation_mysql_data` Docker volume. Restarting the application does
not recreate the database.

Do not run `docker compose down -v` unless deleting the persistent database is
intentional.

## First-time or repair seeding

Performance seeding is disabled by default. Enable it explicitly for first-time
seeding or to safely resume a partial dataset:

```bash
APP_PERFORMANCE_SEEDING_ENABLED=true \
./mvnw -pl reservation-service spring-boot:run
```

The seeder verifies all deterministic performance users, slots, seeded
reservations, and reserved-slot state. It skips an already-complete dataset and
uses duplicate-safe inserts to resume missing rows. If existing normal rows
conflict with deterministic performance IDs, startup stops with a clear error;
the seeder never truncates, deletes, or overwrites existing data.

## Start the application without rerunning the seeder

```bash
./mvnw -pl reservation-service spring-boot:run
```

The explicit equivalent is:

```bash
APP_PERFORMANCE_SEEDING_ENABLED=false \
./mvnw -pl reservation-service spring-boot:run
```

---

# Preflight validation

Run these checks from the repository root:

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
curl -i http://127.0.0.1:8081/actuator/health
```

Verify Redis:

```bash
docker exec reservation-redis redis-cli PING
```

Expected response:

```text
PONG
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

The API accepts a maximum range of 30 days.

Do not use:

```text
2026-08-01T00:00:00
to
2026-09-01T00:00:00
```

That interval spans 31 days.

## Read-only authentication

```text
AUTH_USERNAME=k6-load-test-user-new
AUTH_PASSWORD=TestPassword123
```

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

---

## 1. First-page read test

This scenario calls:

```http
GET /api/slots?from=...&to=...&limit=100
```

without a cursor.

Expected path:

```text
request without cursor
→ Redis per-day head cache
→ return at most 100 items
→ use one extra internal item to determine hasNext
```

Run all levels:

```bash
AUTH_USERNAME=k6-load-test-user-new \
AUTH_PASSWORD=TestPassword123 \
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

## 2. Deep-cursor read test

This scenario calls `GET /api/slots` with a cursor.

Expected path:

```text
request with cursor
→ Redis head cache bypass
→ indexed MySQL keyset query
→ ORDER BY start_time, id
→ fetch limit + 1
```

Verified cursor:

```text
eyJzdGFydFRpbWUiOiIyMDI2LTA2LTI4VDIzOjU4OjAwIiwiaWQiOjc4MzM1OX0
```

Run all levels:

```bash
AUTH_USERNAME=k6-load-test-user-new \
AUTH_PASSWORD=TestPassword123 \
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

Before each level, the runner deletes only the confirmed slot-cache key
patterns:

```text
slots:version:*
slots:head:*
slots:head-lock:*
```

It uses Redis `SCAN` plus `UNLINK`; it does not flush the shared Redis database.
After the cursor-only run, it scans `slots:head:*` and `slots:head-lock:*`.
Finding zero matching keys confirms that cursor requests did not populate the
slot head cache, regardless of unrelated authentication, session, blacklist, or
application keys in Redis.

Manual head-cache check:

```bash
docker exec reservation-redis redis-cli --scan --pattern 'slots:head:*'
```

---

## 3. Mixed hotspot test

Business distribution:

```text
80% browse only
15% browse and reserve
5% browse, reserve, and cancel
```

All VUs query the same broad range and compete for the same first-page slots.

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

The mixed runner modifies database rows, so it refuses to start unless
`ALLOW_PERFORMANCE_DATA_RESET=true` is explicitly supplied. Cleanup runs in a
transaction and selects only reservations owned by users whose usernames match
`USERNAME_PREFIX` (default `perf-user-`), whose slots fall inside `FROM`/`TO`,
and whose reservation timestamp is not the deterministic seeded-baseline
timestamp. It deletes only those selected reservations, restores only their
slots when no reservation remains, and reports selected, deleted, and restored
row counts. Seeded baseline reservations and every non-performance user's data
are preserved.

The runner also clears only the three slot-cache Redis patterns documented
above.

---

## 4. Mixed distributed test

The business ratios are the same as the hotspot test:

```text
80% browse only
15% browse and reserve
5% browse, reserve, and cancel
```

VUs rotate over separate daily windows:

```javascript
dayIndex = (__VU - 1 + __ITER) % totalDays;
```

This keeps the requests concurrent while reducing artificial contention on one small slot set.

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

# Metrics and thresholds

## Read-only thresholds

```text
page duration P95 < 150 ms
page duration P99 < 200 ms
page error rate < 1%
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

Within one VU, the journey is sequential:

```text
Browse → optional Reserve → optional Cancel
```

Different VUs execute concurrently.

`200 VUs` does not mean `200 requests per second`; the actual request rate depends on latency, think time, and the selected business path.

---

# Verified benchmark results

## First-page read

| VU | Avg | P95 | P99 | Max | Measurement req/s |
|---:|---:|---:|---:|---:|---:|
| 20 | 10.42 ms | 15.59 ms | 18.93 ms | 43.04 ms | 149 |
| 50 | 11.10 ms | 17.69 ms | 20.92 ms | 39.99 ms | 372 |
| 100 | 8.83 ms | 16.56 ms | 19.71 ms | 40.08 ms | 760 |
| 200 | 5.75 ms | 10.98 ms | 15.54 ms | 46.85 ms | 1,565 |

Result:

```text
All response checks passed.
HTTP failure rate: 0%.
Page error rate: 0%.
All configured thresholds passed.
```

The first-page Redis cache remained fast at every tested VU level.

At `200 VUs`, it processed approximately `1,565` measured page requests per second with a `P95` of approximately `11 ms`.

The lower average at higher VU levels is likely influenced by a fully warmed cache and local connection reuse. It should not be interpreted as a general rule that higher load improves latency.

---

## Deep cursor

| VU | Avg | P95 | P99 | Max | Measurement req/s |
|---:|---:|---:|---:|---:|---:|
| 20 | 18.33 ms | 25.32 ms | 29.61 ms | 40.08 ms | 139 |
| 50 | 12.85 ms | 19.24 ms | 24.24 ms | 64.25 ms | 367 |
| 100 | 11.18 ms | 16.80 ms | 21.98 ms | 102.10 ms | 745 |
| 200 | 61.08 ms | 105.97 ms | 142.18 ms | 365.18 ms | 1,029 |

Result:

```text
All response and pagination checks passed.
HTTP failure rate: 0%.
Page error rate: 0%.
P95 and P99 thresholds passed at every level.
```

At `200 VUs`, database pressure became visible:

```text
Avg: 61.08 ms
P95: 105.97 ms
P99: 142.18 ms
```

The configured percentile targets still passed, but the maximum value reached `365.18 ms`. Therefore, the results do not support a claim that every individual request always remains below `200 ms`.

---

## Mixed distributed

| VU | Journey/s | HTTP req/s | Browse P95 | Browse P99 | Reserve P95 | Cancel P95 | Conflict rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 20 | 182 | 228 | 9.18 ms | 11.28 ms | 9.53 ms | 8.99 ms | 0.00% |
| 50 | 437 | 545 | 8.96 ms | 28.87 ms | 9.64 ms | 9.98 ms | 0.07% |
| 100 | 802 | 1,005 | 11.81 ms | 33.26 ms | 13.41 ms | 13.84 ms | 0.26% |
| 200 | 1,235 | 1,542 | 47.68 ms | 96.93 ms | 47.13 ms | 49.33 ms | 2.83% |

### Distributed 200-VU operation counts

```text
Journeys:                    97,622
HTTP requests:              121,966

Reservation attempts:        19,428
Successful reservations:     18,878
Expected conflicts:             550

Cancellation attempts:        4,716
Successful cancellations:     4,716
```

Result:

```text
Browse errors: 0%.
Reservation technical errors: 0%.
Cancellation errors: 0%.
HTTP failure rate: 0%.
All configured thresholds passed.
All attempted cancellations succeeded.
```

This is the closest scenario to a normal high-throughput workload.

At `200 VUs`, the system handled approximately:

```text
1,235 journeys/s
1,542 HTTP requests/s
```

while keeping browse `P95` at approximately `47.68 ms`.

---

## Mixed hotspot

| VU | Journey/s | HTTP req/s | Browse P95 | Browse P99 | Reserve P95 | Cancel P95 | Conflict rate |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 20 | 173 | 216 | 40.22 ms | 49.75 ms | 9.07 ms | 8.48 ms | 2.89% |
| 50 | 377 | 471 | 57.41 ms | 68.12 ms | 13.92 ms | 13.17 ms | 10.65% |
| 100 | 422 | 521 | 168.54 ms | 196.22 ms | 82.04 ms | 74.74 ms | 33.65% |
| 200 | 334 | 409 | 396.20 ms | 565.99 ms | 295.96 ms | 306.44 ms | 66.35% |

### Hotspot 200-VU operation counts

```text
Reservation attempts:         5,323
Successful reservations:      1,791
Expected conflicts:            3,532

Cancellation attempts:           447
Successful cancellations:        447
```

Result:

```text
Technical error rate: 0%.
HTTP failure rate: 0%.
Reservation correctness remained intact.
All attempted cancellations succeeded.
```

At `200 VUs`, the browse percentile thresholds failed:

```text
Browse P95 target: < 250 ms
Observed:          396.20 ms

Browse P99 target: < 500 ms
Observed:          565.99 ms
```

Throughput also fell from approximately `521 HTTP req/s` at `100 VUs` to `409 HTTP req/s` at `200 VUs`.

The combination of rising latency and falling throughput indicates saturation under an artificial worst-case hotspot.

The high conflict rate is expected because all users repeatedly compete for the same limited slot set.

---

# Overall assessment

| Area | Result |
|---|---|
| First-page Redis cache | Passed; strong performance |
| Deep cursor up to 100 VUs | Passed; strong performance |
| Deep cursor at 200 VUs | P95/P99 passed; some outliers above 200 ms |
| Mixed distributed up to 200 VUs | Passed; strong and realistic result |
| Mixed hotspot up to 100 VUs | Passed |
| Mixed hotspot at 200 VUs | Saturated; browse thresholds failed |
| Technical errors | 0% in all 16 runs |
| Reservation concurrency correctness | Preserved |
| Successful cancellation rate | 100% for attempted cancellations |

With more than one million slot records, the first-page, deep-cursor, and mixed distributed scenarios completed without technical errors and kept their important percentile metrics below `200 ms`.

The implementation should not claim that every request in every possible scenario always remains below `200 ms`, because:

- deep cursor at `200 VUs` included a `365.18 ms` maximum outlier
- distributed `200 VUs` included maximum outliers above `200 ms`
- the artificial hotspot at `200 VUs` exceeded the browse percentile targets

A defensible conclusion is:

> On a dataset containing 1.2 million slots, the first-page, deep-cursor, and realistic mixed distributed workloads completed at up to 200 concurrent VUs without technical errors. Their key P95/P99 response times remained below 200 ms. The artificial 200-VU hotspot reached saturation because all users repeatedly competed for the same limited slot set, while reservation and cancellation correctness remained intact.

---

# Result files

The verified suite contains 16 result files:

```text
results-first-vus-20.json
results-first-vus-50.json
results-first-vus-100.json
results-first-vus-200.json

results-deep-vus-20.json
results-deep-vus-50.json
results-deep-vus-100.json
results-deep-vus-200.json

results-mixed-hotspot-vus-20.json
results-mixed-hotspot-vus-50.json
results-mixed-hotspot-vus-100.json
results-mixed-hotspot-vus-200.json

results-mixed-distributed-vus-20.json
results-mixed-distributed-vus-50.json
results-mixed-distributed-vus-100.json
results-mixed-distributed-vus-200.json
```

Raw result files must not be committed.

---

# Sanitizing and packaging results

k6 `--summary-export` output may contain `setup_data`, including an access token. Do not commit or share raw result files before sanitizing them.

With `jq`:

```bash
rm -rf performance/results/sanitized
mkdir -p performance/results/sanitized

for file in performance/results/results-*.json; do
  jq 'del(.setup_data)' \
    "$file" \
    > "performance/results/sanitized/$(basename "$file")"
done

(
  cd performance/results/sanitized
  zip -r ../performance-results.zip ./*.json
)
```

Check the archive:

```bash
unzip -l performance/results/performance-results.zip
```

The generated ZIP also belongs under `performance/results/` and must remain excluded from Git.
