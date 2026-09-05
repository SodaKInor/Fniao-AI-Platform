#!/usr/bin/env python3
"""Exercise the ten allocated baseline environments, preserving original WGAI resources."""
import datetime as dt
import gzip
import json
from pathlib import Path
import socket
import subprocess
import urllib.request

from runtime_profiles import EVIDENCE, ROOT, prepare


def compose(allocation, args, **kwargs):
    directory = Path(allocation['runtime_profile']).parent
    command = ['docker', 'compose', '--project-directory', str(directory), '-f',
               allocation['runtime_profile'], '--env-file', str(directory / '.env'), *args]
    result = subprocess.run(command, capture_output=True, **kwargs)
    with (directory / 'commands.private.log').open('ab') as out:
        out.write(result.stdout + result.stderr)
    if result.returncode:
        raise RuntimeError(f"{allocation['id']}: compose command failed; see private log")
    return result.stdout


def mysql(allocation, sql):
    return compose(allocation, ['exec', '-T', 'mysql', 'sh', '-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 '
        '--batch --skip-column-names "$MYSQL_DATABASE"'], input=sql.encode()).decode().strip()


def import_backup(allocation, backup):
    count = int(mysql(allocation, 'SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE();'))
    if count:
        assert count in (122, 123), 'Unexpected nonempty test database; refusing import'
        return
    directory = Path(allocation['runtime_profile']).parent
    command = ['docker', 'compose', '--project-directory', str(directory), '-f',
        allocation['runtime_profile'], '--env-file', str(directory / '.env'), 'exec', '-T', 'mysql',
        'sh', '-c', 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot '
        '--default-character-set=utf8mb4 "$MYSQL_DATABASE"']
    with (directory / 'import.private.log').open('wb') as log:
        with subprocess.Popen(command, stdin=subprocess.PIPE, stdout=log, stderr=log) as process:
            with gzip.open(backup, 'rb') as source:
                for line in source:
                    # Fresh per-package DB only. Never alter the backup or replay the old sanitize SQL.
                    if line.startswith((b'CREATE DATABASE ', b'USE `java_ai`')):
                        continue
                    process.stdin.write(line)
            process.stdin.close()
            assert process.wait() == 0, 'Test database import failed'
    assert int(mysql(allocation, 'SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE();')) == 122


def verify_containers(allocation):
    ids = compose(allocation, ['ps', '-q']).decode().split()
    actual = json.loads(subprocess.check_output(['docker', 'inspect', *ids]))
    assert len(actual) == 4
    rows = []
    for container in actual:
        labels = container['Config']['Labels']
        assert labels['com.docker.compose.project'] == allocation['project']
        assert labels['wgai.foundation.package'] == allocation['id']
        assert set(container['NetworkSettings']['Networks']) == {allocation['network']}
        for mount in container['Mounts']:
            assert mount['Type'] == 'volume' and mount['Name'] in allocation['volumes'], mount
        ports = container['HostConfig'].get('PortBindings') or {}
        for bindings in ports.values():
            for binding in bindings or []:
                assert binding['HostIp'] == '127.0.0.1'
                assert int(binding['HostPort']) in [allocation['frontend_port'], allocation['backend_port']]
        assert container['State'].get('Health', {}).get('Status') == 'healthy'
        rows.append({'service': labels['com.docker.compose.service'], 'id': container['Id'],
                     'image': container['Image'], 'health': 'healthy',
                     'volumes': [m['Name'] for m in container['Mounts']], 'ports': ports})
    return rows


def probe(allocation, backup):
    for port in [allocation['frontend_port'], allocation['backend_port']]:
        with socket.socket() as connection:
            connection.settimeout(0.2)
            assert connection.connect_ex(('127.0.0.1', port)) != 0, f'Port {port} already occupied'
    evidence = {'id': allocation['id'], 'started_at': dt.datetime.now(dt.timezone.utc).isoformat()}
    try:
        compose(allocation, ['up', '-d', '--wait', '--wait-timeout', '240', 'mysql', 'redis'])
        import_backup(allocation, backup)
        owner = allocation['id']
        assert all(c.isalnum() or c == '-' for c in owner)
        mysql(allocation, 'CREATE TABLE IF NOT EXISTS foundation_isolation_probe '
                          '(id INT PRIMARY KEY, owner VARCHAR(80) NOT NULL); '
                          f"INSERT IGNORE INTO foundation_isolation_probe VALUES (1,'{owner}');")
        assert mysql(allocation, 'SELECT owner FROM foundation_isolation_probe WHERE id=1;') == owner
        compose(allocation, ['exec', '-T', 'redis', 'sh', '-c',
            'REDISCLI_AUTH="$REDIS_PASSWORD" exec redis-cli SET foundation:owner "$1"', 'sh', owner])
        actual_owner = compose(allocation, ['exec', '-T', 'redis', 'sh', '-c',
            'REDISCLI_AUTH="$REDIS_PASSWORD" exec redis-cli GET foundation:owner']).decode().strip()
        assert actual_owner == owner
        compose(allocation, ['up', '-d', '--wait', '--wait-timeout', '240', 'backend'])
        compose(allocation, ['exec', '-T', 'backend', 'sh', '-c',
            'printf %s "$1" > /data/uploads/.foundation-isolation-owner', 'sh', owner])
        assert compose(allocation, ['exec', '-T', 'backend', 'cat',
            '/data/uploads/.foundation-isolation-owner']).decode() == owner
        compose(allocation, ['up', '-d', '--wait', '--wait-timeout', '120', 'frontend'])
        evidence['containers'] = verify_containers(allocation)
        for port, path, label in [(allocation['frontend_port'], '/', 'frontend'),
                                  (allocation['backend_port'], '/jeecg-boot/v2/api-docs', 'backend')]:
            with urllib.request.urlopen(f'http://127.0.0.1:{port}{path}', timeout=10) as response:
                assert response.status == 200
                evidence[label + '_http_status'] = response.status
        evidence.update(database_owner=owner, redis_owner=actual_owner, upload_owner=owner,
                        status='PASS', login_tested=False, inference_tested=False)
    finally:
        compose(allocation, ['stop', '--timeout', '30'])
        evidence['stopped_at'] = dt.datetime.now(dt.timezone.utc).isoformat()
        evidence['volumes_preserved'] = True
        (EVIDENCE / ('isolation-' + allocation['id'] + '.json')).write_text(json.dumps(evidence, indent=2) + '\n')
    return evidence


def main():
    receipt = json.loads((EVIDENCE / 'backup-receipt.json').read_text())
    assert receipt['status'] == 'PASS'
    backup = Path(receipt['backup_directory']) / 'database.sql.gz'
    allocations = prepare()
    results = []
    for allocation in allocations:
        record = EVIDENCE / ('isolation-' + allocation['id'] + '.json')
        if record.exists() and json.loads(record.read_text()).get('status') == 'PASS':
            results.append(json.loads(record.read_text()))
            continue
        print('Checking isolated baseline:', allocation['id'], flush=True)
        result = probe(allocation, backup)
        results.append(result)
        print('PASS; test services stopped, data volumes preserved:', allocation['id'], flush=True)
    (EVIDENCE / 'isolation-summary.json').write_text(json.dumps({'status': 'PASS', 'packages': results,
        'coverage': 'Rendered and actual container network, port, mount, database, Redis and upload isolation; management home/docs only',
        'baseline_source_commit': receipt['foundation_start_commit']}, indent=2) + '\n')


if __name__ == '__main__':
    main()
