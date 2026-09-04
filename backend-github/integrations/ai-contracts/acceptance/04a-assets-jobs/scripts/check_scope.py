#!/usr/bin/env python3
"""Record owned paths and immutable shared-contract ancestry before handoff."""
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[6]
BASE = '0bafd30726e82de74cfeb58ebad12393b36841c7'
CONTRACT = '1177de8be45123d043d7cb26b845ee9d94c26784'
AI = 'backend-github/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/ai/'
EVIDENCE = Path(__file__).resolve().parents[1]


def git(*args, cwd=ROOT):
    return subprocess.check_output(['git', *args], cwd=cwd, text=True).strip()


def main():
    assert git('rev-parse', '--show-toplevel') == str(ROOT)
    assert git('branch', '--show-current') == 'work/remote-inference/04a-assets-jobs'
    git('merge-base', '--is-ancestor', CONTRACT, 'HEAD')
    changed = set(git('diff', '--name-only', BASE).splitlines())
    changed.update(git('ls-files', '--others', '--exclude-standard').splitlines())
    allowed = [AI + p for p in ('application/jobs/', 'application/assets/', 'storage/', 'persistence/', 'config/jobs/',
                               'application/streams/', 'api/controller/', 'api/mapper/jobs/', 'api/mapper/assets/',
                               'api/mapper/streams/')]
    allowed += ['backend-github/deploy/remote-ai/migrations/',
                'backend-github/integrations/ai-contracts/acceptance/04a-assets-jobs/',
                'backend-github/jeecg-module-system/jeecg-system-biz/src/test/java/org/jeecg/modules/ai/assetsjobs/']
    assert all(path.startswith(tuple(allowed)) for path in changed), sorted(changed)
    assert not any(path.endswith('CapabilityController.java') for path in changed)
    frozen = [AI + 'domain', AI + 'port', AI + 'api/dto', 'backend-github/integrations/ai-contracts/v1',
              'backend-github/integrations/ai-contracts/v1.1',
              'backend-github/integrations/ai-contracts/provider-draft']
    assert not git('diff', BASE, '--', *frozen)
    consumers = []
    for package in ('00-integration', '03-client', '04a-assets-jobs'):
        cwd = ROOT.parent.parent / package / 'code'
        git('merge-base', '--is-ancestor', CONTRACT, 'HEAD', cwd=cwd)
        for path in frozen:
            assert git('rev-parse', 'HEAD:' + path, cwd=cwd) == git('rev-parse', BASE + ':' + path)
        consumers.append({'package': package, 'head_at_check': git('rev-parse', 'HEAD', cwd=cwd), 'frozen_content_matches': True})
    (EVIDENCE / 'scope-check.json').write_text(json.dumps({'status': 'PASS', 'base': BASE, 'contract': CONTRACT,
        'changed_paths': sorted(changed), 'consumers': consumers, 'frontend_and_shared_files_unchanged': True}, indent=2) + '\n')
    print('Scope PASS:', len(changed), 'owned paths; common frozen contract verified')


if __name__ == '__main__':
    main()
