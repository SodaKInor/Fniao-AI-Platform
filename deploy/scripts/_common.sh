#!/bin/sh

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DEPLOY_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
WORKSPACE_DIR=$(CDPATH= cd -- "$DEPLOY_DIR/.." && pwd)
ENV_FILE="$DEPLOY_DIR/.env"
COMPOSE_FILE="$DEPLOY_DIR/docker-compose.yml"

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
