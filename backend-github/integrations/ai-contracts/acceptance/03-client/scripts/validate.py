#!/usr/bin/env python3
"""Check this package's ownership, Java AST boundaries and the unchanged frozen contracts."""
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import javalang

ROOT = Path(__file__).resolve().parents[6]
OUT = Path(__file__).resolve().parents[1]
BASE = 'ab9809d23919ea5d61dfc7d8b34d7f30bb9d607c'
AI = 'backend-github/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/ai/'
LEGACY = 'backend-github/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/'
OWNED_FILES = [
    'backend-github/jeecg-boot-base-core/src/main/java/org/jeecg/config/shiro/ShiroConfig.java',
    'backend-github/jeecg-boot-base-core/src/main/java/org/jeecg/config/shiro/AiFilterChains.java',
    'backend-github/jeecg-module-system/jeecg-system-start/src/main/java/org/jeecg/JeecgSystemApplication.java',
    AI + 'api/controller/CapabilityController.java',
] + [LEGACY + name for name in [
    'demo/tab/controller/TabAiHistoryController.java', 'demo/tab/controller/TabAiSubscriptionController.java',
    'demo/tab/service/impl/TabAiHistoryServiceImpl.java', 'demo/video/controller/TabVideoUtilController.java',
    'demo/video/service/impl/TabVideoUtilServiceImpl.java', 'tab/controller/AITestController.java']]
OWNED_PREFIXES = [AI + part for part in ['client/', 'config/provider/', 'application/capabilities/', 'api/mapper/capabilities/', 'legacy/']]
OWNED_PREFIXES += ['backend-github/jeecg-module-system/jeecg-system-biz/src/test/java/org/jeecg/modules/ai/',
                   'backend-github/deploy/remote-ai/', 'backend-github/integrations/ai-contracts/acceptance/03-client/']


def git(*args):
    return subprocess.check_output(['git', '-C', str(ROOT), *args], text=True).strip()


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    return module


def main():
    assert git('rev-parse', '--show-toplevel') == str(ROOT)
    assert git('branch', '--show-current') == 'work/remote-inference/03-client'
    changed = sorted(set(git('diff', '--name-only', BASE).splitlines() + git('ls-files', '--others', '--exclude-standard').splitlines()))
    assert all(f in OWNED_FILES or any(f.startswith(p) for p in OWNED_PREFIXES) for f in changed), changed
    assert not any('/migrations/' in f for f in changed)
    frozen = [AI + p for p in ['domain', 'port', 'api/dto']] + [
        'backend-github/integrations/ai-contracts/v1', 'backend-github/integrations/ai-contracts/provider-draft',
        'backend-github/integrations/ai-contracts/examples']
    assert not git('diff', '5a55ca5cc6ea8fde09898f44519d62c715af12db', '--', *frozen)
    types = load('frozen_type_tools', ROOT / 'backend-github/integrations/ai-contracts/acceptance/02-contract/scripts/check_types.py')
    rows = []
    for file in changed:
        if not file.endswith('.java'): continue
        source = (ROOT / file).read_text(); tree = javalang.parse.parse(source)
        assert len(tree.types) == 1 and tree.types[0].name == Path(file).stem
        baseline = subprocess.run(['git', '-C', str(ROOT), 'show', BASE + ':' + file], capture_output=True, text=True)
        tokens = list(javalang.tokenizer.tokenize(source))
        methods = [{'name': n.name, 'line': n.position.line, 'lines': types.physical_span(n, tokens)}
                   for _, n in tree if isinstance(n, (javalang.tree.MethodDeclaration, javalang.tree.ConstructorDeclaration))]
        if baseline.returncode:
            assert len(source.splitlines()) <= 250, file
            assert all(m['lines'] <= 50 for m in methods), (file, methods)
        imports = [i.path + (".*" if i.wildcard else "") for i in tree.imports]
        if file.startswith(AI + 'client/'):
            assert all(not i.startswith('org.jeecg.modules.ai.') or i.startswith((
                'org.jeecg.modules.ai.client.', 'org.jeecg.modules.ai.port.', 'org.jeecg.modules.ai.domain.')) for i in imports), file
        if file.startswith(AI + 'application/'):
            assert all(i.startswith(('java.', 'org.jeecg.modules.ai.domain.', 'org.jeecg.modules.ai.port.')) for i in imports), file
        if file.startswith(AI + 'api/'):
            assert all(not i.startswith(('org.jeecg.modules.ai.client.', 'org.jeecg.modules.ai.persistence.',
                                        'org.jeecg.modules.ai.storage.')) for i in imports), file
        rows.append({'path': file, 'lines': len(source.splitlines()), 'new': bool(baseline.returncode),
                     'baseline_lines': len(baseline.stdout.splitlines()) if not baseline.returncode else None,
                     'imports': imports, 'methods': methods, 'sha256': hashlib.sha256(source.encode()).hexdigest()})
    (OUT / 'scope-and-architecture.json').write_text(json.dumps({'status': 'PASS', 'base': BASE,
        'changed_paths': changed, 'java': rows, 'max_new_java_lines': max(r['lines'] for r in rows if r['new']),
        'max_new_method_lines': max(m['lines'] for r in rows if r['new'] for m in r['methods']),
        'limitations': 'AST imports and spans plus targeted review; not whole-program semantic proof. Existing oversized legacy files receive thin guards only.'}, indent=2) + '\n')
    contracts = load('frozen_contract_tools', ROOT / 'backend-github/integrations/ai-contracts/acceptance/02-contract/scripts/validate_contracts.py')
    contracts.EVIDENCE = OUT; contracts.main()
    actual = ROOT.parent / 'drafts/validation/capabilities.actual.json'
    if actual.exists():
        document = json.loads((contracts.CONTRACTS / 'v1/business.openapi.json').read_text())
        schema = contracts.resolve(document['components']['schemas']['CapabilityListResponse'], document)
        contracts.Draft4Validator(schema).validate(json.loads(actual.read_text()))
    print('PASS: package ownership, Java AST boundaries/spans, frozen contracts and actual capabilities response')


if __name__ == '__main__': main()
