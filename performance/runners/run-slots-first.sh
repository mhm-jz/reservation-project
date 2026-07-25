#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
FROM="${FROM:-2026-06-01T00:00:00}"
TO="${TO:-2026-07-01T00:00:00}"
LIMIT="${LIMIT:-100}"
TEST_DURATION="${TEST_DURATION:-60s}"
WARM_UP_DURATION="${WARM_UP_DURATION:-10s}"
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
  local batch_size="${REDIS_UNLINK_BATCH_SIZE:-500}"
  local -a keys=()

  echo "Clearing Redis slot cache in batches..."

  while IFS= read -r key; do
    [[ -z "$key" ]] && continue

    keys+=("$key")

    if (( ${#keys[@]} >= batch_size )); then
      docker exec "$REDIS_CONTAINER" \
        redis-cli UNLINK "${keys[@]}" >/dev/null

      deleted=$((deleted + ${#keys[@]}))
      keys=()
    fi
  done < <(
    for pattern in \
      'slots:version:*' \
      'slots:head:*' \
      'slots:head-lock:*'
    do
      docker exec "$REDIS_CONTAINER" \
        redis-cli --scan --pattern "$pattern"
    done |
      awk 'NF' |
      sort -u
  )

  if (( ${#keys[@]} > 0 )); then
    docker exec "$REDIS_CONTAINER" \
      redis-cli UNLINK "${keys[@]}" >/dev/null

    deleted=$((deleted + ${#keys[@]}))
  fi

  echo "Deleted $deleted slot-cache Redis key(s)."
}

run_level() {
  local vus="$1"
  local result_file="$RESULT_DIR/results-first-vus-${vus}.json"
  local failed_result_file="${result_file%.json}-threshold-failed.json"
  local temporary_result_file

  temporary_result_file="$(mktemp "${result_file}.tmp.XXXXXX")"

  echo
  echo "Running first-page read test"
  echo "VU level: $vus"
  echo "Range: $FROM -> $TO"
  echo "Limit: $LIMIT"
  echo "Result: $result_file"

  clear_slot_cache

  if k6 run \
    --summary-export="$temporary_result_file" \
    -e BASE_URL="$BASE_URL" \
    -e PAGE_TYPE=first \
    -e FROM="$FROM" \
    -e TO="$TO" \
    -e LIMIT="$LIMIT" \
    -e TARGET_VUS="$vus" \
    -e TEST_DURATION="$TEST_DURATION" \
    -e WARM_UP_DURATION="$WARM_UP_DURATION" \
    performance/scripts/slots.js; then

    mv -f -- "$temporary_result_file" "$result_file"
    rm -f -- "$failed_result_file"

    echo "First-page test passed for ${vus} VUs."
    echo "Successful result saved to: $result_file"

  else
    local status=$?

    if [[ "$status" -eq 99 ]]; then
      if [[ -s "$temporary_result_file" ]]; then
        mv -f -- "$temporary_result_file" "$failed_result_file"

        echo "WARNING: k6 thresholds failed for ${vus} VUs." >&2
        echo "Threshold-failed result saved to:" >&2
        echo "$failed_result_file" >&2
      else
        rm -f -- "$temporary_result_file"

        echo "WARNING: thresholds failed, but no summary file was generated." >&2
      fi

      if [[ -e "$result_file" ]]; then
        echo "Previous successful result preserved:" >&2
        echo "$result_file" >&2
      fi

      overall_exit_code=99
    else
      rm -f -- "$temporary_result_file"

      echo "k6 failed with exit code $status." >&2

      if [[ -e "$result_file" ]]; then
        echo "Previous successful result preserved:" >&2
        echo "$result_file" >&2
      fi

      exit "$status"
    fi
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
