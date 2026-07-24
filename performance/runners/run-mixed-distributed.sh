#!/usr/bin/env bash
set -euo pipefail

WORKLOAD_MODE="distributed"

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
FROM="${FROM:-2026-08-01T00:00:00}"
TO="${TO:-2026-08-31T00:00:00}"
LIMIT="${LIMIT:-10}"
TEST_DURATION="${TEST_DURATION:-60s}"
THINK_TIME="${THINK_TIME:-0.1}"

BROWSE_RATE="${BROWSE_RATE:-80}"
RESERVE_RATE="${RESERVE_RATE:-15}"
CANCEL_RATE="${CANCEL_RATE:-5}"

USER_START_INDEX="${USER_START_INDEX:-1}"
USERNAME_PREFIX="${USERNAME_PREFIX:-perf-user-}"
USERNAME_WIDTH="${USERNAME_WIDTH:-5}"
USER_PASSWORD="${USER_PASSWORD:-TestPassword123}"

MYSQL_CONTAINER="${MYSQL_CONTAINER:-reservation-mysql}"
REDIS_CONTAINER="${REDIS_CONTAINER:-reservation-redis}"
DB_NAME="${DB_NAME:-reservation_db}"
DB_USER="${DB_USER:-reservation_user}"
DB_PASSWORD="${DB_PASSWORD:-reservation_password}"
SEED_RESERVATION_TIMESTAMP="${SEED_RESERVATION_TIMESTAMP:-2025-12-01 00:00:00}"
ALLOW_PERFORMANCE_DATA_RESET="${ALLOW_PERFORMANCE_DATA_RESET:-false}"

RESULT_DIR="${RESULT_DIR:-performance/results}"
CLEANUP_AFTER="${CLEANUP_AFTER:-false}"

