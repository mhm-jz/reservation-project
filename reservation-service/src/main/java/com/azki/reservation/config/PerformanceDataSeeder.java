package com.azki.reservation.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

@Component
@Profile("perf")
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
        log.info("Starting deterministic performance data seeding");

        seedUsers();
        seedSlots();
        seedReservations();
        printFinalCounts();
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

    private void printFinalCounts() {
        Long users = count("users");
        Long slots = count("available_slots");
        Long reservedSlots = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from available_slots
                        where is_reserved = true
                        """,
                Long.class
        );
        Long reservations = count("reservations");

        log.info(
                "Performance data counts: users={}, available_slots={}, " +
                        "reserved_slots={}, reservations={}",
                users,
                slots,
                reservedSlots,
                reservations
        );
    }

    private Long count(String tableName) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + tableName,
                Long.class
        );
    }

    @FunctionalInterface
    private interface StatementBinder {

        void bind(
                PreparedStatement statement,
                int itemNumber
        ) throws SQLException;
    }
}
