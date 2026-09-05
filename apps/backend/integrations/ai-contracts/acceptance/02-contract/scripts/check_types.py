#!/usr/bin/env python3
"""AST inventory: declarations, physical spans, imports and DTO-to-schema field/type mapping."""
import json
from pathlib import Path
import re

import javalang

ROOT = Path(__file__).resolve().parents[6]
CONTRACTS = ROOT / 'backend-github/integrations/ai-contracts'
EVIDENCE = Path(__file__).resolve().parents[1]
AI = ROOT / 'backend-github/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/ai'


def physical_span(node, tokens):
    start = node.position.line
    selected = [t for t in tokens if t.position.line >= start]
    depth, opened = 0, False
    for token in selected:
        if token.value == ';' and not opened:
            return token.position.line - start + 1
        if token.value == '{':
            depth += 1
            opened = True
        elif token.value == '}':
            depth -= 1
            if opened and depth == 0:
                return token.position.line - start + 1
    raise AssertionError('No declaration end')


def type_name(t):
    name = t.name
    if t.arguments:
        name += '<' + ','.join(type_name(a.type) for a in t.arguments) + '>'
    return name


def schema_type(schema):
    if '$ref' in schema:
        return schema['$ref'].split('/')[-1]
    if schema.get('type') == 'array':
        return 'List<' + schema_type(schema['items']) + '>'
    if schema.get('format') == 'date-time':
        return 'Instant'
    if schema.get('type') == 'integer':
        return 'Long' if schema.get('format') == 'int64' else 'Integer'
    return {'string': 'String', 'boolean': 'Boolean', 'number': 'Number'}[schema['type']]


def main():
    rows = []
    schemas = json.loads((CONTRACTS / 'v1.1/business.openapi.json').read_text())['components']['schemas']
    owned_paths = []
    for path in AI.rglob('*.java'):
        parts = path.relative_to(AI).parts
        if len(parts) >= 3 and parts[1] in ('domain', 'port'):
            owned_paths.append(path)
        elif len(parts) >= 4 and parts[1:3] == ('api', 'dto'):
            owned_paths.append(path)
    for path in sorted(owned_paths):
        source = path.read_text()
        tree = javalang.parse.parse(source)
        assert len(tree.types) == 1 and tree.types[0].name == path.stem
        assert 'public' in tree.types[0].modifiers
        relative_parts = path.relative_to(AI).parts
        layer = 'api' if relative_parts[1:3] == ('api', 'dto') else relative_parts[1]
        imports = [i.path for i in tree.imports]
        allowed = ['java.']
        if layer == 'domain':
            allowed += ['org.jeecg.modules.ai.']
        if layer == 'port':
            allowed += ['org.jeecg.modules.ai.']
        if layer == 'api':
            allowed += ['com.fasterxml.jackson.annotation.',
                        'org.jeecg.modules.ai.asset.api.dto.',
                        'org.jeecg.modules.ai.image.api.dto.',
                        'org.jeecg.modules.ai.job.api.dto.',
                        'org.jeecg.modules.ai.video.api.dto.',
                        'org.jeecg.modules.ai.job.domain.JobState',
                        'org.jeecg.modules.ai.job.domain.ErrorCode',
                        'org.jeecg.modules.ai.job.domain.JobType',
                        'org.jeecg.modules.ai.result.domain.ResultType',
                        'org.jeecg.modules.ai.stream.domain.StreamSessionState',
                        'org.jeecg.modules.ai.job.domain.UnknownOperationReason']
        assert all(any(i.startswith(prefix) for prefix in allowed) for i in imports), path
        tokens = list(javalang.tokenizer.tokenize(source))
        methods = [{'name': n.name, 'line': n.position.line, 'physical_lines': physical_span(n, tokens)}
                   for _, n in tree if isinstance(n, (javalang.tree.MethodDeclaration,
                                                      javalang.tree.ConstructorDeclaration))]
        lines = len(source.splitlines())
        assert lines < 400 and all(m['physical_lines'] < 80 for m in methods), path
        if layer == 'api':
            actual = {d.name: type_name(f.type) for f in tree.types[0].fields for d in f.declarators}
            expected = {n: schema_type(s) for n, s in schemas[path.stem]['properties'].items()}
            for field in actual:
                if expected.get(field) == 'Number':
                    assert actual[field] in ['Double', 'BigDecimal']
                    expected[field] = actual[field]
                if path.stem == 'ErrorDto' and field == 'errorCode':
                    expected[field] = 'ErrorCode'
            assert actual == expected, (path.name, actual, expected)
            assert '@JsonInclude(JsonInclude.Include.NON_NULL)' in source
        rows.append({'path': str(path.relative_to(ROOT)), 'physical_lines': lines,
                     'imports': imports, 'methods': methods})
        assert 'Map<' not in source, path
    for name, values in [
            ('JobState', schemas['JobState']['enum']),
            ('ErrorCode', schemas['ErrorDto']['properties']['errorCode']['enum']),
            ('JobType', schemas['JobType']['enum']),
            ('ResultType', schemas['ResultType']['enum']),
            ('StreamSessionState', schemas['StreamSessionState']['enum']),
            ('UnknownOperationReason', schemas['UnknownOperationReason']['enum'])]:
        enum_paths = {
            'JobState': AI / 'job/domain/JobState.java',
            'ErrorCode': AI / 'job/domain/ErrorCode.java',
            'JobType': AI / 'job/domain/JobType.java',
            'ResultType': AI / 'result/domain/ResultType.java',
            'StreamSessionState': AI / 'stream/domain/StreamSessionState.java',
            'UnknownOperationReason': AI / 'job/domain/UnknownOperationReason.java',
        }
        tree = javalang.parse.parse(enum_paths[name].read_text())
        assert [c.name for c in tree.types[0].body.constants] == values
    assert len(rows) >= 43
    (EVIDENCE / 'type-checks.json').write_text(json.dumps({
        'status': 'PASS', 'public_types': len(rows),
        'max_java_physical_lines': max(r['physical_lines'] for r in rows),
        'max_method_physical_lines': max(m['physical_lines'] for r in rows for m in r['methods']),
        'files_requiring_400_line_review': [], 'methods_requiring_80_line_review': [],
        'coverage': 'Java AST declarations/imports/physical method spans, no unbounded Map, and DTO/schema mappings. '
                    'Combined with Java 8 isolated compile, jdeps and documented responsibility review; '
                    'not a general proof of runtime architecture.', 'files': rows}, indent=2) + '\n')
    print('PASS:', len(rows), 'public types; DTO field/type and enum agreement; AST layer/size inventory')


if __name__ == '__main__':
    main()
