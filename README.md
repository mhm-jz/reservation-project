# Reservation Platform

A Spring Boot reservation service with MySQL persistence, Redis slot caching,
JWT authentication, cursor pagination, atomic reservation updates, and
database-backed idempotency.

The Java application runs directly on the host. Docker Compose provides MySQL
and Redis through `127.0.0.1`.

## Technology

- Java 21 and Spring Boot 3.5
- Spring MVC, Validation, Security, and Data JPA
- MySQL 8.4 and Redis 7.4
- JJWT and Springdoc OpenAPI
- MapStruct and Lombok

## Local configuration

The application reads these environment variables:

| Variable | Required | Default |
|---|---:|---|
| `JWT_SECRET` | Yes | None |
| `DB_PASSWORD` | Yes | None |
| `DB_URL` | No | `jdbc:mysql://127.0.0.1:3306/reservation_db` with batching and UTC options |
| `DB_USERNAME` | No | `reservation_user` |
| `REDIS_HOST` | No | `127.0.0.1` |
| `REDIS_PORT` | No | `6379` |

Docker Compose also requires `MYSQL_ROOT_PASSWORD` and uses:

- `MYSQL_DATABASE`, default `reservation_db`
- `MYSQL_PORT`, default `3306`
- `DB_USERNAME`, default `reservation_user`
- `DB_PASSWORD`
- `REDIS_PORT`, default `6379`

Keep local secrets outside Git. `JWT_SECRET` must be a sufficiently long
Base64-encoded HMAC key.

## Start locally

Load the local environment:

```shell
set -a
source .env
set +a
```

Start infrastructure only:

```shell
docker compose up -d reservation-mysql reservation-redis
```

Start the Java application on the host:

```shell
mvn -pl reservation-service spring-boot:run
```

The default addresses are:

- Application: `http://127.0.0.1:8080`
- MySQL: `127.0.0.1:3306`
- Redis: `127.0.0.1:6379`

MySQL initialization variables affect only a new data directory. Changing them
does not update credentials or data in the existing
`reservation_mysql_data` volume.

## API documentation

- Swagger UI: `http://127.0.0.1:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://127.0.0.1:8080/v3/api-docs`

Main endpoints:

| Method | Path | Authentication |
|---|---|---|
| `POST` | `/api/auth/register` | Public |
| `POST` | `/api/auth/login` | Public |
| `GET` | `/api/auth/me` | Bearer JWT |
| `GET` | `/api/slots` | Public |
| `POST` | `/api/reservations` | Bearer JWT |
| `DELETE` | `/api/reservations/{reservationId}` | Bearer JWT |

Smoke-check the public slot endpoint:

```shell
curl -fsS \
  "http://127.0.0.1:8080/api/slots?from=2026-07-26T00:00:00&to=2026-07-31T00:00:00&limit=20"
```

## Reservation idempotency

`POST /api/reservations` accepts an optional UUID `Idempotency-Key` header:

```shell
curl -i -X POST http://127.0.0.1:8080/api/reservations \
  -H "Authorization: Bearer <access-token>" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -H "Content-Type: application/json" \
  -d '{"slotId":123}'
```

Retry the same logical operation with the same authenticated user, key, and
body to receive the stored successful snapshot. Use a new UUID for each new
operation. Reusing a key for a different slot returns
`IDEMPOTENCY_KEY_REUSED`.

## Concurrency and expiration

Reservation correctness depends on an atomic conditional database update.
There is no Java synchronization or pessimistic lock.

An unavailable reservation attempt is classified as:

- `SLOT_NOT_FOUND` when the slot does not exist
- `SLOT_EXPIRED` when `startTime < now`
- `SLOT_UNAVAILABLE` otherwise

The exact boundary remains `startTime >= now`, so a slot equal to the captured
current time is eligible.

## Cursor pagination and caching

`GET /api/slots` uses keyset pagination ordered by `startTime`, then `id`.
Clients must treat `nextCursor` as opaque and pass it back unchanged.

First-page slot reads may use the Redis day-head cache. Cursor requests query
MySQL directly. Redis is an acceleration layer; failures fall back to MySQL.

## UTC convention

The system uses UTC across the JVM, the shared Java `Clock`, Hibernate, JDBC,
and MySQL sessions. API and database `LocalDateTime` values represent UTC
without a `Z` or numeric offset.

The JDBC URL preserves:

```text
rewriteBatchedStatements=true
connectionTimeZone=UTC
forceConnectionTimeZoneToSession=true
```

## Build

Compile without starting infrastructure:

```shell
mvn -pl reservation-service -DskipTests compile
```
