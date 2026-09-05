#!/usr/bin/env python3
import argparse
import hashlib
import json
from pathlib import Path
import secrets
import subprocess
import sys
import time
from datetime import datetime, timezone


DATABASE = Path(__file__).resolve().parent
ROOT = DATABASE.parent
MANIFEST = DATABASE / "MIGRATIONS.json"
ALLOWED_NON_DATABASE_PATHS = {
    "deploy/db/002-local-sanitize.sql",
    "apps/backend/deploy/remote-ai/migrations/V001__04a_assets_jobs.sql",
    "apps/backend/deploy/remote-ai/migrations/V002__04a_video_stream.sql",
    "apps/backend/deploy/remote-ai/stub-bindings.example.sql",
}


def command(args, *, data=None, input_file=None, check=True):
    kwargs = {"cwd": ROOT, "stdout": subprocess.PIPE, "stderr": subprocess.PIPE}
    if data is not None:
        kwargs["input"] = data
    elif input_file is not None:
        kwargs["stdin"] = input_file
    result = subprocess.run(args, **kwargs)
    if check and result.returncode:
        message = result.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(f"command failed ({args[0]}): {message[-1200:]}")
    return result


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_and_check_manifest(base):
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    if manifest["baseCommit"] != base:
        raise RuntimeError("manifest baseCommit does not match --base")
    entries = manifest["execution"]
    if [item["order"] for item in entries] != [1, 2, 3, 4]:
        raise RuntimeError("manifest execution order is not contiguous")
    if [item["version"] for item in entries] != [
        "bootstrap-002", "V001", "V002", "stub-bindings-v1"
    ]:
        raise RuntimeError("manifest version order changed")
    if entries[2]["previousVersion"] != "V001":
        raise RuntimeError("V002 must declare V001 as its predecessor")
    observed = {}
    for item in entries:
        target = ROOT / item["target"]
        actual = sha256(target)
        if actual != item["sha256"]:
            raise RuntimeError(f"checksum mismatch: {item['target']}")
        observed[item["version"]] = actual
    return manifest, observed


def check_scope(base):
    result = command(["git", "diff", "--name-only", base, "--"])
    changed = [line for line in result.stdout.decode().splitlines() if line]
    unexpected = [
        path for path in changed
        if not path.startswith("database/") and path not in ALLOWED_NON_DATABASE_PATHS
    ]
    if unexpected:
        raise RuntimeError("out-of-scope paths: " + ", ".join(unexpected))
    return changed


def check_private_and_codegen():
    tracked = command(["git", "ls-files", "database/private"]).stdout.decode().splitlines()
    allowed = {"database/private/.gitignore", "database/private/README.md"}
    if set(tracked) != allowed:
        raise RuntimeError("database/private may track only README.md and .gitignore")
    codegen = command(["git", "ls-files", "*_menu_insert.sql"]).stdout.decode().splitlines()
    if len(codegen) != 26 or any(path.startswith("database/") for path in codegen):
        raise RuntimeError("code-generator SQL ownership changed")
    return len(codegen)


