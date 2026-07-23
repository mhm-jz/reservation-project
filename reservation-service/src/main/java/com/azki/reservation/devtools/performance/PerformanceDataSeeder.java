package com.azki.reservation.devtools.performance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(
        name = "app.performance-seeding.enabled",
        havingValue = "true"
)
public class PerformanceDataSeeder implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(PerformanceDataSeeder.class);

    private static final int USER_COUNT = 10_000;
    private static final int SLOT_COUNT = 1_200_000;
    private static final int RESERVED_SLOT_COUNT = 360_000;
    private static final int BATCH_SIZE = 5_000;
    private static final int MINUTES_IN_YEAR = 365 * 24 * 60;
    private static final LocalDateTime SLOT_TIMELINE_START =
            LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final LocalDateTime CREATED_AT =
            LocalDateTime.of(2025, 12, 1, 0, 0);
    private static final String PASSWORD_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoO5u4SQQj8V9R1PpVCu2cMFRaYOv5zS.G";

    private final JdbcTemplate jdbcTemplate;

    public PerformanceDataSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        DatasetStatus before = inspectDataset();
        if (before.isComplete()) {
            log.info(
                    "Performance dataset already exists and is complete; " +
                            "skipping seeding ({})",
                    before
            );
            return;
        }

        log.info(
                "Performance dataset is absent or incomplete; safely resuming " +
                        "duplicate-safe seeding ({})",
                before
        );

        seedUsers();
        seedSlots();
        seedReservations();

        DatasetStatus after = inspectDataset();
        if (!after.isComplete()) {
            throw new IllegalStateException(
                    "Performance dataset remains incomplete after duplicate-safe " +
                            "seeding. Existing rows may conflict with deterministic " +
                            "performance IDs; no data was deleted or overwritten. " +
                            after
            );
        }

        log.info("Performance dataset seeding complete ({})", after);
    }

    private void seedUsers() {
        executeBatches(USER_COUNT, (statement, itemNumber) -> {
            statement.setLong(1, itemNumber);
            statement.setString(
                    2,
                    "perf-user-" + String.format("%05d", itemNumber)
            );
            statement.setString(3, PASSWORD_HASH);
            statement.setTimestamp(4, Timestamp.valueOf(CREATED_AT));
        }, """
                insert ignore into users (
                    id,
                    username,
                    password,
                    created_at
                ) values (?, ?, ?, ?)
                """);
    }

    private void seedSlots() {
        executeBatches(SLOT_COUNT, (statement, itemNumber) -> {
            LocalDateTime startTime = SLOT_TIMELINE_START.plusMinutes(
                    (itemNumber - 1L) % MINUTES_IN_YEAR
            );

            statement.setLong(1, itemNumber);
            statement.setTimestamp(2, Timestamp.valueOf(startTime));
            statement.setTimestamp(
                    3,
                    Timestamp.valueOf(startTime.plusMinutes(30))
            );
            statement.setBoolean(4, isReserved(itemNumber));
            statement.setTimestamp(5, Timestamp.valueOf(CREATED_AT));
        }, """
                insert ignore into available_slots (
                    id,
                    start_time,
                    end_time,
                    is_reserved,
                    created_at
                ) values (?, ?, ?, ?, ?)
                """);
    }

    private void seedReservations() {
        int[] reservedSlotIds = new int[RESERVED_SLOT_COUNT];
        int reservationIndex = 0;

        for (int slotId = 1; slotId <= SLOT_COUNT; slotId++) {
            if (isReserved(slotId)) {
                reservedSlotIds[reservationIndex++] = slotId;
            }
        }

        executeBatches(
                RESERVED_SLOT_COUNT,
                (statement, itemNumber) -> {
                    long slotId = reservedSlotIds[itemNumber - 1];
                    long userId = ((slotId - 1) % USER_COUNT) + 1;

                    statement.setLong(1, slotId);
                    statement.setLong(2, userId);
                    statement.setLong(3, slotId);
                    statement.setTimestamp(
                            4,
                            Timestamp.valueOf(CREATED_AT)
                    );
                },
                """
                        insert ignore into reservations (
                            id,
                            user_id,
                            slot_id,
                            created_at
                        ) values (?, ?, ?, ?)
                        """
        );
    }

    private void executeBatches(
            int totalItems,
            StatementBinder statementBinder,
            String sql
    ) {
        for (int batchStart = 1;
             batchStart <= totalItems;
             batchStart += BATCH_SIZE) {

            int firstItem = batchStart;
            int batchSize = Math.min(
                    BATCH_SIZE,
                    totalItems - batchStart + 1
            );

            jdbcTemplate.batchUpdate(
                    sql,
                    new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(
                                PreparedStatement statement,
                                int index
                        ) throws SQLException {
                            statementBinder.bind(
                                    statement,
                                    firstItem + index
                            );
                        }

                        @Override
                        public int getBatchSize() {
                            return batchSize;
                        }
                    }
            );
        }
    }

    private boolean isReserved(long slotId) {
        return slotId % 10 < 3;
    }

    private DatasetStatus inspectDataset() {
        long users = queryCount(
                """
                        select count(*)
                        from users
                        where id between 1 and ?
                          and username = concat(
                              'perf-user-',
                              lpad(id, 5, '0')
                          )
                        """,
                USER_COUNT
        );
        long slots = queryCount(
                """
                        select count(*)
                        from available_slots
                        where id between 1 and ?
                          and created_at = ?
                          and start_time = date_add(
                              ?,
                              interval mod(id - 1, ?) minute
                          )
                          and end_time = date_add(start_time, interval 30 minute)
                        """,
                SLOT_COUNT,
                Timestamp.valueOf(CREATED_AT),
                Timestamp.valueOf(SLOT_TIMELINE_START),
                MINUTES_IN_YEAR
        );
        long reservations = queryCount(
                """
                        select count(*)
                        from reservations r
                        join users u on u.id = r.user_id
                        where r.id between 1 and ?
                          and mod(r.id, 10) < 3
                          and r.slot_id = r.id
                          and r.created_at = ?
                          and u.id = mod(r.slot_id - 1, ?) + 1
                          and u.username = concat(
                              'perf-user-',
                              lpad(u.id, 5, '0')
                          )
                        """,
                SLOT_COUNT,
                Timestamp.valueOf(CREATED_AT),
                USER_COUNT
        );
        long reservedSlots = queryCount(
                """
                        select count(*)
                        from available_slots s
                        join reservations r
                          on r.id = s.id
                         and r.slot_id = s.id
                         and r.created_at = ?
                        where s.id between 1 and ?
                          and mod(s.id, 10) < 3
                          and s.is_reserved = true
                        """,
                Timestamp.valueOf(CREATED_AT),
                SLOT_COUNT
        );

        return new DatasetStatus(users, slots, reservations, reservedSlots);
    }

    private long queryCount(String sql, Object... arguments) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        return result == null ? 0L : result;
    }

    @FunctionalInterface
    private interface StatementBinder {

        void bind(
                PreparedStatement statement,
                int itemNumber
        ) throws SQLException;
    }

    private record DatasetStatus(
            long users,
            long slots,
            long reservations,
            long reservedSlots
    ) {

        private boolean isComplete() {
            return users == USER_COUNT
                    && slots == SLOT_COUNT
                    && reservations == RESERVED_SLOT_COUNT
                    && reservedSlots == RESERVED_SLOT_COUNT;
        }
    }
}
