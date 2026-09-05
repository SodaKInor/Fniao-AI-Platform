#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT=$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)
CHECK_PATH=${CHECK_PATH:-$ROOT}
DOCKER_BIN=${DOCKER_BIN:-docker}
DOCKER_PROBE_TIMEOUT_SECONDS=${DOCKER_PROBE_TIMEOUT_SECONDS:-5}
BASELINE_IMAGE_BYTES=${BASELINE_IMAGE_BYTES:-1111539550}
IMAGE_SAFETY_MULTIPLE=${IMAGE_SAFETY_MULTIPLE:-3}
ABSOLUTE_MIN_FREE_BYTES=4294967296

is_unsigned_integer() {
  case "$1" in
    ''|*[!0-9]*) return 1 ;;
    *) return 0 ;;
  esac
}

for value in "$BASELINE_IMAGE_BYTES" "$IMAGE_SAFETY_MULTIPLE" "$DOCKER_PROBE_TIMEOUT_SECONDS"; do
  if ! is_unsigned_integer "$value"; then
    echo "BASELINE_IMAGE_BYTES, IMAGE_SAFETY_MULTIPLE and DOCKER_PROBE_TIMEOUT_SECONDS must be unsigned integers" >&2
    exit 64
  fi
done

calculated_min_free_bytes=$((BASELINE_IMAGE_BYTES * IMAGE_SAFETY_MULTIPLE))
if [ "$calculated_min_free_bytes" -lt "$ABSOLUTE_MIN_FREE_BYTES" ]; then
  calculated_min_free_bytes=$ABSOLUTE_MIN_FREE_BYTES
fi
MIN_FREE_BYTES=${MIN_FREE_BYTES:-$calculated_min_free_bytes}
if ! is_unsigned_integer "$MIN_FREE_BYTES" || [ "$MIN_FREE_BYTES" -eq 0 ]; then
  echo "MIN_FREE_BYTES must be a positive integer" >&2
  exit 64
fi

free_kib=$(df -Pk "$CHECK_PATH" | awk 'NR == 2 {print $4}')
if ! is_unsigned_integer "$free_kib"; then
  echo "Unable to determine free space for $CHECK_PATH" >&2
  exit 65
fi
free_bytes=$((free_kib * 1024))

docker_cli_available=false
docker_engine_ready=false
docker_probe_timed_out=false
if command -v "$DOCKER_BIN" >/dev/null 2>&1; then
  docker_cli_available=true
  "$DOCKER_BIN" info >/dev/null 2>&1 &
  docker_info_pid=$!
  elapsed=0
  while kill -0 "$docker_info_pid" >/dev/null 2>&1 && [ "$elapsed" -lt "$DOCKER_PROBE_TIMEOUT_SECONDS" ]; do
    sleep 1
    elapsed=$((elapsed + 1))
  done
  if kill -0 "$docker_info_pid" >/dev/null 2>&1; then
    docker_probe_timed_out=true
    kill "$docker_info_pid" >/dev/null 2>&1 || true
    wait "$docker_info_pid" 2>/dev/null || true
  elif wait "$docker_info_pid"; then
    docker_engine_ready=true
  fi
fi

reasons=
append_reason() {
  if [ -n "$reasons" ]; then
    reasons="$reasons,"
  fi
  reasons="$reasons\"$1\""
}

if [ "$docker_cli_available" != true ]; then
  append_reason "DOCKER_CLI_UNAVAILABLE"
elif [ "$docker_engine_ready" != true ]; then
  append_reason "DOCKER_ENGINE_UNAVAILABLE"
fi
if [ "$free_bytes" -lt "$MIN_FREE_BYTES" ]; then
  append_reason "HOST_FREE_SPACE_BELOW_REQUIRED_MINIMUM"
fi

status=PASS_READY
exit_code=0
if [ -n "$reasons" ]; then
  status=BLOCKED
  exit_code=2
fi

printf '{\n'
printf '  "status": "%s",\n' "$status"
printf '  "checkPath": "%s",\n' "$CHECK_PATH"
printf '  "dockerCliAvailable": %s,\n' "$docker_cli_available"
printf '  "dockerEngineReady": %s,\n' "$docker_engine_ready"
printf '  "dockerProbeTimedOut": %s,\n' "$docker_probe_timed_out"
printf '  "freeBytes": %s,\n' "$free_bytes"
printf '  "requiredFreeBytes": %s,\n' "$MIN_FREE_BYTES"
printf '  "baselineImageBytes": %s,\n' "$BASELINE_IMAGE_BYTES"
printf '  "imageSafetyMultiple": %s,\n' "$IMAGE_SAFETY_MULTIPLE"
printf '  "reasons": [%s],\n' "$reasons"
printf '  "destructiveCleanupPerformed": false\n'
printf '}\n'
exit "$exit_code"
