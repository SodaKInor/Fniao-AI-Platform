#!/usr/bin/env python3
"""Independently run the integrated Java 8 suite against a disposable 00 schema."""

import hashlib
import json
from pathlib import Path
import re
import subprocess
import uuid


ROOT = Path(__file__).resolve().parents[7]
OUT = Path(__file__).resolve().parents[1]
WORK = ROOT.parent / "drafts/round7-cleanup/java"
LIBS = ROOT.parent / "drafts/round3/java/libs"
ENV = ROOT.parent / "drafts/round3/tests.env"
MYSQL = "wgai-ri-00-integration-mysql-1"
NETWORK = "wgai-ri-00-integration_network"


def sql(statement, database=""):
    assert not database or re.fullmatch(r"ai_00_verify_[a-f0-9]+", database)
    command = [
        "docker", "exec", "-i", MYSQL, "sh", "-c",
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot '
        "--default-character-set=utf8mb4 --batch --skip-column-names " + database,
    ]
    subprocess.run(command, input=statement, text=True, check=True, stdout=subprocess.DEVNULL)


def main():
    assert ROOT.name == "code" and ROOT.parent.name == "00-integration"
    assert LIBS.is_dir() and ENV.is_file()
    mounts = json.loads(subprocess.check_output(["docker", "inspect", MYSQL], text=True))[0]["Mounts"]
    assert any(item.get("Name") == "wgai-ri-00-integration_mysql_data" for item in mounts)
    WORK.mkdir(parents=True, exist_ok=True)
    (WORK / "logback-test.xml").write_text('<configuration><root level="ERROR"/></configuration>\n')
    database = "ai_00_verify_" + uuid.uuid4().hex[:12]
    sql(
        "CREATE DATABASE " + database + " CHARACTER SET utf8mb4 COLLATE utf8mb4_bin; "
        "GRANT ALL ON " + database + ".* TO 'foundation'@'%';"
    )
    command = [
        "docker", "run", "--rm", "--network", NETWORK,
        "-e", "AI_TEST_JDBC=jdbc:mysql://mysql:3306/" + database
        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
        "--env-file", str(ENV),
        "-v", str(ROOT) + ":/workspace:ro",
        "-v", str(WORK) + ":/validation",
        "-v", str(LIBS) + ":/validation/lib:ro",
        "maven:3.8.8-eclipse-temurin-8", "sh",
        "/workspace/backend-github/integrations/ai-contracts/acceptance/00-integration/round7-cleanup/scripts/run-java8.sh",
    ]
    result = None
    migrations = [
        ROOT / "backend-github/deploy/remote-ai/migrations/V001__04a_assets_jobs.sql",
        ROOT / "backend-github/deploy/remote-ai/migrations/V002__04a_video_stream.sql",
    ]
    try:
        for migration in migrations:
            sql(migration.read_text(), database)
        with (WORK / "tests.log").open("w") as log:
            result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT)
    finally:
        sql("REVOKE ALL ON " + database + ".* FROM 'foundation'@'%'; DROP DATABASE " + database + ";")
    output = (WORK / "tests.log").read_text()
    match = re.search(r"OK \((\d+) tests\)", output)
    receipt = {
        "status": "PASS" if result and result.returncode == 0 and match else "FAIL",
        "tests": int(match.group(1)) if match else 0,
        "exitCode": result.returncode if result else None,
        "javaClassMajor": 52,
        "database": "00-owned disposable schema; dropped after test",
        "sourceReadOnly": True,
        "log": str(WORK / "tests.log"),
        "logSha256": hashlib.sha256((WORK / "tests.log").read_bytes()).hexdigest(),
    }
    (OUT / "java8-tests.json").write_text(json.dumps(receipt, indent=2) + "\n")
    print(receipt["status"], receipt["tests"], "tests; private log:", receipt["log"])
    if receipt["status"] != "PASS":
        print(output[-8000:])
        raise SystemExit(1)


if __name__ == "__main__":
    main()
