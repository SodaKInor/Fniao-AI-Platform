"""Frozen declarations, all integrated Java layer imports and size-review triggers."""
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import javalang

ROOT = Path(__file__).resolve().parents[7]
OUT = Path(__file__).resolve().parents[1]
AI = 'backend-github/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/ai/'
BASE = 'ab9809d23919ea5d61dfc7d8b34d7f30bb9d607c'


def load(name, relative):
    spec = importlib.util.spec_from_file_location(name, ROOT / relative)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


frozen = [AI + p for p in ['domain', 'port', 'api/dto']] + [
    'backend-github/integrations/ai-contracts/' + p for p in ['v1', 'provider-draft', 'examples']]
assert not subprocess.check_output(['git', 'diff', '5a55ca5', '--', *frozen], cwd=ROOT)
contracts = load('contracts', 'backend-github/integrations/ai-contracts/acceptance/02-contract/scripts/validate_contracts.py')
contracts.EVIDENCE = OUT
contracts.main()
types = load('types', 'backend-github/integrations/ai-contracts/acceptance/02-contract/scripts/check_types.py')
rows = []
for file in sorted((ROOT / AI).rglob('*.java')):
    source = file.read_text()
    tree = javalang.parse.parse(source)
    imports = [i.path for i in tree.imports]
    layer = file.relative_to(ROOT / AI).parts[0]
    internal = [i for i in imports if i.startswith('org.jeecg.modules.ai.')]
    allowed = {
        'api': ['api', 'application', 'domain'],
        'application': ['application', 'domain', 'port'],
        'domain': ['domain'], 'port': ['domain', 'port'],
        'client': ['client', 'domain', 'port'],
        'persistence': ['persistence', 'domain', 'port'],
        'storage': ['storage', 'domain', 'port']
    }
    if layer in allowed:
        assert all(i.split('.')[4] in allowed[layer] for i in internal), (file, internal)
    tokens = list(javalang.tokenizer.tokenize(source))
    methods = [{'name': n.name, 'line': n.position.line, 'lines': types.physical_span(n, tokens)}
               for _, n in tree if isinstance(n, (javalang.tree.MethodDeclaration, javalang.tree.ConstructorDeclaration))]
    rows.append({'path': str(file.relative_to(ROOT)), 'lines': len(source.splitlines()),
                 'layer': layer, 'imports': imports, 'methods': methods,
                 'sha256': hashlib.sha256(source.encode()).hexdigest()})
triggers = [r['path'] for r in rows if r['lines'] > 400 or any(m['lines'] > 80 for m in r['methods'])]
assert not triggers, triggers
changed = subprocess.check_output(['git', 'diff', '--name-only', BASE], cwd=ROOT, text=True).splitlines()
legacy = [{'path': p, 'lines': len((ROOT / p).read_text().splitlines())}
          for p in changed if p.endswith('.java') and '/modules/ai/' not in p and '/src/main/' in p]
for row in legacy:
    source = (ROOT / row['path']).read_text()
    tokens = list(javalang.tokenizer.tokenize(source))
    row['reviewMethodsOver80'] = [{'name': n.name, 'line': n.position.line, 'lines': types.physical_span(n, tokens)}
        for _, n in javalang.parse.parse(source)
        if isinstance(n, (javalang.tree.MethodDeclaration, javalang.tree.ConstructorDeclaration)) and types.physical_span(n, tokens) > 80]
(OUT / 'architecture.json').write_text(json.dumps({'status': 'PASS', 'java': rows, 'triggers': triggers,
    'legacy': legacy, 'review': 'Existing oversized legacy files receive disabled-entry thin guards only; no old-system rewrite.',
    'limitations': 'AST import graph and physical spans, not a whole-program semantic proof.'}, indent=2) + '\n')
print('PASS: frozen contracts,', len(rows), 'Java files and layer dependencies')
