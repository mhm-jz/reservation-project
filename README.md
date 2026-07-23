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

See [performance/README.md](performance/README.md) for the full workflow and
shared-data cleanup safeguards.
