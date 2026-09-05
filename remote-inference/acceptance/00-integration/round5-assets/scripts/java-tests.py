#!/usr/bin/env python3
"""Independently compile and run 04a tests on a disposable schema owned by 00."""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[7]
OUT = Path(__file__).resolve().parents[1]
WORK = ROOT.parent / 'drafts/round5-assets/java'
LIBS = ROOT.parent / 'drafts/round3/java/libs'
RUNTIME = ROOT / 'backend-github/integrations/ai-contracts/acceptance/00-integration/round3/scripts/runtime.cjs'


def sql(statement, database='wgai_ri_00_integration'):
    source = 'const r=require(process.argv[1]);r.sql(process.argv[2],process.argv[3]);'
    subprocess.run(['node', '-e', source, str(RUNTIME), statement, database], check=True,
                   stdout=subprocess.DEVNULL)


def main():
    assert ROOT.parent.name == '00-integration'
    assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip() == \
        '45ba76ce3d55629041092ecb1230bdc1afb8b230'
    WORK.mkdir(parents=True, exist_ok=True)
    modules = ROOT / 'backend-github/jeecg-module-system/jeecg-system-biz/src'
    main_sources = sorted((modules / 'main/java/org/jeecg/modules/ai').rglob('*.java'))
    test_sources = sorted((modules / 'test/java/org/jeecg/modules/ai/assetsjobs').rglob('*.java'))
    (WORK / 'main-sources.txt').write_text('\n'.join('/workspace/' + str(p.relative_to(ROOT)) for p in main_sources) + '\n')
    (WORK / 'test-sources.txt').write_text('\n'.join('/workspace/' + str(p.relative_to(ROOT)) for p in test_sources) + '\n')
    (WORK / 'logback-test.xml').write_text('<configuration><root level="ERROR"/></configuration>\n')
    database = 'ai_00_verify_' + uuid.uuid4().hex[:12]
    sql('CREATE DATABASE ' + database + " CHARACTER SET utf8mb4 COLLATE utf8mb4_bin; "
        "GRANT ALL ON " + database + ".* TO 'foundation'@'%';")
    migrations = [ROOT / 'backend-github/deploy/remote-ai/migrations/V001__04a_assets_jobs.sql',
                  ROOT / 'backend-github/deploy/remote-ai/migrations/V002__04a_video_stream.sql']
    command = ['docker', 'run', '--rm', '--network', 'wgai-ri-00-integration_network',
               '-e', 'AI_TEST_JDBC=jdbc:mysql://mysql:3306/' + database
               + '?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC',
               '--env-file', str(ROOT.parent / 'drafts/round3/tests.env'),
               '-v', str(ROOT) + ':/workspace:ro', '-v', str(WORK) + ':/validation',
               '-v', str(LIBS) + ':/validation/lib:ro', 'maven:3.8.8-eclipse-temurin-8',
               'sh', '/workspace/backend-github/integrations/ai-contracts/acceptance/'
               '00-integration/round5-assets/scripts/java-tests.sh']
    try:
        for migration in migrations:
            sql(migration.read_text(), database)
        with (WORK / 'tests.log').open('w') as log:
            result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT)
    finally:
        sql('REVOKE ALL ON ' + database + ".* FROM 'foundation'@'%'; DROP DATABASE " + database + ';')
    text = (WORK / 'tests.log').read_text()
    match = re.search(r'OK \((\d+) tests\)', text)
    receipt = {
        'status': 'PASS' if result.returncode == 0 and match else 'FAIL',
        'tests': int(match.group(1)) if match else 0,
        'exit_code': result.returncode,
        'java_class_major': 52,
        'database': '00-owned disposable MySQL schema; dropped after test',
        'source_read_only': True,
        'log': str(WORK / 'tests.log'),
        'log_sha256': hashlib.sha256((WORK / 'tests.log').read_bytes()).hexdigest(),
        'migrations': {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in migrations},
        'source_sha256': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest()
                          for p in main_sources + test_sources}
    }
    (OUT / 'java8-tests.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(receipt['status'], receipt['tests'], 'tests; private log:', receipt['log'])
    if receipt['status'] != 'PASS':
        print(text[-5000:])
        raise SystemExit(1)


if __name__ == '__main__':
    main()
