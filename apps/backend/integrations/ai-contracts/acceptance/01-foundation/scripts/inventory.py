#!/usr/bin/env python3
"""Collect navigation/source evidence without login, inference or business mutations."""
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[6]
EVIDENCE = Path(__file__).resolve().parents[1]
ORIGINAL = Path('/Users/twowt88/Documents/ChatGPT/WGAI')
AREAS = ('tab', 'video', 'train', 'face', 'szr', 'audio', 'chat', 'tchat', 'easy', 'teasy', 'maxkb')
ZIP_HASHES = {
    'wgai-github.zip': '350e6da553ded3966313424a9638537976b6a4a2911f5754432b34b814ecef0c',
    'wgai-master.zip': '44723e788f9420d1b816c31972fc3f0a59b4bc6221185a47c5db53d6b0fd3630',
    'wgai-vue.zip': 'dba19ab7383d15f4aa97f2a0195244d73fe6d09278bce2127e7b862991ef3d27',
}


def command(args, **kwargs):
    return subprocess.run(args, check=True, capture_output=True, **kwargs).stdout


def sha(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda: f.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def save(name, data):
    (EVIDENCE / name).write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')


def main():
    captured = dt.datetime.now(dt.timezone.utc).isoformat()
    query = ('SELECT name,url,component,menu_type,hidden,del_flag,status,internal_or_external '
             'FROM sys_permission WHERE del_flag=0 ORDER BY sort_no,id;')
    raw = command(['docker', 'exec', '-i', 'wgai-mysql-1', 'sh', '-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 '
        '--batch --skip-column-names "$MYSQL_DATABASE"'], input=query.encode()).decode()
    menus = []
    for line in raw.splitlines():
        row = dict(zip(['name', 'url', 'component', 'menu_type', 'hidden', 'del_flag', 'status', 'external'],
                       line.split('\t')))
        for key in ['url', 'component']:
            row[key] = row[key].split('?')[0].split('#')[0]
        row['component_exists'] = (ROOT / 'frontend-vue/src/views' / (row['component'] + '.vue')).is_file()
        row['disposition'] = '需求待定' if row['component'].split('/')[0] in ['maxkb', 'easy', 'tchat', 'teasy'] else '保留管理'
        if row['component'] == 'easy':
            row['disposition'] = '停用'
        elif row['component'].startswith('tab/live/'):
            row['disposition'] = '需求待定'
        row['deletion_authorized'] = False
        menus.append(row)
    save('menu-inventory.json', {'captured_at': captured, 'scope': 'sys_permission navigation metadata; not authenticated UI validation', 'rows': menus})
    with urllib.request.urlopen('http://127.0.0.1:8080/jeecg-boot/v2/api-docs', timeout=15) as response:
        spec = json.load(response)
    paths = {p.removeprefix('/jeecg-boot'): sorted(v.keys()) for p, v in spec['paths'].items()}
    save('runtime-api-inventory.json', {'captured_at': captured, 'path_count': len(paths),
        'by_area': {area: sum(p.startswith('/' + area + '/') for p in paths) for area in AREAS},
        'paths': paths, 'execution_endpoints_invoked': False})
    files = command(['rg', '--files', 'frontend-vue/src', 'backend-github/jeecg-module-system',
                     'backend-github/jeecg-boot-base-core'], cwd=ROOT).decode().splitlines()
    frontend, controllers, boundaries = [], [], []
    area_pattern = '(?:' + '|'.join(AREAS) + ')'
    url_pattern = re.compile(r'''["'`](/''' + area_pattern + r'''/[^"'`\s]*)["'`]''')
    for name in files:
        path = ROOT / name
        if name.endswith(('.vue', '.js')):
            if '/src/views/' in name and name.split('/src/views/')[1].split('/')[0] in AREAS:
                frontend.append({'file': name, 'kind': 'view', 'lines': len(path.read_text(errors='replace').splitlines())})
            for number, line in enumerate(path.read_text(errors='replace').splitlines(), 1):
                for match in url_pattern.finditer(line):
                    frontend.append({'file': name, 'line': number, 'kind': 'literal_path',
                                     'path': match.group(1).split('?')[0]})
        elif name.endswith('Controller.java'):
            lines = path.read_text(errors='replace').splitlines()
            selected = any(re.search('/' + area_pattern + '/', line) for line in lines[:90])
            if selected:
                mappings = [{'line': i, 'annotation': line.strip()} for i, line in enumerate(lines, 1)
                            if re.search(r'@(Request|Get|Post|Put|Delete)Mapping', line)]
                controllers.append({'file': name, 'compiled_source_root': '/jeecg-system-biz/src/main/java/' in name,
                                    'mappings': mappings})
        if name.endswith(('ShiroConfig.java', 'TabAiHistoryServiceImpl.java', 'TabVideoUtilServiceImpl.java',
                          'WebSocket.java', 'router/index.js', 'permission.js', 'util.js')):
            for i, line in enumerate(path.read_text(errors='replace').splitlines(), 1):
                if any(key in line for key in ['startAi(', 'AIModelYolo', 'generateIndexRouter',
                                               'addRouters', 'filterChainDefinitionMap.put', '@ServerEndpoint']):
                    boundaries.append({'file': name, 'line': i, 'evidence': line.strip()})
    save('source-entrypoints.json', {'captured_at': captured, 'frontend': frontend,
        'target_backend_controllers': controllers, 'boundaries': boundaries,
        'scope': 'Literal source/mapping inspection and runtime Swagger comparison; dynamic expressions require manual review; no semantic completeness claim'})
    artifacts = json.loads((ROOT / 'backend-github/development/remote-inference/local-artifacts.json').read_text())
    for item in artifacts['artifacts']:
        p = ORIGINAL / item['path']
        item['actual_sha256'] = sha(p)
        item['hash_matches'] = item['actual_sha256'] == item['sha256']
        with zipfile.ZipFile(p) as archive:
            item['embedded_license_paths'] = [n for n in archive.namelist() if 'license' in n.lower()]
        with zipfile.ZipFile(ORIGINAL / 'wgai-github.zip') as upstream:
            member = 'wgai-github/' + item['path'].removeprefix('backend-github/')
            item['source_archive_member'] = member
            item['matches_source_archive'] = hashlib.sha256(upstream.read(member)).hexdigest() == item['actual_sha256']
        item['license_evidence'] = 'upstream-license-references.json; upstream declaration, not a binary signature'
        item['local_build_input_available'] = (ROOT / item['path']).is_file()
    artifacts['licenses'] = [{'path': name, 'sha256': sha(ROOT / name), 'unchanged': sha(ROOT / name) == sha(ORIGINAL / name)}
                             for name in ['backend-github/LICENSE', 'backend-github/wg/LICENSE', 'frontend-vue/LICENSE']]
    artifacts['original_archives'] = []
    for name, expected in ZIP_HASHES.items():
        archive = ORIGINAL / name
        actual = sha(archive)
        assert actual == expected, name
        with zipfile.ZipFile(archive) as source:
            assert source.testzip() is None, name
            members = source.namelist()
        artifacts['original_archives'].append({'path': str(archive), 'bytes': archive.stat().st_size,
            'sha256': actual, 'matches_historical_hash': True, 'zip_integrity': 'PASS',
            'license_paths': [n for n in members if n.split('/')[-1].lower() in ['license', 'notice']]})
    artifacts['original_zip_status'] = 'VERIFIED after user supplied originals in the original workspace'
    artifacts['search_scope'] = ['project, Downloads, Desktop, Documents', 'Spotlight exact filenames',
                                'accessible home directories; macOS-protected locations were not bypassed']
    save('source-inventory.json', artifacts)
    print(f'Collected {len(menus)} menu records, {len(paths)} runtime paths, {len(controllers)} target controllers')


if __name__ == '__main__':
    main()
