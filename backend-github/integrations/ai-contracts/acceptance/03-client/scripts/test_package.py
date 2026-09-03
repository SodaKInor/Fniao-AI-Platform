#!/usr/bin/env python3
"""Run real Java 8 JUnit tests using existing, pinned build dependencies; no POM changes."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[6]
OUT = Path(__file__).resolve().parents[1]
WORK = ROOT.parent / 'drafts/validation'
AI = ROOT / 'backend-github/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--dependency-image', default='wgai-integration-backend:round1-5a55ca5')
    args = parser.parse_args()
    assert subprocess.check_output(['git', '-C', str(ROOT), 'rev-parse', '--show-toplevel'], text=True).strip() == str(ROOT)
    WORK.mkdir(parents=True, exist_ok=True); libs = WORK / 'libs'; libs.mkdir(exist_ok=True)
    image = subprocess.check_output(['docker', 'image', 'inspect', '--format', '{{.Id}}', args.dependency_image], text=True).strip()
    archive = WORK / 'baseline-app.jar'
    cache = WORK / 'dependency-source.json'
    cached = json.loads(cache.read_text()) if cache.exists() else {}
    if not archive.exists() or cached.get('image') != image or cached.get('sha256') != hashlib.sha256(archive.read_bytes()).hexdigest():
        name = 'wgai-03-client-libs-' + uuid.uuid4().hex[:10]
        subprocess.run(['docker', 'create', '--name', name, args.dependency_image], check=True, stdout=subprocess.DEVNULL)
        try: subprocess.run(['docker', 'cp', name + ':/app/app.jar', str(archive)], check=True)
        finally: subprocess.run(['docker', 'rm', name], check=True, stdout=subprocess.DEVNULL)
    cache.write_text(json.dumps({'image': image, 'sha256': hashlib.sha256(archive.read_bytes()).hexdigest()}) + '\n')
    shutil.rmtree(libs); libs.mkdir()
    with zipfile.ZipFile(archive) as source:
        for name in source.namelist():
            if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
                path = libs / Path(name).name
                if not path.exists(): path.write_bytes(source.read(name))
    repository = Path.home() / '.m2/repository'
    for artifact in ['junit/junit/4.13.2/junit-4.13.2.jar', 'org/hamcrest/hamcrest/2.2/hamcrest-2.2.jar',
                     'org/springframework/spring-test/5.3.18/spring-test-5.3.18.jar']:
        source = repository / artifact
        assert source.is_file(), 'Missing existing test dependency: ' + artifact
        shutil.copy2(source, libs / source.name)
    sources = list((AI / 'ai').rglob('*.java'))
    sources += [AI / p for p in ['demo/tab/controller/TabAiHistoryController.java', 'demo/tab/controller/TabAiSubscriptionController.java',
        'demo/tab/service/impl/TabAiHistoryServiceImpl.java', 'demo/video/controller/TabVideoUtilController.java',
        'demo/video/service/impl/TabVideoUtilServiceImpl.java', 'tab/controller/AITestController.java']]
    sources += [ROOT / ('backend-github/jeecg-boot-base-core/src/main/java/org/jeecg/config/shiro/' + p)
                for p in ['ShiroConfig.java', 'AiFilterChains.java']]
    sources += [ROOT / 'backend-github/jeecg-module-system/jeecg-system-start/src/main/java/org/jeecg/JeecgSystemApplication.java']
    tests = list((ROOT / 'backend-github/jeecg-module-system/jeecg-system-biz/src/test/java/org/jeecg/modules/ai').rglob('*.java'))
    for name, files in [('main', sources), ('test', tests)]:
        (WORK / (name + '-sources.txt')).write_text('\n'.join('/workspace/' + str(p.relative_to(ROOT)) for p in files) + '\n')
    for name in ['server.p12', 'server.crt']:
        path = WORK / name
        if path.exists(): path.unlink()
    for name in ['classes', 'test-classes']:
        path = WORK / name
        if path.exists(): shutil.rmtree(path)
    (WORK / 'logback-test.xml').write_text('<configuration><root level="ERROR"/></configuration>\n')
    command = ['docker', 'run', '--rm', '--network', 'none', '-v', str(ROOT) + ':/workspace:ro',
               '-v', str(WORK) + ':/validation', 'maven:3.8.8-eclipse-temurin-8', 'sh',
               '/workspace/backend-github/integrations/ai-contracts/acceptance/03-client/scripts/run_tests.sh']
    log = WORK / 'test-run.log'
    with log.open('w') as output: result = subprocess.run(command, stdout=output, stderr=subprocess.STDOUT)
    text = log.read_text(); match = re.search(r'OK \((\d+) tests\)', text)
    passed = result.returncode == 0 and match is not None
    classes = list((WORK / 'classes').rglob('*.class'))
    assert classes and all(int.from_bytes(p.read_bytes()[6:8], 'big') == 52 for p in classes)
    receipt = {'status': 'PASS' if passed else 'FAIL', 'tests': int(match.group(1)) if match else 0,
        'exit_code': result.returncode, 'dependency_image': image, 'dependency_archive_sha256': hashlib.sha256(archive.read_bytes()).hexdigest(),
        'source_read_only': True, 'container_network': 'none (loopback fixtures only)', 'java_class_major': 52,
        'log': str(log), 'log_sha256': hashlib.sha256(log.read_bytes()).hexdigest(),
        'source_sha256': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources + tests},
        'limitations': 'Shiro/JwtFilter are real; fixture realm replaces account authentication. Repository substitutes are test-only. No GPU or end-to-end asset/job persistence.'}
    (OUT / 'java8-tests.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(receipt['status'], receipt['tests'], 'JUnit tests, Java class major 52; log:', log)
    if not passed: print(text[-4000:]); raise SystemExit(1)


if __name__ == '__main__': main()
