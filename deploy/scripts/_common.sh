#!/bin/sh

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)
DEPLOY_DIR="$REPO_ROOT/deploy"
WORKSPACE_DIR="$REPO_ROOT"
ENV_FILE="$DEPLOY_DIR/.env"
COMPOSE_FILE="$DEPLOY_DIR/docker-compose.yml"

if [ "$SCRIPT_DIR" != "$DEPLOY_DIR/scripts" ]; then
  echo "Deployment scripts must run from the current Git repository" >&2
  exit 1
fi

compose() {
  docker compose --project-directory "$DEPLOY_DIR" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

load_env() {
  if [ ! -f "$ENV_FILE" ]; then
    echo "Missing $ENV_FILE; run deploy/scripts/start.sh first" >&2
    exit 1
  fi
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
}

require_database_baseline() {
  baseline=${DATABASE_BASELINE_FILE:-../database/private/java_ai.sql}
  case "$baseline" in
    /*) baseline_path=$baseline ;;
    *) baseline_path="$DEPLOY_DIR/$baseline" ;;
  esac
  if [ ! -f "$baseline_path" ]; then
    echo "Missing private database baseline: $baseline_path" >&2
    echo "Place an authorized local snapshot at database/private/java_ai.sql or set DATABASE_BASELINE_FILE in deploy/.env." >&2
    exit 1
  fi
}

wait_for_health() {
  service=$1
  timeout_seconds=${2:-300}
  elapsed=0
  while [ "$elapsed" -lt "$timeout_seconds" ]; do
    container_id=$(compose ps -q "$service" 2>/dev/null || true)
    if [ -n "$container_id" ]; then
      status=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id" 2>/dev/null || true)
      case "$status" in
        healthy|running)
          echo "$service is $status"
          return 0
          ;;
        unhealthy|exited|dead)
          echo "$service entered state $status" >&2
          compose logs --tail=120 "$service" >&2 || true
          return 1
          ;;
      esac
    fi
    sleep 5
    elapsed=$((elapsed + 5))
  done
  echo "Timed out waiting for $service health" >&2
  compose logs --tail=120 "$service" >&2 || true
  return 1
}
