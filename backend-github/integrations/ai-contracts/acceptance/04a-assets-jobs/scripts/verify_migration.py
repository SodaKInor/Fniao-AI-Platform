#!/usr/bin/env python3
"""Validate only this package's MySQL copy; never emit old business rows or credentials."""
import hashlib
import json
from pathlib import Path
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[6]
CONTAINER = 'wgai-ri-04a-assets-jobs-mysql-1'
DATABASE = 'wgai_ri_04a_assets_jobs'
TABLES = {'ai_asset', 'ai_job', 'ai_capability_binding', 'ai_job_event', 'ai_job_capacity'}
MIGRATION = ROOT / 'backend-github/deploy/remote-ai/migrations/V001__04a_assets_jobs.sql'


def sql(statement, database=DATABASE):
    assert database == DATABASE or database.startswith('ai_04a_verify_')
    command = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --skip-column-names ' + database
    result = subprocess.run(['docker', 'exec', '-i', CONTAINER, 'sh', '-c', command],
                            input=statement.encode(), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        raise RuntimeError('Isolated MySQL operation failed; inspect local database availability')
    return result.stdout


def inventory():
    tables = sql('SHOW TABLES').decode().splitlines()
    result = {}
    for table in tables:
        if table in TABLES:
            continue
        assert table.replace('_', '').isalnum()
        schema = sql('SHOW CREATE TABLE `' + table + '`')
        rows = sorted(sql('SELECT * FROM `' + table + '`').splitlines())
        result[table] = {'schema_sha256': hashlib.sha256(schema).hexdigest(),
                         'rows_sha256': hashlib.sha256(b'\n'.join(rows)).hexdigest(), 'row_count': len(rows)}
    return result


def main():
    assert ROOT.parent.name == '04a-assets-jobs' and ROOT.name == 'code'
    mounts = json.loads(subprocess.check_output(['docker', 'inspect', CONTAINER]))[0]['Mounts']
    assert any(m.get('Name') == 'wgai-ri-04a-assets-jobs_mysql_data' for m in mounts)
    assert all(not m.get('Name') or m['Name'].startswith('wgai-ri-04a-assets-jobs_') for m in mounts)
    before = inventory()
    expected = 'ai_04a_verify_' + uuid.uuid4().hex
    sql('CREATE DATABASE `' + expected + '` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin')
    try:
        ddl = MIGRATION.read_text()
        sql(ddl, expected)
        expected_schema = {t: sql('SHOW CREATE TABLE ' + t, expected) for t in TABLES}
        existing = set(sql('SHOW TABLES').decode().splitlines())
        for table in TABLES & existing:
            assert sql('SHOW CREATE TABLE ' + table) == expected_schema[table], 'Existing AI schema differs: ' + table
        sql(ddl)
        first = {t: sql('SHOW CREATE TABLE ' + t) for t in TABLES}
        sql(ddl)
        assert first == {t: sql('SHOW CREATE TABLE ' + t) for t in TABLES} == expected_schema
        assert inventory() == before, 'Historical schema/data changed'
        assert sql('SELECT COUNT(*) FROM ai_job_capacity') == b'1\n'
        evidence = {'status': 'PASS', 'database': '04a isolated existing copy', 'historical_tables': before,
                    'migration_sha256': hashlib.sha256(ddl.encode()).hexdigest(), 'executions': 2,
                    'new_tables': sorted(TABLES), 'old_data_unchanged': True, 'schema_preflight': True}
        output = Path(__file__).resolve().parents[1] / 'migration-checks.json'
        output.write_text(json.dumps(evidence, indent=2) + '\n')
        print('Migration PASS:', len(before), 'historical tables unchanged; repeat application and schema checks passed')
    finally:
        sql('DROP DATABASE `' + expected + '`')


if __name__ == '__main__':
    main()
