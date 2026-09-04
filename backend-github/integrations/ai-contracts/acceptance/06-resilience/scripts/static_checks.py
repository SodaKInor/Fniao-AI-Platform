#!/usr/bin/env python3
"""Verify the 06 local-only ownership, safety, metrics and fail-closed boundaries."""
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[6]
OUT = Path(__file__).resolve().parents[1] / 'static-checks.json'
BASE = 'b23f2fc8c5d1911af61dd0f55ad6a89d73c0d09d'
AI = Path('backend-github/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/ai')


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()


def sha(path):
    return hashlib.sha256((ROOT / path).read_bytes()).hexdigest()


def main():
    assert git('rev-parse','--show-toplevel') == str(ROOT)
    subprocess.run(['git','merge-base','--is-ancestor',BASE,'HEAD'],cwd=ROOT,check=True)
    changed=set(filter(None,git('diff','--name-only',BASE).splitlines()))
    changed.update(filter(None,git('ls-files','--others','--exclude-standard').splitlines()))
    allowed_prefixes=(
        str(AI / 'config/jobs')+'/',
        str(AI / 'persistence/mapper/JobMapper.java'),
        str(AI / 'persistence/repository/MyBatisJobRepository.java'),
        str(AI / 'persistence/repository/MyBatisStreamSessionRepository.java'),
        str(AI / 'persistence/repository/MyBatisStreamEventRepository.java'),
        'backend-github/jeecg-module-system/jeecg-system-biz/src/test/java/org/jeecg/modules/ai/assetsjobs/',
        'backend-github/integrations/ai-contracts/acceptance/06-resilience/',
        'backend-github/jeecg-module-system/jeecg-system-start/src/main/resources/logback-spring.xml')
    unexpected=sorted(path for path in changed if not path.startswith(allowed_prefixes))
    assert not unexpected,unexpected
    assert not any(Path(path).name in ('pom.xml','package.json','package-lock.json','yarn.lock') for path in changed)
    assert not any(path.startswith(('backend-master/','frontend-vue/')) for path in changed)
    migrations={
        'V001__04a_assets_jobs.sql':'0e50ad45101cc92bff877aa63ae60bb42fbf9720f2dc5d93c604a5f682f9c026',
        'V002__04a_video_stream.sql':'40e190fea24cdd476ef7bbd00520fe1b859082a8e8440b7ae4fe3c3845b54a15'}
    for name,digest in migrations.items():
        assert sha('backend-github/deploy/remote-ai/migrations/'+name) == digest
    provider=json.loads((ROOT/'backend-github/integrations/ai-contracts/provider-draft/v0.2.openapi.json').read_text())
    assert provider['x-confirmation-status']=='UNCONFIRMED'
    assert all(value is False for value in provider['x-features'].values())
    config=(ROOT/AI/'config/jobs/JobsConfiguration.java').read_text()
    assert 'new org.jeecg.modules.ai.domain.StreamProviderFeatures(false,false,false,false)' in config
    metrics=(ROOT/AI/'config/jobs/AiRuntimeMetrics.java').read_text()
    assert not any(identity in metrics for identity in ('requestId','sessionId','ownerId','providerSessionId'))
    assert all(name in metrics for name in ('wgai.ai.queue.size','wgai.ai.inflight.size',
            'wgai.ai.operation.duration','wgai.ai.errors','wgai.ai.stream.events'))
    stream=(ROOT/AI/'config/jobs/StreamSessionWorker.java').read_text()
    assert stream.count('provider.start(')==1 and stream.count('provider.stop(')==1
    assert 'Work.AMBIGUOUS_START' in stream and 'Work.STOP_RECOVERY' in stream
    jobs=(ROOT/AI/'config/jobs/JobWorker.java').read_text()
    assert 'findFetchingResult(staleBefore' in jobs and 'markUncertainUnknown' in jobs
    logback=ROOT/'backend-github/jeecg-module-system/jeecg-system-start/src/main/resources/logback-spring.xml'
    ET.parse(logback)
    logtext=logback.read_text()
    assert logtext.count('SizeAndTimeBasedRollingPolicy')==3
    assert 'error-log-%d{yyyy-MM-dd}.%i.html' in logtext
    result={
        'status':'PASS_WITH_EXTERNAL_BLOCKER',
        'base':BASE,
        'changedPaths':len(changed),
        'ownershipExceptionRecordedExternally':True,
        'unexpectedPaths':unexpected,
        'frozenContractsAndMigrationsUnchanged':True,
        'dependenciesUnchanged':True,
        'productionProviderContract':'UNCONFIRMED',
        'productionCapabilitiesRemainDisabled':True,
        'metricsUseBoundedTags':True,
        'allFileLogsHaveSizeAndTimeRollover':True,
        'remotePostReplayGuardPresent':True,
        'externalBlocker':'05 has not supplied the confirmed RTX 5070 contract or real request evidence.'}
    OUT.write_text(json.dumps(result,indent=2)+'\n')
    print(result['status']+': 06 local safety and ownership checks passed; 05 remains blocking')


if __name__=='__main__':
    main()
