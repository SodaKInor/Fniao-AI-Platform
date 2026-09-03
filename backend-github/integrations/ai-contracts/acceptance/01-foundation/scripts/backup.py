#!/usr/bin/env python3
"""Back up the existing, explicitly authorized local deployment; never restore it."""
import datetime as dt
import gzip
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[6]
ORIGINAL = Path('/Users/twowt88/Documents/ChatGPT/WGAI')
SERVICES = ['wgai-frontend-1', 'wgai-backend-1', 'wgai-mysql-1', 'wgai-redis-1']
EVIDENCE = Path(__file__).resolve().parents[1]


def run(args, **kwargs):
    return subprocess.run(args, check=True, capture_output=True, **kwargs).stdout


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def mysql(sql):
    return run(['docker', 'exec', '-i', SERVICES[2], 'sh', '-c',
                'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot '
                '--batch --skip-column-names "$MYSQL_DATABASE"'],
               input=sql.encode()).decode().strip()


def healthy(name, timeout=240):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        state = json.loads(run(['docker', 'inspect', name]))[0]['State']
        if state.get('Health', {}).get('Status') == 'healthy':
            return
        time.sleep(3)
    raise RuntimeError(f'{name} did not recover health within {timeout}s')


def main():
    os.umask(0o077)
    actual_root = Path(run(['git', '-C', str(ROOT), 'rev-parse', '--show-toplevel']).decode().strip())
    assert actual_root == ROOT and ROOT.name == 'code', actual_root
    branch = run(['git', '-C', str(ROOT), 'branch', '--show-current']).decode().strip()
    assert branch == 'work/remote-inference/01-foundation', branch
    containers = json.loads(run(['docker', 'inspect', *SERVICES]))
    assert all(c['State']['Running'] for c in containers), 'Expected all four original services running'
    assert all(c['Config']['Labels'].get('com.docker.compose.project') == 'wgai' for c in containers)
    assert shutil.disk_usage(ORIGINAL.parent).free > 12 * 1024**3, 'Need at least 12 GiB free'
    stamp = dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    target = ORIGINAL.parent / 'WGAI-backups' / 'round1' / stamp
    target.mkdir(parents=True, exist_ok=False, mode=0o700)
    (target / 'private').mkdir(mode=0o700)
    # Actual environment is retained privately because image tags and env files may have drifted.
    (target / 'private' / 'container-config.json').write_text(json.dumps(containers, indent=2))
    shutil.copy2(ORIGINAL / 'deploy/.env', target / 'private/deploy.env')
    os.chmod(target / 'private/deploy.env', 0o600)
    image_mapping = []
    for container in containers:
        original_image = container['Image']
        probe = subprocess.run(['docker', 'image', 'inspect', original_image], capture_output=True)
        saved_image = original_image
        method = 'original_image'
        if probe.returncode:
            service = container['Name'].strip('/').removeprefix('wgai-').removesuffix('-1')
            tag = f'wgai-round1-recovery-{service}:{stamp.lower()}'
            # Data volumes are excluded. No existing tag or running container is changed.
            run(['docker', 'commit', '--no-pause', container['Id'], tag])
            saved_image = json.loads(run(['docker', 'image', 'inspect', tag]))[0]['Id']
            method = 'container_root_filesystem_snapshot_original_image_unavailable'
        image_mapping.append({'container': container['Name'], 'original_image': original_image,
                              'saved_image': saved_image, 'method': method})
    images = sorted({item['saved_image'] for item in image_mapping})
    with (target / 'images.tar').open('wb') as out:
        subprocess.run(['docker', 'image', 'save', *images], stdout=out, check=True)
    receipt = {
        'backup_directory': str(target), 'started_at': stamp,
        'source_commit': run(['git', '-C', str(ORIGINAL), 'rev-parse', 'HEAD']).decode().strip(),
        'foundation_start_commit': run(['git', '-C', str(ROOT), 'rev-parse', 'HEAD']).decode().strip(),
        'running_image_ids': sorted({c['Image'] for c in containers}),
        'saved_image_mapping': image_mapping,
        'table_engines_before': mysql('SELECT ENGINE,COUNT(*) FROM information_schema.TABLES '
                                     'WHERE TABLE_SCHEMA=DATABASE() GROUP BY ENGINE;'),
        'method': 'Application writers stopped; mysqldump --lock-all-tables; stopped-container upload archive',
        'restore_executed': False,
    }
    print(f'Prepared private backup at {target}; beginning authorized downtime', flush=True)
    quiesce_attempted = False
    redis_stop_attempted = False
    try:
        quiesce_attempted = True
        run(['docker', 'stop', '--time', '45', SERVICES[0], SERVICES[1]])
        assert mysql('SELECT COUNT(*) FROM information_schema.PROCESSLIST '
                     'WHERE DB=DATABASE() AND ID<>CONNECTION_ID();') == '0', 'Other DB clients are connected'
        assert mysql('SELECT COUNT(*) FROM information_schema.EVENTS '
                     'WHERE EVENT_SCHEMA=DATABASE() AND STATUS="ENABLED";') == '0', 'Enabled database events need review'
        cmd = ['docker', 'exec', SERVICES[2], 'sh', '-c',
               'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot '
               '--lock-all-tables --routines --events --triggers --hex-blob '
               '--set-gtid-purged=OFF --no-tablespaces --databases "$MYSQL_DATABASE"']
        with gzip.open(target / 'database.sql.gz', 'wb') as out:
            with subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE) as proc:
                shutil.copyfileobj(proc.stdout, out)
                error = proc.stderr.read()
                if proc.wait():
                    raise RuntimeError('Database export failed; diagnostic retained privately')
                (target / 'private/dump-stderr.txt').write_bytes(error)
        with (target / 'uploads.tar').open('wb') as out:
            subprocess.run(['docker', 'cp', SERVICES[1] + ':/data/uploads/.', '-'], stdout=out, check=True)
        redis_stop_attempted = True
        run(['docker', 'stop', '--time', '30', SERVICES[3]])
        with (target / 'redis.tar').open('wb') as out:
            subprocess.run(['docker', 'cp', SERVICES[3] + ':/data/.', '-'], stdout=out, check=True)
        receipt['snapshot_complete_at'] = dt.datetime.now(dt.timezone.utc).isoformat()
    finally:
        # Restore original container identities/images without compose up, rebuild or volume initialization.
        if redis_stop_attempted:
            run(['docker', 'start', SERVICES[3]])
            healthy(SERVICES[3])
        if quiesce_attempted:
            run(['docker', 'start', SERVICES[1]])
            healthy(SERVICES[1])
            run(['docker', 'start', SERVICES[0]])
            healthy(SERVICES[0])
        receipt['services_restored_at'] = dt.datetime.now(dt.timezone.utc).isoformat()
        (target / 'receipt.partial.json').write_text(json.dumps(receipt, indent=2))
        print('Original services restored; checking backup readability', flush=True)
    table_count = 0
    with gzip.open(target / 'database.sql.gz', 'rt', errors='strict') as source:
        for line in source:
            if line.startswith('CREATE TABLE '):
                table_count += 1
    assert table_count == int(mysql('SELECT COUNT(*) FROM information_schema.TABLES '
                                   'WHERE TABLE_SCHEMA=DATABASE();'))
    receipt['sql_create_table_count'] = table_count
    for filename in ['uploads.tar', 'redis.tar', 'images.tar']:
        count = 0
        members = []
        with tarfile.open(target / filename) as archive:
            for member in archive:
                if member.isfile():
                    h = hashlib.sha256()
                    with archive.extractfile(member) as content:
                        for chunk in iter(lambda: content.read(1024 * 1024), b''):
                            h.update(chunk)
                    members.append({'path': member.name, 'bytes': member.size, 'sha256': h.hexdigest()})
                    count += 1
        (target / 'private' / (filename + '.members.json')).write_text(json.dumps(members, indent=2))
        receipt[filename + '_readable_files'] = count
    receipt['files'] = [{'path': p.name, 'bytes': p.stat().st_size, 'sha256': digest(p)}
                        for p in sorted(target.iterdir()) if p.suffix in ['.tar', '.gz']]
    for name in SERVICES:
        healthy(name)
    with urllib.request.urlopen('http://127.0.0.1:8080/', timeout=10) as response:
        receipt['restored_home_http_status'] = response.status
    receipt['status'] = 'PASS'
    receipt['verified_at'] = dt.datetime.now(dt.timezone.utc).isoformat()
    (target / 'manifest.json').write_text(json.dumps(receipt, indent=2))
    (EVIDENCE / 'backup-receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(json.dumps(receipt, ensure_ascii=False), flush=True)


if __name__ == '__main__':
    main()
