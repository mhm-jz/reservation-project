# Reservation Platform

## Shared development and performance infrastructure

Start the normal persistent MySQL and Redis services:

```shell
docker compose up -d reservation-mysql reservation-redis
```

Performance seeding is disabled during normal startup. For first-time or repair
seeding, enable it explicitly:

```shell
APP_PERFORMANCE_SEEDING_ENABLED=true \
./mvnw -pl reservation-service spring-boot:run
```

The seeder uses deterministic, duplicate-safe JDBC batches and skips a complete
dataset. Normal subsequent startup needs no performance profile or seeding
variable:

```shell
./mvnw -pl reservation-service spring-boot:run
```

## API documentation

With the application running, Swagger UI is available at
`http://127.0.0.1:8080/swagger-ui/index.html` and the OpenAPI JSON document at
`http://127.0.0.1:8080/v3/api-docs`.

An authenticated reservation can use an optional idempotency key:

```shell
curl -i -X POST http://127.0.0.1:8080/api/reservations \
  -H "Authorization: Bearer <access-token>" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -H "Content-Type: application/json" \
  -d '{"slotId":123}'
```

Retry the same body with the same UUID to receive the original successful
snapshot. Use a new UUID for each new reservation operation. Idempotency records
have no expiration policy, and cancellation does not remove the snapshot.

See [performance/README.md](performance/README.md) for the full workflow and
shared-data cleanup safeguards.
