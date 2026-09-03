"""Validate captured real Java responses, not just frozen synthetic examples."""
import importlib.util
import json
from pathlib import Path
from jsonschema import Draft4Validator, FormatChecker

ROOT = Path(__file__).resolve().parents[7]
OUT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('contracts', ROOT / 'backend-github/integrations/ai-contracts/acceptance/02-contract/scripts/validate_contracts.py')
contracts = importlib.util.module_from_spec(spec)
spec.loader.exec_module(contracts)
document = json.loads((contracts.CONTRACTS / 'v1/business.openapi.json').read_text())
checks = []
failures = []
for name in ['spring-boot-smoke', 'api-e2e', 'runtime-modes']:
    receipt = json.loads((OUT / (name + '.json')).read_text())
    for row in receipt['requests']:
        url = row['url'].split('?')[0]
        if not url.startswith('/ai/v1/') or (url.endswith('/content') and row['status'] == 200):
            continue
        if 'body' not in row:
            failures.append({'evidence': name, 'url': url, 'issue': 'Expected AI JSON'})
            continue
        if row['status'] >= 400:
            schema = 'ErrorResponse'
        elif url.endswith('/capabilities'):
            schema = 'CapabilityListResponse'
        elif url.endswith('/assets'):
            schema = 'AssetResponse'
        elif url.endswith('/jobs') and row['method'] == 'GET':
            schema = 'JobPageResponse'
        else:
            schema = 'JobResponse'
        for error in Draft4Validator(contracts.resolve(document['components']['schemas'][schema], document), format_checker=FormatChecker()).iter_errors(row['body']):
            failures.append({'evidence': name, 'url': url, 'issue': error.message, 'path': list(error.path)})
        assert row['body']['code'] == row['status']
        checks.append({'evidence': name, 'url': url, 'status': row['status'], 'schema': schema})
(OUT / 'live-contracts.json').write_text(json.dumps({'status': 'FAIL' if failures else 'PASS', 'responses': len(checks), 'checks': checks, 'failures': failures}, indent=2) + '\n')
if failures:
    print(json.dumps(failures, indent=2))
    raise SystemExit(1)
print('PASS:', len(checks), 'actual Java JSON responses match frozen business v1')
