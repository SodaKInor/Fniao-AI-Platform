#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
# shellcheck disable=SC1091
. "$SCRIPT_DIR/_common.sh"
load_env

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 /absolute/path/outside-repository/backup.sql" >&2
  exit 2
fi

destination=$1
case "$destination" in
  /*) ;;
  *) echo "Backup destination must be absolute" >&2; exit 2 ;;
esac
destination_dir=$(CDPATH= cd -- "$(dirname -- "$destination")" && pwd)
case "$destination_dir/" in
  "$REPO_ROOT/"*) echo "Database backups must stay outside the repository" >&2; exit 2 ;;
esac

temporary=$(mktemp "$destination_dir/.fniao-database-backup.XXXXXX")
trap 'rm -f "$temporary"' EXIT HUP INT TERM
compose exec -T -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  mysqldump -u"$MYSQL_USER" --single-transaction --routines --triggers "$MYSQL_DATABASE" \
  > "$temporary"
chmod 0600 "$temporary"
mv "$temporary" "$destination"
trap - EXIT HUP INT TERM
echo "Database backup written outside the repository: $destination"
