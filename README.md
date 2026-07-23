# Reservation Platform

## Performance-test database

Start the isolated MySQL service:

```shell
docker compose up -d reservation-mysql-perf
```

After MySQL is ready, run the application once with the `perf` profile and
explicit seeding flag:

```shell
mvn -pl reservation-service spring-boot:run \
  -Dspring-boot.run.profiles=perf \
  -Dspring-boot.run.arguments=--app.performance-seeding.enabled=true
```

The performance database uses port `3307` and the `reservation_perf` schema, so
the normal development database on port `3306` is not modified. The seeder uses
deterministic, idempotent JDBC batches and logs final users, slots, reserved
slots, and reservations counts. Stop the application after seeding completes.
