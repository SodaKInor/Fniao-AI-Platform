#!/usr/bin/env python3
"""Materialize private, package-specific baseline test profiles, never production config."""
import json
import os
from pathlib import Path
import secrets
import subprocess

EVIDENCE = Path(__file__).resolve().parents[1]
ROOT = Path(__file__).resolve().parents[6]
PACKAGES = ROOT.parent.parent


def inspect_image(name):
    return json.loads(subprocess.check_output(['docker', 'image', 'inspect', name]))[0]['Id']


def profile(package, images, credentials):
    package_id = package['id']
    project = 'wgai-ri-' + package_id
    labels = {'wgai.foundation.package': package_id, 'wgai.foundation.purpose': 'baseline-isolation'}
    health = lambda command: {'test': ['CMD-SHELL', command], 'interval': '3s', 'timeout': '5s',
                              'retries': 60, 'start_period': '20s'}
    database = 'wgai_ri_' + package_id.replace('-', '_')
    common = {'labels': labels, 'restart': 'no', 'pull_policy': 'never'}
    backend_env = {
        'JAVA_TOOL_OPTIONS': '-Xms128m -Xmx768m', 'SPRING_PROFILES_ACTIVE': 'prod,docker',
        'SPRING_AUTOCONFIGURE_EXCLUDE': 'com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceAutoConfigure',
        'MYBATIS_PLUS_MAPPER_LOCATIONS': 'classpath*:org/jeecg/modules/**/xml/*Mapper.xml',
        'JEECG_ELASTICSEARCH_CLUSTER_NODES': '',
        'SPRING_APPLICATION_JSON': json.dumps({'spring': {'mail': {'username': ''}}, 'jeecg': {
            'oss': {'accessKey': '', 'secretKey': '', 'endpoint': '', 'bucketName': ''},
            'minio': {'bucketName': ''}}}),
        'SERVER_PORT': '8080', 'MYSQL_HOST': 'mysql', 'MYSQL_PORT': '3306',
        'MYSQL_DATABASE': database, 'MYSQL_USER': 'foundation', 'MYSQL_PASSWORD': '${MYSQL_PASSWORD}',
        'REDIS_HOST': 'redis', 'REDIS_PORT': '6379', 'REDIS_PASSWORD': '${REDIS_PASSWORD}',
    }
    for key in ['OPENCV', 'ALGORITHM', 'ASR', 'CAMERA', 'GPU', 'PLC']:
        backend_env['WGAI_' + key + '_ENABLED'] = 'false'
    spec = {'name': project, 'services': {
        'mysql': dict(common, image=images['mysql'], environment={
            'MYSQL_ROOT_PASSWORD': '${MYSQL_ROOT_PASSWORD}', 'MYSQL_DATABASE': database,
            'MYSQL_USER': 'foundation', 'MYSQL_PASSWORD': '${MYSQL_PASSWORD}'},
            command=['--lower_case_table_names=1', '--character-set-server=utf8mb4',
                     '--collation-server=utf8mb4_general_ci', '--max_allowed_packet=256M'],
            volumes=['mysql_data:/var/lib/mysql'],
            healthcheck=health('MYSQL_PWD="$$MYSQL_ROOT_PASSWORD" mysql --protocol=TCP -h127.0.0.1 '
                              '-uroot --database="$$MYSQL_DATABASE" --execute="SELECT 1" >/dev/null')),
        'redis': dict(common, image=images['redis'], environment={'REDIS_PASSWORD': '${REDIS_PASSWORD}'},
            command=['sh', '-c', 'exec redis-server --appendonly yes --requirepass "$$REDIS_PASSWORD"'],
            volumes=['redis_data:/data'],
            healthcheck=health('REDISCLI_AUTH="$$REDIS_PASSWORD" redis-cli ping | grep -q PONG')),
        'backend': dict(common, image=images['backend'], environment=backend_env,
            ports=[f"127.0.0.1:{package['suggested_backend_port']}:8080"],
            volumes=['uploads:/data/uploads'],
            healthcheck=health('curl -fsS http://127.0.0.1:8080/jeecg-boot/doc.html >/dev/null')),
        'frontend': dict(common, image=images['frontend'],
            ports=[f"127.0.0.1:{package['suggested_frontend_port']}:80"],
            healthcheck=health('wget -q --spider http://127.0.0.1/')),
    }, 'volumes': {n: {'name': project + '_' + n, 'labels': labels}
                   for n in ['mysql_data', 'redis_data', 'uploads']},
        'networks': {'default': {'name': project + '_network', 'labels': labels}}}
    directory = ROOT.parent / 'drafts/runtimes' / package_id
    directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    envfile = directory / '.env'
    if not envfile.exists():
        envfile.write_text(''.join(f'{key}={value}\n' for key, value in credentials.items()))
    os.chmod(envfile, 0o600)
    (directory / 'compose.json').write_text(json.dumps(spec, indent=2) + '\n')
    return directory, spec