VUS_LEVELS=("$@")
overall_exit_code=0
if [[ ${#VUS_LEVELS[@]} -eq 0 ]]; then
  VUS_LEVELS=(20 50 100 200)
fi

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command not found: $1" >&2
    exit 1
  }
}

require_running_container() {
  local container="$1"
  local running
  running="$(docker inspect -f '{{.State.Running}}' "$container" 2>/dev/null || true)"

  if [[ "$running" != "true" ]]; then
    echo "Required container is not running: $container" >&2
    exit 1
  fi
}

validate_environment() {
  require_command docker
  require_command k6

  if [[ "$ALLOW_PERFORMANCE_DATA_RESET" != "true" ]]; then
    echo "Refusing performance cleanup without ALLOW_PERFORMANCE_DATA_RESET=true" >&2
    exit 1
  fi

  if [[ ! "$USERNAME_PREFIX" =~ ^[[:alnum:]-]+$ ]]; then
    echo "USERNAME_PREFIX may contain only letters, numbers, and hyphens" >&2
    exit 1
  fi

  if [[ ! "$FROM" =~ ^[0-9T:-]+$ || ! "$TO" =~ ^[0-9T:-]+$ ]]; then
    echo "FROM and TO must use an ISO-like date-time format." >&2
    exit 1
  fi

  if [[ ! "$SEED_RESERVATION_TIMESTAMP" =~ ^[0-9\ :-]+$ ]]; then
    echo "SEED_RESERVATION_TIMESTAMP has an invalid format." >&2
    exit 1
  fi

  docker info >/dev/null
  require_running_container "$MYSQL_CONTAINER"
  require_running_container "$REDIS_CONTAINER"

  if ! docker exec "$MYSQL_CONTAINER" \
    mysqladmin ping \
    -h 127.0.0.1 \
    -u"$DB_USER" \
    -p"$DB_PASSWORD" \
    --silent; then
    echo "MySQL container is not ready." >&2
    exit 1
  fi

  if [[ "$(docker exec "$REDIS_CONTAINER" redis-cli PING)" != "PONG" ]]; then
    echo "Redis did not respond with PONG." >&2
    exit 1
  fi

  mkdir -p "$RESULT_DIR"

  echo "Workload mode: $WORKLOAD_MODE"
  echo "Database: $DB_NAME"
  echo "MySQL container: $MYSQL_CONTAINER"
  echo "Redis container: $REDIS_CONTAINER"
  echo "Range: $FROM -> $TO"
}

reset_performance_data() {
  local mysql_from="${FROM/T/ }"
  local mysql_to="${TO/T/ }"

  echo
  echo "Resetting load-test data in $DB_NAME..."

  docker exec -i "$MYSQL_CONTAINER" \
    mysql \
    -u"$DB_USER" \
    -p"$DB_PASSWORD" \
    "$DB_NAME" <<SQL
START TRANSACTION;

CREATE TEMPORARY TABLE load_test_slot_ids (
    slot_id BIGINT PRIMARY KEY
);

INSERT IGNORE INTO load_test_slot_ids (slot_id)
SELECT DISTINCT r.slot_id
FROM reservations r
JOIN users u ON u.id = r.user_id
JOIN available_slots s ON s.id = r.slot_id
WHERE u.username LIKE '${USERNAME_PREFIX}%'
  AND r.created_at <> '${SEED_RESERVATION_TIMESTAMP}'
  AND s.start_time >= '${mysql_from}'
  AND s.start_time < '${mysql_to}';

SET @selected_reservations = ROW_COUNT();

DELETE r
FROM reservations r
JOIN load_test_slot_ids t ON t.slot_id = r.slot_id
JOIN users u ON u.id = r.user_id
WHERE u.username LIKE '${USERNAME_PREFIX}%'
  AND r.created_at <> '${SEED_RESERVATION_TIMESTAMP}';

SET @deleted_reservations = ROW_COUNT();

UPDATE available_slots s
JOIN load_test_slot_ids t ON t.slot_id = s.id
SET s.is_reserved = FALSE
WHERE NOT EXISTS (
    SELECT 1
    FROM reservations remaining
    WHERE remaining.slot_id = s.id
);

SET @restored_slots = ROW_COUNT();

COMMIT;

SELECT
    @selected_reservations AS selected_performance_reservations,
    @deleted_reservations AS deleted_performance_reservations,
    @restored_slots AS restored_performance_slots;
SQL

  clear_slot_cache
  echo "Authorized performance-user cleanup completed."
}

clear_slot_cache() {
  local pattern
  local key
  local deleted=0

  for pattern in 'slots:version:*' 'slots:head:*' 'slots:head-lock:*'; do
    while IFS= read -r key; do
      [[ -z "$key" ]] && continue
      docker exec "$REDIS_CONTAINER" redis-cli UNLINK "$key" >/dev/null
      ((deleted += 1))
    done < <(docker exec "$REDIS_CONTAINER" redis-cli --scan --pattern "$pattern")
  done

  echo "Deleted $deleted slot-cache Redis key(s)."
}

run_level() {
  local vus="$1"
  local result_file="$RESULT_DIR/results-mixed-${WORKLOAD_MODE}-vus-${vus}.json"

  echo
  echo "Running mixed ${WORKLOAD_MODE} test"
  echo "VU level: $vus"
  echo "Duration: $TEST_DURATION"
  echo "Result: $result_file"

  if k6 run \
    --summary-export="$result_file" \
    -e BASE_URL="$BASE_URL" \
    -e FROM="$FROM" \
    -e TO="$TO" \
    -e WORKLOAD_MODE="$WORKLOAD_MODE" \
    -e TARGET_VUS="$vus" \
    -e USER_POOL_SIZE="$vus" \
    -e USER_START_INDEX="$USER_START_INDEX" \
    -e USERNAME_PREFIX="$USERNAME_PREFIX" \
    -e USERNAME_WIDTH="$USERNAME_WIDTH" \
    -e USER_PASSWORD="$USER_PASSWORD" \
    -e TEST_DURATION="$TEST_DURATION" \
    -e LIMIT="$LIMIT" \
    -e THINK_TIME="$THINK_TIME" \
    -e BROWSE_RATE="$BROWSE_RATE" \
    -e RESERVE_RATE="$RESERVE_RATE" \
    -e CANCEL_RATE="$CANCEL_RATE" \
    performance/scripts/reservation-flow.js; then
    echo "Mixed ${WORKLOAD_MODE} test passed for ${vus} VUs."
  else
    local status=$?

    if [[ "$status" -eq 99 ]]; then
      echo "WARNING: k6 thresholds failed for ${vus} VUs; continuing." >&2
      overall_exit_code=99
    else
      echo "k6 failed with exit code $status." >&2
      exit "$status"
    fi
  fi
}

validate_environment

for vus in "${VUS_LEVELS[@]}"; do
  reset_performance_data
  run_level "$vus"
done

if [[ "$CLEANUP_AFTER" == "true" ]]; then
  reset_performance_data
  echo "Final cleanup completed."
fi

exit "$overall_exit_code"
