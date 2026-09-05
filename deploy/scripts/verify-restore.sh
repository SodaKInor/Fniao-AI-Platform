#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
# shellcheck disable=SC1091
. "$SCRIPT_DIR/_common.sh"

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
  echo "Usage: $0 /absolute/path/private-baseline.sql [report.json]" >&2
  exit 2
fi

baseline=$1
report=${2:-database/validation/final-run.json}
base_commit=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["baseCommit"])' \
  "$REPO_ROOT/database/MIGRATIONS.json")
exec python3 "$REPO_ROOT/database/verify_database.py" \
  --base "$base_commit" \
  --baseline "$baseline" \
  --report "$report"