def prepare():
    os.umask(0o077)
    images = {key: inspect_image(name) for key, name in {
        'mysql': 'mysql:8.0.36', 'redis': 'redis:7.2.7-alpine',
        'backend': 'wgai-foundation-backend:e1ccab1',
        'frontend': 'wgai-foundation-frontend:e1ccab1'}.items()}
    packages = json.loads((PACKAGES / 'WORKSPACES.json').read_text())['packages']
    allocations = []
    source_inodes = set()
    for package in packages:
        code = Path(package['code_directory'])
        assert code.resolve() == code and not code.is_symlink()
        actual = subprocess.check_output(['git', '-C', str(code), 'rev-parse', '--show-toplevel']).decode().strip()
        branch = subprocess.check_output(['git', '-C', str(code), 'branch', '--show-current']).decode().strip()
        assert actual == str(code) and branch == package['branch']
        inode = (code / 'AGENTS.md').stat().st_ino
        assert inode not in source_inodes, 'Shared/hardlinked tracked source detected'
        source_inodes.add(inode)
        credentials = {key: secrets.token_hex(24) for key in
                       ['MYSQL_ROOT_PASSWORD', 'MYSQL_PASSWORD', 'REDIS_PASSWORD']}
        directory, spec = profile(package, images, credentials)
        command = ['docker', 'compose', '--project-directory', str(directory), '-f',
                   str(directory / 'compose.json'), '--env-file', str(directory / '.env')]
        rendered = json.loads(subprocess.check_output(command + ['config', '--format', 'json']))
        # Rendered output contains credentials, and stays outside the Git worktree.
        (directory / 'rendered.private.json').write_text(json.dumps(rendered, indent=2))
        for name, service in rendered['services'].items():
            assert 'container_name' not in service
            for mount in service.get('volumes', []):
                assert mount['type'] == 'volume' and mount['source'] in spec['volumes']
            for port in service.get('ports', []):
                assert port['host_ip'] == '127.0.0.1'
        assert all(not v.get('external') for v in rendered['volumes'].values())
        allocations.append({'id': package['id'], 'project': spec['name'], 'code_directory': str(code),
            'branch': branch, 'runtime_profile': str(directory / 'compose.json'),
            'frontend_port': package['suggested_frontend_port'], 'backend_port': package['suggested_backend_port'],
            'database': spec['services']['mysql']['environment']['MYSQL_DATABASE'],
            'volumes': [v['name'] for v in spec['volumes'].values()],
            'network': spec['networks']['default']['name'], 'images': images, 'config_rendered': True})
    for key in ['database', 'project', 'network', 'frontend_port', 'backend_port']:
        assert len({item[key] for item in allocations}) == len(allocations), key
    assert len({v for a in allocations for v in a['volumes']}) == len(allocations) * 3
    (EVIDENCE / 'runtime-allocations.json').write_text(json.dumps(allocations, indent=2) + '\n')
    return allocations


if __name__ == '__main__':
    print(json.dumps(prepare(), indent=2))
