#!/usr/bin/env python3
"""Independently verify the 04a delivery boundary and fail-closed production posture."""
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[7]
OUT = Path(__file__).resolve().parents[1] / 'review.json'
BASE = '0bafd30726e82de74cfeb58ebad12393b36841c7'
DELIVERY = '45ba76ce3d55629041092ecb1230bdc1afb8b230'
AI = 'backend-github/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/ai/'


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()


def main():
    assert git('rev-parse', '--show-toplevel') == str(ROOT)
    assert git('rev-parse', 'HEAD') == DELIVERY
    subprocess.check_call(['git', 'merge-base', '--is-ancestor', BASE, DELIVERY], cwd=ROOT)
    changed = sorted(git('diff', '--name-only', BASE, DELIVERY).splitlines())
    allowed = [AI + suffix for suffix in (
        'application/jobs/', 'application/assets/', 'application/streams/', 'storage/',
        'persistence/', 'config/jobs/', 'api/controller/', 'api/mapper/jobs/',
        'api/mapper/assets/', 'api/mapper/streams/')]
    allowed += [
        'backend-github/deploy/remote-ai/migrations/',
        'backend-github/integrations/ai-contracts/acceptance/04a-assets-jobs/',
        'backend-github/jeecg-module-system/jeecg-system-biz/src/test/java/org/jeecg/modules/ai/assetsjobs/'
    ]
    unexpected = [name for name in changed if not name.startswith(tuple(allowed))]
    assert not unexpected, unexpected

    frozen = [AI + 'domain', AI + 'port', AI + 'api/dto',
              'backend-github/integrations/ai-contracts/v1',
              'backend-github/integrations/ai-contracts/v1.1',
              'backend-github/integrations/ai-contracts/provider-draft']
    assert not git('diff', BASE, DELIVERY, '--', *frozen)
    assert not any(name.startswith(('backend-master/', 'frontend-vue/')) for name in changed)
    assert not any(Path(name).name in ('pom.xml', 'package.json', 'package-lock.json', 'yarn.lock') for name in changed)

    jobs = (ROOT / (AI + 'config/jobs/JobsConfiguration.java')).read_text()
    assert 'new org.jeecg.modules.ai.domain.StreamProviderFeatures(false,false,false,false)' in jobs
    request = (ROOT / (AI + 'api/dto/StreamSessionRequestDto.java')).read_text().lower()
    response = (ROOT / (AI + 'api/dto/StreamSessionDto.java')).read_text().lower()
    assert 'streamsourceid' in request
    for forbidden in ('rtsp', 'providerurl', 'gpuurl', 'credential', 'password', 'token'):
        assert forbidden not in request
    for forbidden in ('providersessionid', 'providersourceref', 'rtsp', 'credential'):
        assert forbidden not in response.replace('providersessionid is deliberately absent', '')

    provider = json.loads((ROOT / 'backend-github/integrations/ai-contracts/provider-draft/v0.2.openapi.json').read_text())
    assert provider['x-confirmation-status'] == 'UNCONFIRMED'
    assert all(value is False for value in provider['x-features'].values())
    v002 = (ROOT / 'backend-github/deploy/remote-ai/migrations/V002__04a_video_stream.sql').read_text().upper()
    assert not any(word in v002 for word in ('ALTER TABLE', 'DROP TABLE', 'DELETE FROM', 'TRUNCATE TABLE'))

    result = {
        'status': 'PASS_WITH_EXTERNAL_BLOCKER',
        'base': BASE,
        'delivery': DELIVERY,
        'changedPathCount': len(changed),
        'changedPathsOwned': True,
        'frozenContractsUnchanged': True,
        'frontendDependenciesAndBackendMasterUnchanged': True,
        'productionStreamFeaturesHardDisabled': True,
        'browserOpaqueSourceBoundary': True,
        'v002AdditiveOnly': True,
        'externalBlocker': 'Provider draft v0.2 remains UNCONFIRMED; video and stream real capability must remain unavailable until 05 supplies evidence.',
        'releaseDecision': '04a may be accepted as a fail-closed implementation foundation; this is not 5.1 or an RC.'
    }
    OUT.write_text(json.dumps(result, indent=2) + '\n')
    print('PASS_WITH_EXTERNAL_BLOCKER: 04a scope and fail-closed boundaries verified')


if __name__ == '__main__':
    main()
