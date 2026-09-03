#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
# shellcheck disable=SC1091
. "$SCRIPT_DIR/_common.sh"
load_env

fail() {
  echo "VERIFY FAILED: $*" >&2
  exit 1
}

echo "[1/10] Compose services"
compose ps
for service in mysql redis backend frontend; do
  wait_for_health "$service" 300 || fail "$service is not healthy"
done

echo "[2/10] MySQL and Redis authentication"
compose exec -T -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  mysql -u"$MYSQL_USER" -D"$MYSQL_DATABASE" -Nse 'SELECT 1' | grep -qx 1 \
  || fail "MySQL query failed"
compose exec -T redis redis-cli -a "$REDIS_PASSWORD" ping 2>/dev/null | grep -q PONG \
  || fail "Redis ping failed"

echo "[3/10] Business schema"
table_count=$(compose exec -T -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  mysql -u"$MYSQL_USER" -D"$MYSQL_DATABASE" -Nse \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$MYSQL_DATABASE'")
[ "$table_count" -ge 100 ] || fail "Only $table_count tables were initialized"
for table in sys_user sys_permission tab_ai_model; do
  present=$(compose exec -T -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
    mysql -u"$MYSQL_USER" -D"$MYSQL_DATABASE" -Nse "SHOW TABLES LIKE '$table'")
  [ "$present" = "$table" ] || fail "Missing business table $table"
done

echo "[4/10] Backend and same-origin API"
compose exec -T backend curl -fsS \
  "http://127.0.0.1:${BACKEND_INTERNAL_PORT:-8080}/jeecg-boot/doc.html" >/dev/null \
  || fail "Backend anonymous page failed"
base_url="http://${FRONTEND_BIND_ADDRESS:-127.0.0.1}:${FRONTEND_PORT:-8080}"
/usr/bin/curl -fsS "$base_url/" >/dev/null || fail "Frontend index failed"
/usr/bin/curl -fsS "$base_url/user/login" >/dev/null || fail "Vue history fallback failed"
/usr/bin/curl -fsS "$base_url/jeecg-boot/sys/randomImage/deploy-verify" >/dev/null \
  || fail "Nginx API proxy failed"

echo "[5/10] Sanitized local seed data"
legacy_binding_count=$(compose exec -T -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  mysql -u"$MYSQL_USER" -D"$MYSQL_DATABASE" -Nse \
  "SELECT (SELECT COUNT(*) FROM tab_ai_model_bund) + (SELECT COUNT(*) FROM sys_log)")
[ "$legacy_binding_count" -eq 0 ] || fail "Historical algorithm/log rows were not sanitized"

echo "[6/10] Production static-address scan"
bad_static=$(compose exec -T frontend sh -c \
  'for file in $(find /usr/share/nginx/html -type f \( -name "*.js" -o -name "*.html" -o -name "*.css" \)); do grep -lE "192\\.168\\.|127\\.0\\.0\\.1:(9998|8080|19091)|[A-Za-z]:\\\\(JAVAAI|Users)" "$file" || true; done')
[ -z "$bad_static" ] || fail "Forbidden addresses remain in: $bad_static"

echo "[7/10] Backend error and hardware-attempt scan"
backend_log=$(compose logs --no-color --tail=600 backend)
if printf '%s\n' "$backend_log" | grep -E \
  'BeanCreationException|UnsatisfiedDependencyException|Communications link failure|Unable to connect.*(mysql|redis)|Connection refused.*(mysql|redis)|Exception.*(VideoCapture|camera|microphone|PLC)' >/dev/null; then
  fail "Backend log contains startup/database/hardware errors"
fi
printf '%s\n' "$backend_log" | grep -q 'OpenCV native loading is disabled' \
  || fail "OpenCV-disabled startup marker not found"

echo "[8/10] Persistence probe before Compose restart"
probe_token=$(/usr/bin/openssl rand -hex 12)
compose exec -T -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  mysql -u"$MYSQL_USER" -D"$MYSQL_DATABASE" -e \
  "CREATE TABLE IF NOT EXISTS deploy_persistence_probe (id INT PRIMARY KEY, token VARCHAR(64) NOT NULL); REPLACE INTO deploy_persistence_probe VALUES (1, '$probe_token');"

echo "[9/10] Compose restart and persistence verification"
compose restart
for service in mysql redis backend frontend; do
  wait_for_health "$service" 600 || fail "$service did not recover after restart"
done
persisted=$(compose exec -T -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  mysql -u"$MYSQL_USER" -D"$MYSQL_DATABASE" -Nse \
  'SELECT token FROM deploy_persistence_probe WHERE id=1')
[ "$persisted" = "$probe_token" ] || fail "Database probe did not survive restart"
compose exec -T -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  mysql -u"$MYSQL_USER" -D"$MYSQL_DATABASE" -e 'DROP TABLE deploy_persistence_probe'

echo "[10/10] Final HTTP check"
/usr/bin/curl -fsS "$base_url/" >/dev/null || fail "Frontend failed after restart"
/usr/bin/curl -fsS "$base_url/jeecg-boot/sys/randomImage/deploy-verify-final" >/dev/null \
  || fail "API proxy failed after restart"

echo "VERIFY PASSED: core WGAI deployment is healthy and persistent"
