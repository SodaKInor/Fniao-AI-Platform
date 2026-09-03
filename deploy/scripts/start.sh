#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
# shellcheck disable=SC1091
. "$SCRIPT_DIR/_common.sh"

if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
  "$SCRIPT_DIR/bootstrap.sh"
fi

if [ ! -f "$ENV_FILE" ]; then
  mysql_root_password=$(/usr/bin/openssl rand -hex 24)
  mysql_password=$(/usr/bin/openssl rand -hex 24)
  redis_password=$(/usr/bin/openssl rand -hex 24)
  temp_env="${ENV_FILE}.tmp"
  sed \
    -e "s/^MYSQL_ROOT_PASSWORD=.*/MYSQL_ROOT_PASSWORD=$mysql_root_password/" \
    -e "s/^MYSQL_PASSWORD=.*/MYSQL_PASSWORD=$mysql_password/" \
    -e "s/^REDIS_PASSWORD=.*/REDIS_PASSWORD=$redis_password/" \
    "$DEPLOY_DIR/.env.example" > "$temp_env"
  mv "$temp_env" "$ENV_FILE"
  chmod 600 "$ENV_FILE"
  echo "Generated local secrets in $ENV_FILE"
fi

if grep -q 'CHANGE_ME_' "$ENV_FILE"; then
  echo "$ENV_FILE still contains placeholder secrets" >&2
  exit 1
fi

load_env
compose config --quiet

echo "Building MySQL initialization image"
compose build mysql
echo "Building backend"
compose build backend
echo "Building frontend"
compose build frontend

echo "Starting MySQL and Redis"
compose up -d mysql redis
wait_for_health mysql 600
wait_for_health redis 180

echo "Starting backend"
compose up -d backend
wait_for_health backend 600

echo "Starting frontend"
compose up -d frontend
wait_for_health frontend 180

compose up -d
compose ps
echo "WGAI is available at http://${FRONTEND_BIND_ADDRESS:-127.0.0.1}:${FRONTEND_PORT:-8080}"
