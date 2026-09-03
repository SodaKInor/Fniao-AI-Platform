#!/usr/bin/env python3
"""Validate versioned documents, references, fixtures and business cross-field obligations."""
import copy
import hashlib
import json
from pathlib import Path
import struct
import zlib

from jsonschema import Draft4Validator, FormatChecker
from openapi_spec_validator import validate_spec

CONTRACTS = Path(__file__).resolve().parents[3]
EVIDENCE = Path(__file__).resolve().parents[1]


def resolve(schema, document):
    schema = copy.deepcopy(schema)
    if '$ref' in schema:
        parts = schema.pop('$ref').split('/')
        assert parts[:3] == ['#', 'components', 'schemas'] and len(parts) == 4
        target = resolve(document['components']['schemas'][parts[3]], document)
        target.update(schema)
        schema = target
    for key, value in list(schema.items()):
        if isinstance(value, dict):
            schema[key] = resolve(value, document)
        elif isinstance(value, list):
            schema[key] = [resolve(x, document) if isinstance(x, dict) else x for x in value]
    if schema.pop('nullable', False):
        schema['type'] = [schema['type'], 'null']
    return schema


def validate_job(job):
    assert job['simulated'] is True
    if job['state'] == 'SUCCEEDED':
        assert 'error' not in job and 'result' in job
        result = job['result']
        assert result['simulated'] is True
        assert len(result['data']['detections']) <= job['parameters']['maxDetections']
        for detection in result['data']['detections']:
            box = detection['box']
            assert box['x'] + box['width'] <= 1 and box['y'] + box['height'] <= 1
        if not job['parameters']['annotate']:
            assert not result['artifacts']
    else:
        assert 'result' not in job
    if job['state'] in ['FAILED', 'UNKNOWN']:
        assert 'error' in job


def verify_png(path):
    data = path.read_bytes()
    assert data[:8] == b'\x89PNG\r\n\x1a\n'
    position, compressed, dimensions = 8, b'', None
    while position < len(data):
        length = struct.unpack('!I', data[position:position + 4])[0]
        kind = data[position + 4:position + 8]
        payload = data[position + 8:position + 8 + length]
        checksum = struct.unpack('!I', data[position + 8 + length:position + 12 + length])[0]
        assert zlib.crc32(kind + payload) & 0xffffffff == checksum
        if kind == b'IHDR':
            dimensions = struct.unpack('!2I', payload[:8])
        if kind == b'IDAT':
            compressed += payload
        position += 12 + length
    assert dimensions == (16, 16) and len(zlib.decompress(compressed)) == 16 * (1 + 16 * 3)


def main():
    docs = {name: json.loads((CONTRACTS / path).read_text()) for name, path in {
        'business': 'v1/business.openapi.json', 'provider': 'provider-draft/v0.1.openapi.json'}.items()}
    for document in docs.values():
        validate_spec(document)
        for schema in document['components']['schemas'].values():
            Draft4Validator.check_schema(resolve(schema, document))
    manifest = json.loads((CONTRACTS / 'examples/manifest.json').read_text())
    assert manifest['simulated'] is True
    outcomes = []
    for name, case in manifest['cases'].items():
        value = json.loads((CONTRACTS / 'examples' / name).read_text())
        schema = resolve(docs[case['contract']]['components']['schemas'][case['schema']], docs[case['contract']])
        errors = list(Draft4Validator(schema, format_checker=FormatChecker()).iter_errors(value))
        assert (not errors) == case['valid'], name
        if case['valid'] and case['schema'] == 'JobResponse':
            validate_job(value['result'])
        if case['contract'] == 'provider' and case['schema'] in ['Success', 'Error']:
            assert value['simulated'] is True
        outcomes.append({'file': name, 'expected_valid': case['valid'], 'passed': True})
    for file in manifest['files']:
        path = CONTRACTS / 'examples' / file['path']
        assert path.stat().st_size == file['sizeBytes']
        assert hashlib.sha256(path.read_bytes()).hexdigest() == file['sha256']
        verify_png(path)
    read = lambda name: json.loads((CONTRACTS / 'examples' / name).read_text())
    accepted, success = read('accepted.json'), read('success.json')
    assert accepted['code'] == 202 and success['code'] == 200
    assert accepted['result']['requestId'] == success['result']['requestId']
    metadata = read('provider-metadata.json')
    request = read('submit.json')
    assert metadata['request_id'] == success['result']['requestId']
    assert metadata['parameters']['threshold'] == request['parameters']['threshold']
    assert metadata['parameters']['max_detections'] == request['parameters']['maxDetections']
    artifact = success['result']['result']['artifacts'][0]
    wire_artifact = read('provider-success.json')['artifacts'][0]
    expected = manifest['files'][1]
    assert artifact['sha256'] == wire_artifact['sha256'] == expected['sha256']
    assert artifact['sizeBytes'] == wire_artifact['size_bytes'] == expected['sizeBytes']
    assert docs['provider']['x-features'] == {'query': False, 'cancel': False, 'deduplication': False}
    assert list(docs['provider']['paths']) == ['/infer']
    (EVIDENCE / 'contract-checks.json').write_text(json.dumps({
        'status': 'PASS', 'openapi_documents': 2, 'fixtures': outcomes,
        'binary_fixtures': '2 PNG files: signature, chunk CRC, decoded dimensions, size and SHA-256',
        'semantic_checks': ['same local identity for 200/202', 'terminal/result/error shape',
                            'normalized boxes/count', 'wire/business parameter correspondence',
                            'local/provider/file artifact hashes', 'unconfirmed optional features disabled'],
        'scope': 'Declarations and synthetic fixtures only; no live API, authentication or GPU execution'}, indent=2) + '\n')
    print('PASS: 2 OpenAPI documents, 15 positive/negative JSON cases, 2 PNG fixtures, cross-field checks')


if __name__ == '__main__':
    main()
