"""Compile and execute all delivered Java tests against the isolated 00 database."""
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import shutil
import subprocess
import zipfile
import uuid

ROOT = Path(__file__).resolve().parents[7]
OUT = Path(__file__).resolve().parents[1]
WORK = ROOT.parent / 'drafts/round3/java'
WORK.mkdir(parents=True, exist_ok=True)
assert ROOT.parent.name == '00-integration'
libs = WORK / 'libs'
libs.mkdir(exist_ok=True)
image = 'wgai-integration-backend:round1-5a55ca5'
container = subprocess.check_output(['docker', 'create', image], text=True).strip()
try:
    subprocess.run(['docker', 'cp', container + ':/app/app.jar', str(WORK / 'baseline.jar')], check=True, stdout=subprocess.DEVNULL)
finally:
    subprocess.run(['docker', 'rm', container], check=True, stdout=subprocess.DEVNULL)
with zipfile.ZipFile(WORK / 'baseline.jar') as archive:
    for name in archive.namelist():
        if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
            (libs / Path(name).name).write_bytes(archive.read(name))
dependency_script = ROOT / 'backend-github/integrations/ai-contracts/acceptance/04a-assets-jobs/scripts/prepare_validation.py'
spec = importlib.util.spec_from_file_location('pinned04a', dependency_script)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
for coordinate, expected in module.DEPENDENCIES.items():
    candidates = [Path.home() / '.m2/repository' / coordinate,
                  ROOT.parent.parent / '04a-assets-jobs/code/backend-github/jeecg-module-system/jeecg-system-biz/target/04a-validation/lib' / coordinate.split('/')[-1]]
    source = next(p for p in candidates if p.is_file() and hashlib.sha256(p.read_bytes()).hexdigest() == expected)
    shutil.copy2(source, libs / source.name)
hamcrest = Path.home() / '.m2/repository/org/hamcrest/hamcrest/2.2/hamcrest-2.2.jar'
shutil.copy2(hamcrest, libs / hamcrest.name)
modules = ROOT / 'backend-github/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules'
sources = list((modules / 'ai').rglob('*.java'))
sources += [modules / p for p in ['demo/tab/controller/TabAiHistoryController.java', 'demo/tab/controller/TabAiSubscriptionController.java',
    'demo/tab/service/impl/TabAiHistoryServiceImpl.java', 'demo/video/controller/TabVideoUtilController.java',
    'demo/video/service/impl/TabVideoUtilServiceImpl.java', 'tab/controller/AITestController.java']]
sources += list((ROOT / 'backend-github/jeecg-boot-base-core/src/main/java/org/jeecg/config/shiro').glob('*.java'))
sources += [ROOT / 'backend-github/jeecg-module-system/jeecg-system-start/src/main/java/org/jeecg/JeecgSystemApplication.java']
tests = list((ROOT / 'backend-github/jeecg-module-system/jeecg-system-biz/src/test/java/org/jeecg/modules/ai').rglob('*.java'))
for name, files in [('main', sources), ('test', tests)]:
    (WORK / (name + '-sources.txt')).write_text('\n'.join('/workspace/' + str(p.relative_to(ROOT)) for p in files) + '\n')
(WORK / 'logback-test.xml').write_text('<configuration><root level="ERROR"/></configuration>\n')
script = '/workspace/backend-github/integrations/ai-contracts/acceptance/00-integration/round3/scripts/java-tests.sh'
database = 'ai_00_verify_' + uuid.uuid4().hex[:12]
runtime = str(Path(__file__).with_name('runtime.cjs'))
node_sql = 'const r=require(process.argv[1]);r.sql(process.argv[2],process.argv[3]);'
def sql(statement, db='wgai_ri_00_integration'):
    subprocess.run(['node', '-e', node_sql, runtime, statement, db], check=True, stdout=subprocess.DEVNULL)

sql('CREATE DATABASE ' + database + ' CHARACTER SET utf8mb4; GRANT ALL ON ' + database + ".* TO 'foundation'@'%';")
command = ['docker', 'run', '--rm', '--network', 'wgai-ri-00-integration_network',
    '-e', 'AI_TEST_JDBC=jdbc:mysql://mysql:3306/' + database + '?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC',
    '--env-file', str(WORK.parent / 'tests.env'), '-v', str(ROOT) + ':/workspace:ro',
    '-v', str(WORK) + ':/validation', 'maven:3.8.8-eclipse-temurin-8', 'sh', script]
try:
    sql((ROOT / 'backend-github/deploy/remote-ai/migrations/V001__04a_assets_jobs.sql').read_text(), database)
    with (WORK / 'tests.log').open('w') as log:
        result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT)
finally:
    sql('REVOKE ALL ON ' + database + ".* FROM 'foundation'@'%'; DROP DATABASE " + database + ';')
text = (WORK / 'tests.log').read_text()
match = re.search(r'OK \((\d+) tests\)', text)
receipt = {'status': 'PASS' if result.returncode == 0 and match else 'FAIL',
    'tests': int(match.group(1)) if match else 0, 'exitCode': result.returncode,
    'database': '00 isolated MySQL temporary schema; dropped after tests', 'sourceReadOnly': True,
    'logSha256': hashlib.sha256((WORK / 'tests.log').read_bytes()).hexdigest(),
    'sources': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources + tests}}
(OUT / 'java-tests.json').write_text(json.dumps(receipt, indent=2) + '\n')
print(receipt['status'], receipt['tests'], 'tests; private log:', WORK / 'tests.log')
if receipt['status'] != 'PASS':
    print(text[-4500:])
    raise SystemExit(1)
