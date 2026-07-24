package com.azki.reservation.devtools.performance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(
        name = "app.performance-seeding.enabled",
        havingValue = "true"
)
public class PerformanceDataSeeder implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(PerformanceDataSeeder.class);

    private static final int USER_COUNT = 10_000;
    private static final int FIRST_USER_ID = 1;
    private static final String USERNAME_PREFIX = "perf-user-";
    private static final int SLOT_COUNT = 1_200_000;
    private static final int RESERVED_SLOT_COUNT = 360_000;
    private static final int BATCH_SIZE = 5_000;
    private static final int MINUTES_IN_YEAR = 365 * 24 * 60;
    private static final LocalDateTime SLOT_TIMELINE_START =
            LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final LocalDateTime CREATED_AT =
            LocalDateTime.of(2025, 12, 1, 0, 0);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final String configuredPerformancePassword;

    public PerformanceDataSeeder(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            @Value("${app.performance-seeding.user-password}")
            String configuredPerformancePassword
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.configuredPerformancePassword = configuredPerformancePassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        DatasetStatus before = inspectDataset();
        if (before.isComplete()) {
            log.info(
                    "Performance dataset and credentials are complete; " +
                            "skipping seeding ({})",
                    before
            );
            return;
        }

        String encodedPerformancePassword = null;
        DatasetStatus current = before;

        if (!before.isStructureComplete()) {
            log.info(
                    "Performance dataset structure is absent or incomplete; " +
                            "safely resuming duplicate-safe seeding ({})",
                    before
            );

            encodedPerformancePassword =
                    passwordEncoder.encode(configuredPerformancePassword);
            seedUsers(encodedPerformancePassword);
            seedSlots();
            seedReservations();

            current = inspectDataset();
            if (!current.isStructureComplete()) {
                logStructuralConflictDiagnostics();
                throw new IllegalStateException(
                        "Performance dataset remains structurally incomplete after " +
                                "duplicate-safe seeding. Existing rows may conflict " +
                                "with deterministic performance IDs; no data was " +
                                "deleted or overwritten. " + current
                );
            }
            log.info("Performance dataset structure is complete ({})", current);
        } else {
            log.info("Performance dataset structure is complete ({})", before);
        }

        if (!current.credentialsValid()) {
            log.info(
                    "Performance credentials are invalid; repairing credentials " +
                            "for {} deterministic performance users",
                    current.users()
            );

            if (encodedPerformancePassword == null) {
                encodedPerformancePassword =
                        passwordEncoder.encode(configuredPerformancePassword);
            }

            int repairedUsers =
                    repairPerformanceCredentials(encodedPerformancePassword);
            if (!inspectCredentials(USER_COUNT)) {
                throw new IllegalStateException(
                        "Performance credential repair could not be verified; " +
                                "no non-password data was modified"
                );
            }

            log.info(
                    "Performance credentials repaired successfully for {} users",
                    repairedUsers
            );
        } else {
            log.info("Performance credentials are already valid; no update needed");
        }

        log.info("Performance dataset is complete");
    }

    private void seedUsers(String encodedPerformancePassword) {
        executeBatches(USER_COUNT, (statement, itemNumber) -> {
            statement.setLong(1, itemNumber);
            statement.setString(
                    2,
                    USERNAME_PREFIX + String.format("%05d", itemNumber)
            );
            statement.setString(3, encodedPerformancePassword);
            statement.setObject(4, CREATED_AT);
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
            statement.setObject(2, startTime);
            statement.setObject(
                    3,
                    startTime.plusMinutes(30)
            );
            statement.setBoolean(4, isReserved(itemNumber));
            statement.setObject(5, CREATED_AT);
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
                    statement.setObject(
                            4,
                            CREATED_AT
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
                        where id between ? and ?
                          and username = concat(
                              ?,
                              lpad(id, 5, '0')
                          )
                        """,
                FIRST_USER_ID,
                USER_COUNT,
                USERNAME_PREFIX
        );
        boolean credentialsValid = inspectCredentials(users);
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
                CREATED_AT,
                SLOT_TIMELINE_START,
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
                CREATED_AT,
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
                CREATED_AT,
                SLOT_COUNT
        );

        return new DatasetStatus(
                users,
                slots,
                reservations,
                reservedSlots,
                credentialsValid
        );
    }

    private boolean inspectCredentials(long deterministicUsers) {
        if (deterministicUsers != USER_COUNT) {
            return false;
        }

        List<String> passwordHashes = jdbcTemplate.queryForList(
                """
                        select distinct password
                        from users
                        where id between ? and ?
                          and username = concat(
                              ?,
                              lpad(id, 5, '0')
                          )
                        """,
                String.class,
                FIRST_USER_ID,
                USER_COUNT,
                USERNAME_PREFIX
        );

        if (passwordHashes.size() != 1) {
            return false;
        }

        String storedHash = passwordHashes.getFirst();
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }

        try {
            return passwordEncoder.matches(
                    configuredPerformancePassword,
                    storedHash
            );
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private int repairPerformanceCredentials(
            String encodedPerformancePassword
    ) {
        return jdbcTemplate.update(
                """
                        update users
                        set password = ?
                        where id between ? and ?
                          and username = concat(
                              ?,
                              lpad(id, 5, '0')
                          )
                        """,
                encodedPerformancePassword,
                FIRST_USER_ID,
                USER_COUNT,
                USERNAME_PREFIX
        );
    }

    private void logStructuralConflictDiagnostics() {
        logConflictSample(
                "performance user ID conflicts",
                """
                        select id, username, created_at
                        from users
                        where id between ? and ?
                          and username <> concat(
                              ?,
                              lpad(id, 5, '0')
                          )
                        order by id
                        limit 10
                        """,
                FIRST_USER_ID,
                USER_COUNT,
                USERNAME_PREFIX
        );
        logConflictSample(
                "performance usernames assigned to unexpected IDs",
                """
                        select id, username, created_at
                        from users
                        where username between ? and ?
                          and username <> concat(
                              ?,
                              lpad(id, 5, '0')
                          )
                        order by username
                        limit 10
                        """,
                USERNAME_PREFIX + String.format("%05d", FIRST_USER_ID),
                USERNAME_PREFIX + String.format("%05d", USER_COUNT),
                USERNAME_PREFIX
        );
        logConflictSample(
                "performance slot ID conflicts",
                """
                        select id, start_time, end_time, is_reserved, created_at
                        from available_slots
                        where id between 1 and ?
                          and not (
                              created_at = ?
                              and start_time = date_add(
                                  ?,
                                  interval mod(id - 1, ?) minute
                              )
                              and end_time = date_add(
                                  start_time,
                                  interval 30 minute
                              )
                          )
                        order by id
                        limit 10
                        """,
                SLOT_COUNT,
                CREATED_AT,
                SLOT_TIMELINE_START,
                MINUTES_IN_YEAR
        );
        logConflictSample(
                "performance reservation ID conflicts",
                """
                        select r.id, r.user_id, r.slot_id, r.created_at
                        from reservations r
                        left join users u on u.id = r.user_id
                        where r.id between 1 and ?
                          and mod(r.id, 10) < 3
                          and not coalesce((
                              r.slot_id = r.id
                              and r.created_at = ?
                              and u.id = mod(r.slot_id - 1, ?) + 1
                              and u.username = concat(
                                  ?,
                                  lpad(u.id, 5, '0')
                              )
                          ), false)
                        order by r.id
                        limit 10
                        """,
                SLOT_COUNT,
                CREATED_AT,
                USER_COUNT,
                USERNAME_PREFIX
        );
        logConflictSample(
                "performance reservation slot conflicts",
                """
                        select id, user_id, slot_id, created_at
                        from reservations
                        where slot_id between 1 and ?
                          and mod(slot_id, 10) < 3
                          and id <> slot_id
                        order by slot_id
                        limit 10
                        """,
                SLOT_COUNT
        );
    }

    private void logConflictSample(
            String description,
            String sql,
            Object... arguments
    ) {
        List<Map<String, Object>> conflicts =
                jdbcTemplate.queryForList(sql, arguments);

        if (!conflicts.isEmpty()) {
            log.error("{} (up to 10): {}", description, conflicts);
        }
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
            long reservedSlots,
            boolean credentialsValid
    ) {

        private boolean isStructureComplete() {
            return users == USER_COUNT
                    && slots == SLOT_COUNT
                    && reservations == RESERVED_SLOT_COUNT
                    && reservedSlots == RESERVED_SLOT_COUNT;
        }

        private boolean isComplete() {
            return isStructureComplete() && credentialsValid;
        }
    }
}
