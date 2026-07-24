#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
FROM="${FROM:-2026-06-01T00:00:00}"
TO="${TO:-2026-07-01T00:00:00}"
LIMIT="${LIMIT:-100}"
DEEP_CURSOR="${DEEP_CURSOR:-eyJzdGFydFRpbWUiOiIyMDI2LTA2LTI4VDIzOjU4OjAwIiwiaWQiOjc4MzM1OX0}"
TEST_DURATION="${TEST_DURATION:-60s}"
WARM_UP_DURATION="${WARM_UP_DURATION:-10s}"
AUTH_USERNAME="${AUTH_USERNAME:-k6-load-test-user-new}"
AUTH_PASSWORD="${AUTH_PASSWORD:-TestPassword123}"
REDIS_CONTAINER="${REDIS_CONTAINER:-reservation-redis}"
RESULT_DIR="${RESULT_DIR:-performance/results}"

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

count_head_cache_keys() {
  local pattern
  local key
  local count=0

  for pattern in 'slots:head:*' 'slots:head-lock:*'; do
    while IFS= read -r key; do
      [[ -z "$key" ]] && continue
      ((count += 1))
    done < <(docker exec "$REDIS_CONTAINER" redis-cli --scan --pattern "$pattern")
  done

  echo "$count"
}

run_level() {
  local vus="$1"
  local result_file="$RESULT_DIR/results-deep-vus-${vus}.json"

  echo
  echo "Running deep-cursor read test"
  echo "VU level: $vus"
  echo "Range: $FROM -> $TO"
  echo "Limit: $LIMIT"
  echo "Result: $result_file"

  clear_slot_cache

  if k6 run \
    --summary-export="$result_file" \
    -e BASE_URL="$BASE_URL" \
    -e PAGE_TYPE=deep \
    -e FROM="$FROM" \
    -e TO="$TO" \
    -e LIMIT="$LIMIT" \
    -e DEEP_CURSOR="$DEEP_CURSOR" \
    -e TARGET_VUS="$vus" \
    -e TEST_DURATION="$TEST_DURATION" \
    -e WARM_UP_DURATION="$WARM_UP_DURATION" \
    -e AUTH_USERNAME="$AUTH_USERNAME" \
    -e AUTH_PASSWORD="$AUTH_PASSWORD" \
    performance/scripts/slots.js; then
    echo "Deep-cursor test passed for ${vus} VUs."
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

  local head_cache_keys
  head_cache_keys="$(count_head_cache_keys)"

  if [[ "$head_cache_keys" != "0" ]]; then
    echo "WARNING: found $head_cache_keys slot head-cache key(s) after a cursor-only run." >&2
  else
    echo "No slot head-cache keys were created; cursor-cache bypass confirmed."
  fi
}

require_command docker
require_command k6
mkdir -p "$RESULT_DIR"

docker info >/dev/null
require_running_container "$REDIS_CONTAINER"

if [[ "$(docker exec "$REDIS_CONTAINER" redis-cli PING)" != "PONG" ]]; then
  echo "Redis did not respond with PONG." >&2
  exit 1
fi

for vus in "${VUS_LEVELS[@]}"; do
  run_level "$vus"
done

exit "$overall_exit_code"