class IsolatedMysql:
    def __init__(self, image):
        self.image = image
        self.name = "fniao-08db-" + secrets.token_hex(6)
        self.password = secrets.token_urlsafe(24)
        self.started = False

    def start(self):
        command([
            "docker", "run", "-d", "--name", self.name,
            "--network", "none",
            "--tmpfs", "/var/lib/mysql:rw,noexec,nosuid,size=1g",
            "-e", "MYSQL_ROOT_PASSWORD=" + self.password,
            "-e", "MYSQL_DATABASE=java_ai",
            self.image,
        ])
        self.started = True
        deadline = time.time() + 180
        while time.time() < deadline:
            probe = command([
                "docker", "exec", "-e", "MYSQL_PWD=" + self.password,
                self.name, "mysql", "-uroot", "--batch", "--skip-column-names",
                "-e", "SELECT 1;"
            ], check=False)
            if probe.returncode == 0 and probe.stdout.strip() == b"1":
                return
            time.sleep(2)
        raise RuntimeError("isolated MySQL did not become ready")

    def remove(self):
        if self.started:
            command(["docker", "rm", "-f", self.name], check=False)
            self.started = False

    def mysql(self, sql=None, *, input_file=None):
        args = [
            "docker", "exec", "-i", "-e", "MYSQL_PWD=" + self.password,
            self.name, "mysql", "-uroot", "--default-character-set=utf8mb4",
            "--batch", "--skip-column-names", "java_ai",
        ]
        if sql is not None:
            args.extend(["-e", sql])
        result = command(args, input_file=input_file)
        return result.stdout.decode("utf-8", errors="strict").strip()

    def apply(self, path, replacements=None):
        data = path.read_bytes()
        if replacements:
            text = data.decode("utf-8")
            for old, new in replacements.items():
                text = text.replace(old, new)
            data = text.encode("utf-8")
        args = [
            "docker", "exec", "-i", "-e", "MYSQL_PWD=" + self.password,
            self.name, "mysql", "-uroot", "--default-character-set=utf8mb4",
            "java_ai",
        ]
        command(args, data=data)

    def table_count(self):
        return int(self.mysql(
            "SELECT COUNT(*) FROM information_schema.tables "
            "WHERE table_schema=DATABASE();"
        ))

    def rows(self, tables):
        return {
            table: int(self.mysql(f"SELECT COUNT(*) FROM `{table}`;"))
            for table in tables
        }

    def schema_fingerprint(self):
        tables = self.mysql(
            "SELECT table_name FROM information_schema.tables "
            "WHERE table_schema=DATABASE() AND table_name REGEXP '^ai_' "
            "ORDER BY table_name;"
        ).splitlines()
        definitions = []
        for table in tables:
            definitions.append(self.mysql(f"SHOW CREATE TABLE `{table}`;"))
        return hashlib.sha256("\n".join(definitions).encode("utf-8")).hexdigest()

    def insert_compatibility_rows(self):
        self.mysql(
            "INSERT INTO ai_asset(asset_id,owner_id,file_name,media_type,storage_key,"
            "size_bytes,sha256,created_at,expires_at) VALUES"
            "('validation-asset','isolated-validation-owner','input.png','image/png',"
            "'validation-storage',1,REPEAT('0',64),1,2);"
            "INSERT INTO ai_job(request_id,owner_id,idempotency_key,request_digest,"
            "request_json,state,version,result_json,created_at,updated_at) VALUES"
            "('validation-job','isolated-validation-owner','validation-key',REPEAT('1',64),"
            "'{\"mediaKind\":\"IMAGE\"}','SUCCEEDED',1,'{}',1,1);"
            "INSERT INTO ai_job_event(request_id,version,state,occurred_at) VALUES"
            "('validation-job',1,'SUCCEEDED',1);"
            "INSERT INTO ai_stream_source(stream_source_id,owner_id,display_name,enabled,"
            "created_at,updated_at) VALUES"
            "('validation-source','isolated-validation-owner','Validation source',0,1,1);"
            "INSERT INTO ai_stream_session(session_id,owner_id,idempotency_key,request_digest,"
            "request_json,stream_source_id,state,version,created_at,updated_at) VALUES"
            "('validation-session','isolated-validation-owner','validation-stream-key',"
            "REPEAT('2',64),'{}','validation-source','STOPPED',1,1,1);"
            "INSERT INTO ai_stream_event(session_id,provider_event_id,event_id,offset_millis,"
            "occurred_at,event_type,snapshot_asset_id) VALUES"
            "('validation-session','provider-event-1','validation-event',1,1,'DETECTION',"
            "'validation-asset');"
        )

    def stub_seed_state(self):
        return {
            "capabilityBindings": int(self.mysql(
                "SELECT COUNT(*) FROM ai_capability_binding WHERE capability_code IN "
                "('image-detection.v1','video-file-analysis.v1','video-stream-analysis.v1');"
            )),
            "syntheticSources": int(self.mysql(
                "SELECT COUNT(*) FROM ai_stream_source "
                "WHERE stream_source_id='stub-source-01';"
            )),
        }

    def sanitized_state(self):
        empty_tables = self.rows([
            "sys_log", "sys_data_log", "tab_ai_history", "tab_ai_model_bund"
        ])
        sensitive = {
            "tab_ai_model": int(self.mysql(
                "SELECT COUNT(*) FROM tab_ai_model WHERE ai_weights IS NOT NULL "
                "OR ai_config IS NOT NULL OR ai_name_name IS NOT NULL;"
            )),
            "tab_ai_subscription": int(self.mysql(
                "SELECT COUNT(*) FROM tab_ai_subscription WHERE event_url IS NOT NULL "
                "OR remake IS NOT NULL OR push_static<>0;"
            )),
            "tab_maxkb_model": int(self.mysql(
                "SELECT COUNT(*) FROM tab_maxkb_model WHERE status IS NOT NULL "
                "OR api_key IS NOT NULL OR api_url IS NOT NULL OR api_js IS NOT NULL "
                "OR start_url IS NOT NULL;"
            )),
            "jimu_report_data_source": int(self.mysql(
                "SELECT COUNT(*) FROM jimu_report_data_source WHERE db_url IS NOT NULL "
                "OR db_username IS NOT NULL OR db_password IS NOT NULL OR connect_times<>0;"
            )),
            "jimu_report_db": int(self.mysql(
                "SELECT COUNT(*) FROM jimu_report_db WHERE api_url IS NOT NULL AND api_url<>'';"
            )),
        }
        return {"emptyTables": empty_tables, "sensitiveValueCounts": sensitive}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True)
    parser.add_argument("--baseline", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--image", default="mysql:8.0.36")
    args = parser.parse_args()

    if command(["git", "rev-parse", "--show-toplevel"]).stdout.decode().strip() != str(ROOT):
        raise RuntimeError("run from this repository worktree")
    baseline = args.baseline.expanduser().resolve()
    if not baseline.is_file() or baseline.is_relative_to(ROOT):
        raise RuntimeError("--baseline must be an existing file outside the repository")

    manifest, checksums = load_and_check_manifest(args.base)
    changed = check_scope(args.base)
    codegen_count = check_private_and_codegen()
    mysql = IsolatedMysql(args.image)
    removed = False
    result = {}
    try:
        mysql.start()
        with baseline.open("rb") as stream:
            mysql.mysql(input_file=stream)
        baseline_tables = mysql.table_count()

        bootstrap = ROOT / manifest["execution"][0]["target"]
        mysql.apply(bootstrap)
        sanitized_first = mysql.sanitized_state()
        mysql.apply(bootstrap)
        sanitized_second = mysql.sanitized_state()
        if sanitized_first != sanitized_second:
            raise RuntimeError("bootstrap sanitizer is not stable on repeat")
        if any(sanitized_first["emptyTables"].values()) or any(
            sanitized_first["sensitiveValueCounts"].values()
        ):
            raise RuntimeError("bootstrap sanitizer left prohibited values")

        v001 = ROOT / manifest["execution"][1]["target"]
        v002 = ROOT / manifest["execution"][2]["target"]
        mysql.apply(v001)
        mysql.apply(v002)
        migration_tables = [
            "ai_asset", "ai_job", "ai_capability_binding", "ai_job_event",
            "ai_job_capacity", "ai_stream_source", "ai_stream_session", "ai_stream_event",
        ]
        mysql.insert_compatibility_rows()
        rows_first = mysql.rows(migration_tables)
        schema_first = mysql.schema_fingerprint()
        mysql.apply(v001)
        mysql.apply(v002)
        rows_second = mysql.rows(migration_tables)
        schema_second = mysql.schema_fingerprint()
        if rows_first != rows_second or schema_first != schema_second:
            raise RuntimeError("V001/V002 changed schema or rows on repeat")

        seed = ROOT / manifest["execution"][3]["target"]
        replacements = {"__OWNER_ID__": "isolated-validation-owner"}
        mysql.apply(seed, replacements)
        seed_first = mysql.stub_seed_state()
        mysql.apply(seed, replacements)
        seed_second = mysql.stub_seed_state()
        if seed_first != seed_second or seed_second != {
            "capabilityBindings": 3, "syntheticSources": 1
        }:
            raise RuntimeError("stub seed repeat behavior changed")

        result = {
            "schemaVersion": 1,
            "result": "PASS",
            "verifiedAt": datetime.now(timezone.utc).isoformat(),
            "baseCommit": args.base,
            "engine": mysql.mysql("SELECT VERSION();"),
            "image": args.image,
            "isolation": {
                "network": "none",
                "storage": "tmpfs",
                "persistentVolumeAttached": False,
                "baseline": "authorized-external-private-snapshot",
            },
            "static": {
                "changedPaths": changed,
                "manifestChecksums": checksums,
                "codeGeneratorSqlFilesRemainingInApps": codegen_count,
            },
            "bootstrap": {
                "baselineTableCount": baseline_tables,
                "firstRun": sanitized_first,
                "secondRun": sanitized_second,
                "repeatStable": True,
            },
            "migrations": {
                "order": ["V001", "V002"],
                "firstRunRows": rows_first,
                "secondRunRows": rows_second,
                "schemaFingerprint": schema_first,
                "repeatStable": True,
            },
            "stubSeed": {
                "firstRunRows": seed_first,
                "secondRunRows": seed_second,
                "repeatStable": True,
                "simulated": True,
            },
        }
    finally:
        mysql.remove()
        removed = True

    result["isolation"]["containerRemoved"] = removed
    report = args.report if args.report.is_absolute() else ROOT / args.report
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({
        "result": result["result"],
        "baseCommit": result["baseCommit"],
        "baselineTableCount": result["bootstrap"]["baselineTableCount"],
        "migrationOrder": result["migrations"]["order"],
        "migrationRepeatStable": result["migrations"]["repeatStable"],
        "seedRepeatStable": result["stubSeed"]["repeatStable"],
        "containerRemoved": removed,
    }, ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"verification failed: {error}", file=sys.stderr)
        sys.exit(1)
