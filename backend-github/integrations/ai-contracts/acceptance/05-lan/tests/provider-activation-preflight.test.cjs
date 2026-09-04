const test = require('node:test')
const assert = require('node:assert/strict')
const crypto = require('node:crypto')
const fs = require('node:fs')
const path = require('node:path')
const { spawnSync } = require('node:child_process')

const root = path.resolve(__dirname, '../../../../../../')
const evidencePath = path.join(root,
  'backend-github/integrations/ai-contracts/acceptance/05-lan/provider-activation-preflight.json')

function read(relative) { return fs.readFileSync(path.join(root, relative), 'utf8') }
function sha256(relative) {
  return crypto.createHash('sha256').update(fs.readFileSync(path.join(root, relative))).digest('hex')
}

test('development stub is explicit while every real-provider path remains fail-closed', () => {
  const evidence = JSON.parse(fs.readFileSync(evidencePath, 'utf8'))
  assert.equal(evidence.status, 'PASS_EXPLICIT_DEVELOPMENT_STUB_REAL_FAIL_CLOSED')
  assert.equal(evidence.contractVersion, '1.1.0')
  assert.deepEqual(evidence.openspecTasksCompleted, ['5.1', '5.2', '5.3', '5.4'])
  assert.deepEqual(evidence.realIntegrationTasksCompleted, [])
  assert.equal(evidence.currentActivationResult.developmentStubRuntimeMayStart, true)
  assert.equal(evidence.currentActivationResult.runtimeMayStartForRealRequests, false)
  assert.equal(evidence.currentActivationResult.realProviderRequestAttempted, false)
  assert.ok(Object.values(evidence.downstreamReleased).every(value => value === false))

  for (const item of evidence.sourceChecks) assert.equal(sha256(item.path), item.sha256, item.path)

  const configuration = read(evidence.sourceChecks[0].path)
  assert.match(configuration, /developmentStubTransport\(p\)/)
  assert.match(configuration, /new DraftHttpProvider|new DraftVideoHttpProvider|new DraftStreamHttpProvider|new DraftArtifactReader/)
  assert.match(configuration, /!properties\.isDevelopmentStub\(\).*return null/s)
  assert.match(configuration, /!"remote"\.equals\(properties\.getMode\(\)\)/)
  assert.match(configuration, /!"stub"\.equals\(properties\.getProviderKey\(\)\)/)

  const availability = read(evidence.sourceChecks[1].path)
  assert.match(availability, /真实服务协议尚未确认/)
  assert.match(availability, /properties\.isDevelopmentStub\(\) && "remote"\.equals\(properties\.getMode\(\)\)/)
  assert.match(availability, /&& "stub"\.equals\(properties\.getProviderKey\(\)\)/)
  assert.match(availability, /开发 stub 只接受明确标记的模拟能力/)

  const inference = read(evidence.sourceChecks[2].path)
  const artifacts = read(evidence.sourceChecks[3].path)
  const video = read(evidence.sourceChecks[4].path)
  const stream = read(evidence.sourceChecks[5].path)
  assert.match(inference, /"remote"\.equals\(mode\.get\(\)\) && remote != null/)
  assert.match(artifacts, /if \(remote == null\).*真实成果下载协议尚未确认/)
  assert.match(video, /if \(remote == null\).*真实上传视频协议尚未确认/)
  assert.match(stream, /if \(remote == null\).*真实实时流协议尚未确认/)

  const appConfig = read(evidence.sourceChecks[6].path)
  const compose = read(evidence.sourceChecks[7].path)
  const stubCompose = read(evidence.sourceChecks[8].path)
  assert.match(appConfig, /mode: \$\{WGAI_INFERENCE_MODE:disabled\}/)
  assert.match(appConfig, /development-stub: \$\{WGAI_INFERENCE_DEVELOPMENT_STUB:false\}/)
  assert.match(compose, /WGAI_INFERENCE_MODE: \$\{WGAI_INFERENCE_MODE:-disabled\}/)
  assert.match(compose, /WGAI_INFERENCE_DEVELOPMENT_STUB: \$\{WGAI_INFERENCE_DEVELOPMENT_STUB:-false\}/)
  assert.match(compose, /read_only: true/)
  assert.doesNotMatch(compose, /^\s{2}(gpu|inference|provider|remote-ai-stub):\s*$/m)
  assert.match(stubCompose, /profiles: \[remote-ai-stub\]/)
  assert.match(stubCompose, /WGAI_INFERENCE_DEVELOPMENT_STUB: "true"/)
  assert.doesNotMatch(stubCompose, /^\s+ports:\s*$/m)

  const ancestry = spawnSync('git', ['merge-base', '--is-ancestor', evidence.inspectedCommit, 'HEAD'], {
    cwd: root, encoding: 'utf8'
  })
  assert.equal(ancestry.status, 0, 'inspected commit must remain in current history')
})
