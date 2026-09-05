"""Hash the actual runnable images and ensure test-only faults are not packaged."""
import hashlib
import io
import json
from pathlib import Path
import subprocess
import zipfile
import tempfile

ROOT = Path(__file__).resolve().parents[7]
OUT = Path(__file__).resolve().parents[1]
WORK = ROOT.parent / 'drafts/round3'
sha = lambda data: hashlib.sha256(data).hexdigest()
images = {}
for service in ['backend', 'frontend']:
    image = 'wgai-integration-' + service + ':round3'
    info = json.loads(subprocess.check_output(['docker', 'image', 'inspect', image]))[0]
    images[service] = {'tag': image, 'id': info['Id'], 'size': info['Size']}
    container = subprocess.check_output(['docker', 'create', image], text=True).strip()
    try:
        dest = WORK / 'app.jar' if service == 'backend' else Path(tempfile.mkdtemp(prefix='frontend-extract-', dir=WORK)) / 'html'
        subprocess.run(['docker', 'cp', container + (':/app/app.jar' if service == 'backend' else ':/usr/share/nginx/html'), str(dest)], check=True)
    finally:
        subprocess.run(['docker', 'rm', container], check=True, stdout=subprocess.DEVNULL)
    if service == 'backend':
        images[service]['jarSha256'] = sha(dest.read_bytes())
        images[service]['jarBytes'] = dest.stat().st_size
        with zipfile.ZipFile(dest) as boot:
            library = next(n for n in boot.namelist() if n.startswith('BOOT-INF/lib/jeecg-system-biz-'))
            with zipfile.ZipFile(io.BytesIO(boot.read(library))) as biz:
                names = biz.namelist()
                assert not any('FaultInjection' in n or 'DownloadFaultFilter' in n or '/acceptance/' in n for n in names)
                ai = [n for n in names if n.startswith('org/jeecg/modules/ai/') and n.endswith('.class')]
                assert ai
                assert all(int.from_bytes(biz.read(n)[6:8], 'big') <= 52 for n in ai)
                images[service]['java8AiClassCount'] = len(ai)
                images[service]['testOverlayAbsent'] = True
    else:
        images[service]['files'] = {str(p.relative_to(dest)): sha(p.read_bytes()) for p in sorted(dest.rglob('*')) if p.is_file()}
        assert 'index.html' in images[service]['files']
logs = {}
for name in ['backend-build', 'frontend-build', 'frontend-tests', 'frontend-lint', 'graphify-update']:
    file = WORK / (name + '.log')
    logs[name] = {'sha256': sha(file.read_bytes()), 'privatePath': str(file)}
assert 'BUILD SUCCESS' in (WORK / 'backend-build.log').read_text()
(OUT / 'artifacts.json').write_text(json.dumps({'status': 'PASS', 'images': images, 'logs': logs,
    'note': 'Packaging uses the existing Dockerfiles; separate 67 Java and 16 frontend tests and live full-application acceptance are mandatory evidence.'}, indent=2) + '\n')
print('PASS: actual application artifacts hashed; fault overlay absent from production JAR')
