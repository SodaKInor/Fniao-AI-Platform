#!/usr/bin/env python3
"""Scoped AST/import and physical-size checks, complemented by runtime tests and review."""
import hashlib
import json
from pathlib import Path
import subprocess
import javalang

ROOT = Path(__file__).resolve().parents[6]
AI = ROOT / 'backend-github/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/ai'
EVIDENCE = Path(__file__).resolve().parents[1]
BASE = '0bafd30726e82de74cfeb58ebad12393b36841c7'
PREFIX = 'org.jeecg.modules.ai.'


def span(node, tokens):
    depth = 0
    opened = False
    for token in tokens:
        if token.position.line < node.position.line:
            continue
        if token.value == ';' and not opened:
            return token.position.line - node.position.line + 1
        if token.value == '{':
            depth += 1
            opened = True
        elif token.value == '}':
            depth -= 1
            if opened and depth == 0:
                return token.position.line - node.position.line + 1
    raise AssertionError('Missing declaration end')


def main():
    changed = set(subprocess.check_output(['git', 'diff', '--name-only', BASE], cwd=ROOT, text=True).splitlines())
    changed.update(subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard'],
                                           cwd=ROOT, text=True).splitlines())
    rows = []
    for path in sorted(AI.rglob('*.java')):
        relative = path.relative_to(AI)
        layer = relative.parts[0]
        if layer in ('domain', 'port', 'client', 'legacy') or relative.parts[:2] == ('api', 'dto'):
            original = subprocess.check_output(['git', 'show', BASE + ':' + str(path.relative_to(ROOT))], cwd=ROOT)
            assert original == path.read_bytes(), 'Frozen public type changed'
            continue
        source = path.read_text()
        tree = javalang.parse.parse(source)
        assert len(tree.types) == 1 and tree.types[0].name == path.stem
        imports = [i.path + ("." if i.wildcard else "") for i in tree.imports]
        allowed = {
            'application': ('application.', 'domain.', 'port.'),
            'storage': ('storage.', 'domain.', 'port.'),
            'persistence': ('persistence.', 'domain.', 'port.'),
            'config': ('config.', 'application.', 'domain.', 'port.', 'storage.', 'persistence.', 'api.', 'client.', 'legacy.'),
            'api': ('api.', 'domain.', 'application.'),
        }[layer]
        if relative.parts[:2] == ('api', 'mapper'):
            allowed = ('api.dto.', 'api.mapper.', 'domain.')
        assert all(not i.startswith(PREFIX) or i[len(PREFIX):].startswith(allowed) for i in imports), relative
        if layer == 'application':
            assert all(i.startswith(('java.', PREFIX)) for i in imports), relative
        if relative.parts[:2] == ('api', 'controller'):
            assert all(not i.startswith(PREFIX + p) for i in imports for p in ('port.', 'persistence.', 'storage.', 'client.'))
        tokens = list(javalang.tokenizer.tokenize(source))
        methods = [{'name': node.name, 'lines': span(node, tokens)} for _, node in tree
                   if isinstance(node, (javalang.tree.MethodDeclaration, javalang.tree.ConstructorDeclaration))]
        lines = len(source.splitlines())
        assert lines <= 250 and all(m['lines'] <= 50 for m in methods), (relative, methods)
        if str(path.relative_to(ROOT)) in changed:
            assert not any(s in source for s in ('HttpURLConnection', 'java.net.http', 'RestTemplate', 'okhttp3'))
        rows.append({'path': str(path.relative_to(ROOT)), 'lines': lines, 'methods': methods,
                     'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
    evidence = {'status': 'PASS', 'implementation_files': len(rows), 'max_file_lines': max(r['lines'] for r in rows),
                'max_method_lines': max(m['lines'] for r in rows for m in r['methods']),
                'frozen_public_types_unchanged': True,
                'coverage': 'AST imports, file/method spans, forbidden direct network API references; not a complete semantic proof',
                'files': rows}
    (EVIDENCE / 'layer-checks.json').write_text(json.dumps(evidence, indent=2) + '\n')
    print('Layer and size PASS:', evidence['implementation_files'], 'files;', evidence['max_file_lines'], 'max lines')


if __name__ == '__main__':
    main()
