# Reservation Platform

A high-performance reservation service built with Spring Boot, MySQL, Redis, JWT authentication, cursor-based pagination, database-level concurrency control, and deterministic performance seeding.

The project is designed to support:

- public browsing of available time slots
- authenticated user registration and login
- atomic slot reservation
- reservation cancellation
- optional idempotent reservation creation
- large datasets with more than one million slot rows
- first-page Redis acceleration
- deep keyset/cursor pagination
- concurrent load and contention testing with k6

> The current repository is a Maven multi-module project with one deployable module: `reservation-service`.

---

## Table of contents

- [Project overview](#project-overview)
- [Architecture](#architecture)
- [Project structure](#project-structure)
- [Technology stack](#technology-stack)
- [Prerequisites](#prerequisites)
- [Environment configuration](#environment-configuration)
- [Recommended first-time setup](#recommended-first-time-setup)
- [Normal application startup](#normal-application-startup)
- [Performance data seeder](#performance-data-seeder)
- [API overview](#api-overview)
- [Authentication and security](#authentication-and-security)
- [Database design](#database-design)
- [Concurrency and consistency](#concurrency-and-consistency)
- [Idempotency](#idempotency)
- [Cursor pagination](#cursor-pagination)
- [Redis cache strategy](#redis-cache-strategy)
- [UTC time convention](#utc-time-convention)
- [Performance testing](#performance-testing)
- [Operational recommendations](#operational-recommendations)
- [Known limitations and design decisions](#known-limitations-and-design-decisions)
- [Troubleshooting](#troubleshooting)

---

# Project overview

The Reservation Platform exposes a REST API for browsing and reserving time slots.

The main business flow is:

```text
Register or login
        ↓
Receive JWT access token
        ↓
Browse public available slots
        ↓
Reserve a future available slot
        ↓
Optionally retry safely with an Idempotency-Key
        ↓
Cancel an owned reservation when needed
```

The slot read path is optimized differently for first-page and deep-page access:

```text
First page without cursor
        ↓
Redis per-day head cache
        ↓
Fallback to indexed MySQL query when needed
```

```text
Page with cursor
        ↓
Bypass Redis head cache
        ↓
Indexed MySQL keyset query
```

---

# Architecture

The application is organized as a modular monolith using package-by-feature.

```text
Client
  │
  ▼
Spring MVC controllers
  │
  ├── Authentication
  ├── Slot browsing
  └── Reservation management
  │
  ▼
Application services
  │
  ├── Spring Data JPA / MySQL
  ├── Redis slot-head cache
  ├── Spring Security / JWT
  └── Transactional domain events
```

The application itself runs on the host by default. Docker Compose provides the persistent infrastructure:

```text
Host
├── Spring Boot application :8080
├── MySQL container         :3306
└── Redis container         :6379
```

---

# Project structure

```text
reservation-project/
├── pom.xml
├── docker-compose.yml
├── .env
├── README.md
│
├── reservation-service/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/azki/reservation/
│       │   ├── auth/
│       │   │   ├── dto/
│       │   │   ├── mapper/
│       │   │   ├── AuthController.java
│       │   │   └── AuthService.java
│       │   │
│       │   ├── common/
│       │   │   ├── exception/
│       │   │   └── openapi/
│       │   │
│       │   ├── config/
│       │   │   ├── JwtProperties.java
│       │   │   ├── PerformanceSeedingProperties.java
│       │   │   ├── SlotCacheProperties.java
│       │   │   ├── SlotSearchProperties.java
│       │   │   └── TimeConfig.java
│       │   │
│       │   ├── devtools/performance/
│       │   │   └── PerformanceDataSeeder.java
│       │   │
│       │   ├── reservation/
│       │   │   ├── dto/
│       │   │   ├── mapper/
│       │   │   ├── ReservationController.java
│       │   │   ├── ReservationService.java
│       │   │   ├── ReservationEntity.java
│       │   │   ├── ReservationRepository.java
│       │   │   ├── ReservationIdempotencyEntity.java
│       │   │   └── ReservationIdempotencyRepository.java
│       │   │
│       │   ├── security/
│       │   │   ├── AuthenticatedUser.java
│       │   │   ├── JwtAuthenticationFilter.java
│       │   │   ├── JwtService.java
│       │   │   └── SecurityConfig.java
│       │   │
│       │   ├── slot/
│       │   │   ├── dto/
│       │   │   ├── AvailableSlotEntity.java
│       │   │   ├── AvailableSlotRepository.java
│       │   │   ├── SlotController.java
│       │   │   ├── SlotService.java
│       │   │   ├── SlotQueryService.java
│       │   │   ├── SlotDayHeadCache.java
│       │   │   └── SlotCacheInvalidationListener.java
│       │   │
│       │   ├── user/
│       │   │   ├── UserEntity.java
│       │   │   └── UserRepository.java
│       │   │
│       │   └── ReservationServiceApplication.java
│       │
│       └── resources/
│           └── application.yaml
│
└── performance/
    ├── README.md
    ├── scripts/
    │   ├── slots.js
    │   └── reservation-flow.js
    ├── runners/
    │   ├── run-slots-first.sh
    │   ├── run-slots-deep.sh
    │   ├── run-mixed-distributed.sh
    │   └── run-mixed-hotspot.sh
    └── results/
```

---

# Technology stack

| Area | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.16 |
| Web | Spring MVC |
| Persistence | Spring Data JPA / Hibernate |
| Database | MySQL 8.4 |
| Cache | Redis 7.4 |
| Security | Spring Security |
| Authentication | JWT using JJWT 0.13.0 |
| Password hashing | BCrypt |
| Validation | Jakarta Bean Validation |
| Mapping | MapStruct 1.6.3 |
| Boilerplate reduction | Lombok |
| API documentation | Springdoc OpenAPI 2.8.17 |
| Build | Maven |
| Infrastructure | Docker Compose |
| Performance testing | k6 |
| Serialization | Jackson |

---

# Prerequisites

Install the following tools:

- Java 21
- Maven
- Docker and Docker Compose
- Git
- `curl`
- k6, only when running performance tests

Verify the installation:

```bash
java -version
mvn -version
docker --version
docker compose version
```

For performance testing:

```bash
k6 version
```

> The repository currently uses `mvn` commands. Use `./mvnw` only after a Maven Wrapper has been generated and committed to the repository.

---

# Environment configuration

Docker Compose reads the root `.env` file automatically.

The host-running Spring Boot process does **not** automatically read `.env`, so export it before starting the application:

```bash
set -a
source .env
set +a
```

A safe local example:

```dotenv
# Docker infrastructure
MYSQL_DATABASE=reservation_db
MYSQL_PORT=3306
MYSQL_ROOT_PASSWORD=replace-with-a-local-root-password

# Spring Boot application -> MySQL
DB_URL='jdbc:mysql://127.0.0.1:3306/reservation_db?rewriteBatchedStatements=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
DB_USERNAME=reservation_user
DB_PASSWORD=reservation_password

# Spring Boot application -> Redis
REDIS_HOST=127.0.0.1
REDIS_PORT=6379

# Generate a different value for every environment
JWT_SECRET=replace-with-a-long-base64-secret
```

Generate a local JWT secret:

```bash
openssl rand -base64 32
```

## Application environment variables

| Variable | Default | Description |
|---|---:|---|
| `DB_URL` | MySQL on `127.0.0.1:3306/reservation_db` | JDBC connection URL |
| `DB_USERNAME` | `reservation_user` | Application database username |
| `DB_PASSWORD` | required | Application database password |
| `REDIS_HOST` | `127.0.0.1` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `SERVER_PORT` | `8080` | HTTP application port |
| `JWT_SECRET` | required | Base64-encoded JWT signing secret |
| `SLOTS_CACHE_ENABLED` | `true` | Enables Redis first-page slot acceleration |
| `APP_PERFORMANCE_SEEDING_ENABLED` | `false` | Enables deterministic performance seeding |
| `APP_PERFORMANCE_USER_PASSWORD` | `TestPassword123` | Password assigned to deterministic performance users |

## Docker Compose variables

| Variable | Default | Description |
|---|---:|---|
| `MYSQL_DATABASE` | `reservation_db` | Database created by MySQL |
| `MYSQL_PORT` | `3306` | MySQL host port |
| `MYSQL_ROOT_PASSWORD` | required | MySQL root password |
| `DB_USERNAME` | `reservation_user` | MySQL application user |
| `DB_PASSWORD` | required | MySQL application-user password |
| `REDIS_PORT` | `6379` | Redis host port |

---

# Recommended first-time setup

## 1. Clone and enter the repository

```bash
git clone https://github.com/mhm-jz/reservation-project.git
cd reservation-project
```

Check out the intended branch when required:

```bash
git checkout feature/add-idempotency
```

## 2. Configure local environment variables

Create or update `.env`, then load it into the current shell:

```bash
set -a
source .env
set +a
```

## 3. Start MySQL and Redis

```bash
docker compose up -d reservation-mysql reservation-redis
```

Check container status:

```bash
docker compose ps
```

Verify Redis:

```bash
docker exec reservation-redis redis-cli PING
```

Expected response:

```text
PONG
```

Verify MySQL:

```bash
docker exec reservation-mysql \
  mysqladmin ping \
  -h 127.0.0.1 \
  -u"$DB_USERNAME" \
  -p"$DB_PASSWORD"
```

## 4. Run the deterministic seeder once

For the first local setup or to repair an incomplete deterministic dataset:

```bash
APP_PERFORMANCE_SEEDING_ENABLED=true \
mvn -pl reservation-service spring-boot:run
```

Wait until the logs report that the performance dataset is complete.

The application remains usable after seeding finishes. Stop it with `Ctrl+C` when needed.

## 5. Start normally on subsequent runs

```bash
mvn -pl reservation-service spring-boot:run
```

Do not enable performance seeding for ordinary startup or benchmark runs.

## 6. Verify the API

Swagger UI:

```text
http://127.0.0.1:8080/swagger-ui/index.html
```

OpenAPI JSON:

```text
http://127.0.0.1:8080/v3/api-docs
```

Public slot request:

```bash
curl -i \
  "http://127.0.0.1:8080/api/slots?from=2026-06-01T00:00:00&to=2026-07-01T00:00:00&limit=20"
```

---

# Normal application startup

Start infrastructure:

```bash
docker compose up -d reservation-mysql reservation-redis
```

Load environment variables:

```bash
set -a
source .env
set +a
```

Run the service:

```bash
mvn -pl reservation-service spring-boot:run
```

Build a JAR:

```bash
mvn clean package
```

Run the packaged application:

```bash
java -jar reservation-service/target/reservation-service-*.jar
```

Stop infrastructure without deleting data:

```bash
docker compose stop
```

Start it again later:

```bash
docker compose start
```

> Avoid `docker compose down -v` unless deleting the persistent MySQL dataset is intentional.

---

# Performance data seeder

The seeder is implemented as a conditional `ApplicationRunner`.

It runs only when:

```text
app.performance-seeding.enabled=true
```

Environment form:

```text
APP_PERFORMANCE_SEEDING_ENABLED=true
```

## Generated dataset

| Data | Count |
|---|---:|
| Deterministic users | 10,000 |
| Slots | 1,200,000 |
| Seeded reservations | 360,000 |
| Initially available slots | 840,000 |
| JDBC batch size | 5,000 |

Deterministic users follow this pattern:

```text
perf-user-00001
perf-user-00002
...
perf-user-10000
```

Their email pattern is:

```text
perf-user-00001@example.test
```

Their password is controlled by:

```text
APP_PERFORMANCE_USER_PASSWORD
```

Default:

```text
TestPassword123
```

## Seeder behavior

The seeder:

- uses deterministic IDs
- uses JDBC batch inserts
- uses `INSERT IGNORE` to make insertion duplicate-safe
- checks whether the expected dataset is already complete
- skips structural seeding when the dataset is complete
- verifies deterministic performance-user credentials
- repairs only performance-user password hashes when necessary
- does not delete or overwrite structurally conflicting rows
- reports diagnostic samples when deterministic IDs conflict with existing data

The slot timeline starts at:

```text
2026-01-01T00:00:00
```

Each generated slot:

- starts one minute after the previous generated position
- lasts 30 minutes
- uses UTC by convention

## Recommended usage

First-time or repair run:

```bash
APP_PERFORMANCE_SEEDING_ENABLED=true \
mvn -pl reservation-service spring-boot:run
```

Normal run:

```bash
APP_PERFORMANCE_SEEDING_ENABLED=false \
mvn -pl reservation-service spring-boot:run
```

The seeder must not be treated as a production migration tool.

---

# API overview

| Method | Path | Authentication | Purpose |
|---|---|---|---|
| `POST` | `/api/auth/register` | Public | Register a user |
| `POST` | `/api/auth/login` | Public | Receive a JWT access token |
| `GET` | `/api/auth/me` | JWT | Get the authenticated user |
| `GET` | `/api/slots` | Public | Browse available slots |
| `POST` | `/api/reservations` | JWT | Reserve a slot |
| `DELETE` | `/api/reservations/{id}` | JWT | Cancel an owned reservation |

## Register

```bash
curl -i -X POST \
  http://127.0.0.1:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "email": "alice@example.com",
    "password": "StrongPassword123"
  }'
```

## Login

```bash
curl -s -X POST \
  http://127.0.0.1:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "password": "StrongPassword123"
  }'
```

## Browse slots

```bash
curl -s \
  "http://127.0.0.1:8080/api/slots?from=2026-06-01T00:00:00&to=2026-07-01T00:00:00&limit=20"
```

Response shape:

```json
{
  "items": [
    {
      "id": 123,
      "startTime": "2026-06-01T10:00:00",
      "endTime": "2026-06-01T10:30:00"
    }
  ],
  "nextCursor": "opaque-url-safe-cursor",
  "hasNext": true
}
```

## Reserve a slot

```bash
curl -i -X POST \
  http://127.0.0.1:8080/api/reservations \
  -H "Authorization: Bearer <access-token>" \
  -H "Content-Type: application/json" \
  -d '{"slotId":123}'
```

## Reserve with idempotency

```bash
curl -i -X POST \
  http://127.0.0.1:8080/api/reservations \
  -H "Authorization: Bearer <access-token>" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -H "Content-Type: application/json" \
  -d '{"slotId":123}'
```

## Cancel a reservation

```bash
curl -i -X DELETE \
  http://127.0.0.1:8080/api/reservations/987 \
  -H "Authorization: Bearer <access-token>"
```

---

# Authentication and security

The security model is stateless.

- passwords are hashed with BCrypt
- login returns a signed JWT access token
- the token contains the username and `userId`
- protected requests use `Authorization: Bearer <token>`
- no server-side HTTP session is created
- CSRF is disabled for the stateless REST API

Public operations:

```text
POST /api/auth/register
POST /api/auth/login
GET  /api/slots
Swagger and OpenAPI endpoints
```

Protected operations:

```text
GET    /api/auth/me
POST   /api/reservations
DELETE /api/reservations/{id}
```

The JWT filter intentionally bypasses the exact public `GET /api/slots` operation. This avoids unnecessary JWT parsing and user lookups on the hot read path.

---

# Database design

The database schema is currently managed by Hibernate:

```yaml
spring.jpa.hibernate.ddl-auto: update
```

No Flyway or Liquibase migrations are used in the current implementation.

## Main tables

### `users`

Stores authentication data.

Important constraints:

```text
UNIQUE(username)
UNIQUE(email)
```

### `available_slots`

Stores slot timing and current reservation state.

Important columns:

```text
id
start_time
end_time
is_reserved
created_at
```

Search index:

```text
idx_available_slots_search (
    is_reserved,
    start_time,
    id
)
```

This index supports:

- filtering available slots
- filtering by time range
- ordering by `start_time, id`
- keyset pagination

### `reservations`

Stores ownership of reserved slots.

Important constraints:

```text
UNIQUE(slot_id)
```

The unique slot constraint is a second database-level protection against duplicate reservations.

### `reservation_idempotency`

Stores the reservation snapshot associated with a per-user idempotency key.

Important constraint:

```text
UNIQUE(user_id, idempotency_key)
```

The table stores scalar IDs and a response snapshot instead of a foreign-key relationship to the reservation row. This allows replaying the original successful response even after the reservation is cancelled and deleted.

## Entity relationship diagram

```mermaid
erDiagram
    USERS ||--o{ RESERVATIONS : creates
    AVAILABLE_SLOTS ||--o| RESERVATIONS : reserved_by

    USERS {
        BIGINT id PK
        VARCHAR username UK
        VARCHAR email UK
        VARCHAR password
        DATETIME created_at
    }

    AVAILABLE_SLOTS {
        BIGINT id PK
        DATETIME start_time
        DATETIME end_time
        BOOLEAN is_reserved
        DATETIME created_at
    }

    RESERVATIONS {
        BIGINT id PK
        BIGINT user_id FK
        BIGINT slot_id FK UK
        DATETIME created_at
    }

    RESERVATION_IDEMPOTENCY {
        BIGINT id PK
        BIGINT user_id
        VARCHAR idempotency_key
        BIGINT slot_id
        BIGINT reservation_id
        DATETIME slot_start_time
        DATETIME slot_end_time
        DATETIME reservation_created_at
        DATETIME created_at
    }
```

---

# Concurrency and consistency

The reservation flow does not depend on Java `synchronized`, in-memory locks, or a single application instance.

Correctness is enforced at the database level.

## Atomic reservation claim

The service reserves a slot with one conditional update:

```sql
UPDATE available_slots
SET is_reserved = true
WHERE id = :slotId
  AND is_reserved = false
  AND start_time >= :now
```

The update returns the affected-row count.

```text
1 row updated
→ this request won the reservation

0 rows updated
→ missing, expired, or already unavailable
```

Under concurrent requests for the same slot, only one transaction can change the row from available to reserved.

## Additional uniqueness protection

The `reservations` table also has:

```text
UNIQUE(slot_id)
```

This protects the invariant that one slot can have at most one reservation, even if application logic is changed later.

## Transaction boundary

Reservation creation runs inside one database transaction:

```text
Optional idempotency claim
        ↓
Atomic slot state update
        ↓
Reservation insert
        ↓
Idempotency snapshot completion
        ↓
Commit
```

A failure rolls back the transaction, including:

- slot state change
- reservation insert
- incomplete idempotency claim

## Cancellation concurrency

Cancellation:

1. verifies the reservation belongs to the authenticated user
2. deletes it using `reservationId + userId`
3. conditionally changes the slot from reserved to available
4. commits the transaction
5. invalidates the affected day cache after commit

The conditional delete and conditional slot update prevent silent success on stale state.

## Error classification

When the atomic reservation update affects zero rows, the current slot state is loaded and mapped to a business error:

| Condition | Result |
|---|---|
| Slot does not exist | `404 SLOT_NOT_FOUND` |
| Slot start time is before current UTC time | `409 SLOT_EXPIRED` |
| Future slot is already reserved or unavailable | `409 SLOT_UNAVAILABLE` |

---

# Idempotency

`POST /api/reservations` accepts an optional UUID header:

```text
Idempotency-Key
```

Without the header, the original non-idempotent behavior is preserved.

## Scope

The uniqueness scope is:

```text
(user_id, idempotency_key)
```

Therefore:

- the same user cannot reuse the same key for a different operation
- different users may use the same UUID independently

## Flow

```text
INSERT IGNORE idempotency claim
        ↓
Inserted
├── create reservation
├── store immutable response snapshot
└── return HTTP 201
        ↓
Not inserted
├── load existing record
├── same slot → replay original HTTP 201 snapshot
└── different slot → HTTP 409 IDEMPOTENCY_KEY_REUSED
```

## Important behavior

- keys must be canonical UUID values
- a new reservation operation should use a new UUID
- retries for the same operation should reuse the same UUID
- failed reservation attempts are not retained
- cancellation does not remove the stored snapshot
- replay can still return the original response after cancellation
- idempotency records currently have no expiration or cleanup policy

---

# Cursor pagination

The slot API uses cursor/keyset pagination rather than offset pagination.

Request:

```http
GET /api/slots?from=...&to=...&limit=...&cursor=...
```

## Ordering

The stable ordering is:

```sql
ORDER BY start_time ASC, id ASC
```

The `id` acts as a deterministic tie-breaker when multiple slots share the same start time.

## Cursor contents

The cursor contains:

```json
{
  "startTime": "2026-06-28T23:58:00",
  "id": 783359
}
```

It is serialized as JSON and encoded using URL-safe Base64 without padding.

Clients must treat it as opaque and send `nextCursor` back unchanged.

## Keyset query

The next page uses:

```sql
start_time > :cursorStartTime
OR (
    start_time = :cursorStartTime
    AND id > :cursorId
)
```

This avoids the increasing scan cost of deep offset pagination.

## Page construction

The database reads:

```text
limit + 1
```

The extra item determines whether another page exists.

```text
items.size > limit
→ hasNext = true
→ nextCursor is built from the last returned item
```

## Validation

Current slot-query rules:

| Rule | Value |
|---|---:|
| Default page size | 20 |
| Minimum page size | 1 |
| Maximum page size | 100 |
| Maximum time range | 30 days |
| Range semantics | `[from, to)` |
| Required relation | `to > from` |

The cursor start time must also fall inside the requested range.

Malformed, incomplete, non-positive-ID, or out-of-range cursors return HTTP `400`.

---

# Redis cache strategy

Redis is an optional acceleration layer for first-page slot reads. MySQL remains the source of truth.

## What is cached

The cache stores the beginning of each day's available-slot list.

Default head size:

```text
101
```

This supports the maximum public page size of 100 plus one extra item for `hasNext`.

## Cache keys

```text
slots:version:{day}
slots:head:{day}:v{version}
slots:head-lock:{day}:v{version}
```

Example:

```text
slots:version:2026-08-01
slots:head:2026-08-01:v7
slots:head-lock:2026-08-01:v7
```

## Versioned invalidation

Reservation and cancellation publish a slot-availability event.

After the database transaction commits:

```text
slots:version:{day} += 1
```

New reads use the new versioned data key. Old cache entries are not synchronously deleted from the request path; they expire naturally.

This approach keeps invalidation fast and avoids returning cache state for a transaction that later rolls back.

## Cache rebuild concurrency

Cache rebuild uses a short Redis lock:

```text
SET key token NX PX lease
```

Default configuration:

| Setting | Default |
|---|---:|
| Cache enabled | `true` |
| Head size | `101` |
| Minimum TTL | `30s` |
| Maximum TTL | `60s` |
| Lock lease | `3s` |
| Retry delays | `25ms`, `50ms` |

Only the lock owner rebuilds the day head. Other requests wait briefly and then:

- use the rebuilt cache when available
- fall back to MySQL when the cache is still unavailable

The lock is released with a token-checking Lua script so one request cannot delete another request's lock.

## TTL jitter

The cache TTL is randomized between 30 and 60 seconds.

This reduces the chance that many daily keys expire at exactly the same moment.

## Failure strategy

Redis failure must not break slot browsing.

When Redis is unavailable, inconsistent, or contains corrupt JSON, the application falls back to the indexed MySQL query.

## Cursor behavior

Requests containing a cursor bypass the day-head cache and query MySQL directly.

This keeps the cache bounded and avoids creating a high-cardinality cache entry for every possible cursor.

---

# UTC time convention

The application uses UTC by convention at every layer.

At startup:

```text
JVM default timezone = UTC
```

Spring provides an injectable UTC `Clock`.

Hibernate JDBC timezone:

```text
UTC
```

The JDBC URL also configures the MySQL session timezone.

API and database `LocalDateTime` values:

- represent UTC
- are serialized without `Z`
- are serialized without a numeric offset

Example:

```text
2026-08-01T10:30:00
```

Clients must interpret this value as UTC.

---

# Performance testing

Detailed instructions are maintained in:

```text
performance/README.md
```

## Test scenarios

| Runner | Purpose |
|---|---|
| `run-slots-first.sh` | Public first-page slot reads using Redis head cache |
| `run-slots-deep.sh` | Public deep cursor reads using indexed MySQL keyset pagination |
| `run-mixed-distributed.sh` | Browse/reserve/cancel with load distributed over date windows |
| `run-mixed-hotspot.sh` | Worst-case contention over shared slots and cache days |

Default VU levels:

```text
20 50 100 200
```

When no VU arguments are supplied, each runner executes all standard levels.

## Read-only examples

```bash
./performance/runners/run-slots-first.sh 20 50 100 200
```

```bash
./performance/runners/run-slots-deep.sh 20 50 100 200
```

## Mixed-workload examples

Distributed:

```bash
ALLOW_PERFORMANCE_DATA_RESET=true \
IDEMPOTENCY_MODE=off \
./performance/runners/run-mixed-distributed.sh 20 50 100 200
```

Hotspot:

```bash
ALLOW_PERFORMANCE_DATA_RESET=true \
IDEMPOTENCY_MODE=off \
./performance/runners/run-mixed-hotspot.sh 200
```

Idempotency retry mode:

```bash
ALLOW_PERFORMANCE_DATA_RESET=true \
IDEMPOTENCY_MODE=retry \
IDEMPOTENCY_RETRY_RATE=0.10 \
./performance/runners/run-mixed-distributed.sh 200
```

## Common runner variables

| Variable | Purpose |
|---|---|
| `BASE_URL` | Application base URL |
| `FROM` / `TO` | Tested slot range |
| `LIMIT` | Slot page size |
| `TEST_DURATION` | Measurement duration |
| `WARM_UP_DURATION` | Read-only warm-up duration |
| `DEEP_CURSOR` | Cursor used by deep-page test |
| `BROWSE_RATE` | Browse-only percentage |
| `RESERVE_RATE` | Browse-and-reserve percentage |
| `CANCEL_RATE` | Browse-reserve-cancel percentage |
| `IDEMPOTENCY_MODE` | `off`, `unique`, or `retry` |
| `IDEMPOTENCY_RETRY_RATE` | Replay rate in retry mode |
| `ALLOW_PERFORMANCE_DATA_RESET` | Explicit permission for scoped mixed-test cleanup |
| `CLEANUP_AFTER` | Runs final scoped cleanup after the last level |
| `REDIS_UNLINK_BATCH_SIZE` | Redis cache-cleanup batch size, default `500` |
| `RESULT_DIR` | Result directory |
| `RESULT_FILE` | Custom single-run result file |
| `USER_START_INDEX` | First deterministic performance-user index |
| `USERNAME_PREFIX` | Performance-user prefix |
| `USERNAME_WIDTH` | Numeric suffix width |
| `USER_PASSWORD` | Performance-user login password |

The mixed ratios should normally total 100:

```text
BROWSE_RATE + RESERVE_RATE + CANCEL_RATE = 100
```

## Cleanup behavior

Before each mixed VU level, the runner performs scoped cleanup:

- only deterministic performance-user reservations
- only the configured time range
- seeded reservations are preserved
- related idempotency rows are deleted
- affected slots are restored only when no reservation remains
- known slot-cache keys are removed in Redis batches

The cleanup does not truncate tables or delete the persistent seeded dataset.

Read-only runners clear only confirmed slot-cache patterns before each level.

## Result files

Successful results use the normal filename:

```text
performance/results/<scenario>.json
```

When k6 completes but one or more thresholds fail, the new result is stored separately:

```text
performance/results/<scenario>-threshold-failed.json
```

A previous successful result with the normal filename is preserved.

When a later run succeeds, the stale threshold-failed file for the same scenario should be removed by the runner.

## Syntax checks

```bash
node --check performance/scripts/slots.js
node --check performance/scripts/reservation-flow.js

k6 inspect performance/scripts/slots.js
k6 inspect performance/scripts/reservation-flow.js

bash -n performance/runners/run-slots-first.sh
bash -n performance/runners/run-slots-deep.sh
bash -n performance/runners/run-mixed-distributed.sh
bash -n performance/runners/run-mixed-hotspot.sh
```

Make runners executable:

```bash
chmod +x performance/runners/*.sh
```

---

# Operational recommendations

## First local run

Use this order:

```text
1. Configure .env
2. Start MySQL and Redis
3. Export .env into the shell
4. Run the deterministic seeder once
5. Verify Swagger and GET /api/slots
6. Restart normally without the seeding flag
7. Run read-only performance tests
8. Run mixed distributed tests
9. Run hotspot tests last
```

## Ordinary development

- keep the persistent MySQL volume
- keep seeding disabled
- keep Redis enabled unless testing fallback behavior
- use a new idempotency UUID for each new reservation operation
- use the same UUID only for retrying the same reservation operation
- treat `nextCursor` as opaque
- keep slot ranges at or below 30 days

## Performance runs

- run the application and runners against the same database
- do not point runners at a different MySQL container than the application
- do not manually truncate or reseed between VU levels
- allow the runners to perform their scoped cleanup
- store generated files only under `performance/results/`
- remove or redact authentication tokens before sharing result files
- record branch, commit, Java version, k6 version, and test command with results

## Production hardening recommendations

Before production use:

- introduce versioned database migrations
- move secrets to a secret manager
- do not commit real JWT or database secrets
- define idempotency-record retention
- run Redis with production persistence and availability settings
- add automated tests
- add observability and alerting
- execute performance tests in an environment comparable to production

---

# Known limitations and design decisions

## Hibernate schema management

The current project uses:

```text
ddl-auto=update
```

This is convenient for the assignment and local development but does not provide production-grade, reviewable migrations.

## Idempotency retention

Idempotency snapshots currently have no TTL or cleanup policy.

This preserves replay behavior indefinitely but causes the table to grow over time.

## Redis scope

Redis accelerates first-page reads only.

It is intentionally not the source of truth and is not used to guarantee reservation correctness.

## Deep pages

Cursor pages bypass Redis and rely on the MySQL composite index.

## Deployment model

The repository currently runs the Spring Boot application on the host and containers only MySQL and Redis.

## Performance results

Local k6 results demonstrate the tested behavior of one environment. They are not production capacity guarantees.

---

# Troubleshooting

## `DB_PASSWORD` or `JWT_SECRET` is missing

Make sure `.env` has been loaded into the shell:

```bash
set -a
source .env
set +a
```

Check:

```bash
env | grep -E 'DB_|REDIS_|JWT_'
```

Do not print secrets in shared logs.

## MySQL port is already in use

Override the Docker host port and update `DB_URL` consistently:

```dotenv
MYSQL_PORT=3307
DB_URL='jdbc:mysql://127.0.0.1:3307/reservation_db?rewriteBatchedStatements=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
```

## Redis port is already in use

```dotenv
REDIS_PORT=6380
```

The Docker mapping and Spring application's `REDIS_PORT` must match.

## Seeder reports deterministic ID conflicts

The seeder intentionally refuses to overwrite conflicting structural rows.

Use a clean local database only when losing the current local data is acceptable, or inspect the diagnostic rows and resolve the collision manually.

## Slot test ranges have expired

Performance scripts use fixed 2026 date ranges. Reservation tests reject slots whose start time is before the current UTC time.

Update `FROM`, `TO`, and any deep cursor together when the test dataset's configured date range is no longer in the future.

## Redis is unavailable

Slot browsing should fall back to MySQL.

Reservation correctness is unaffected because Redis is not used as the concurrency authority.

## Accidentally deleting the benchmark dataset

This command deletes the persistent MySQL volume:

```bash
docker compose down -v
```

Do not run it unless a complete rebuild and reseed is intended.

---

# API documentation

With the application running:

- Swagger UI: `http://127.0.0.1:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://127.0.0.1:8080/v3/api-docs`

For the complete performance workflow, see:

```text
performance/README.md
```
