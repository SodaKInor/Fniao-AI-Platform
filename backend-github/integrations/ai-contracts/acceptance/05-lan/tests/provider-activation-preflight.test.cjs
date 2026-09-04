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

test('current source remains fail-closed until the confirmed owner adapter and assembly replace this receipt', () => {
  const evidence = JSON.parse(fs.readFileSync(evidencePath, 'utf8'))
  assert.equal(evidence.status, 'WAITING_OWNER_ADAPTER_AND_EXTERNAL_CONTRACT')
  assert.equal(evidence.contractVersion, '1.1.0')
  assert.deepEqual(evidence.openspecTasksCompleted, [])
  assert.equal(evidence.currentActivationResult.runtimeMayStartForRealRequests, false)
  assert.equal(evidence.currentActivationResult.realProviderRequestAttempted, false)
  assert.ok(Object.values(evidence.downstreamReleased).every(value => value === false))

  for (const item of evidence.sourceChecks) assert.equal(sha256(item.path), item.sha256, item.path)

  const configuration = read(evidence.sourceChecks[0].path)
  assert.match(configuration, /new ModeInferenceProvider\(p::getMode, availability::modeReason,\s*new MockInferenceProvider/)
  assert.doesNotMatch(configuration, /new DraftHttpProvider|new DraftVideoHttpProvider|new DraftStreamHttpProvider|new DraftArtifactReader/)
  assert.match(configuration, /new ModeArtifactReader\(new MockArtifactReader\(Clock\.systemUTC\(\)\), p\.getOutputMaxBytes\(\)\)/)
  assert.match(configuration, /new ModeVideoAnalysisProvider\(availability::videoReason, null\)/)
  assert.match(configuration, /availability::streamStopReason,\s*null\)/)

  const availability = read(evidence.sourceChecks[1].path)
  assert.match(availability, /真实服务协议尚未确认/)
  assert.match(availability, /当前模拟适配器不支持上传视频/)
  assert.match(availability, /当前模拟适配器不支持实时流/)
  assert.match(availability, /真实流会话查询协议尚未确认/)
  assert.match(availability, /真实流事件查询协议尚未确认/)
  assert.match(availability, /真实流停止协议尚未确认/)

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
  assert.match(appConfig, /mode: \$\{WGAI_INFERENCE_MODE:disabled\}/)
  assert.match(compose, /WGAI_INFERENCE_MODE: \$\{WGAI_INFERENCE_MODE:-disabled\}/)
  assert.match(compose, /read_only: true/)
  assert.doesNotMatch(compose, /^\s{2}(gpu|inference|provider):\s*$/m)

  const ancestry = spawnSync('git', ['merge-base', '--is-ancestor', evidence.inspectedCommit, 'HEAD'], {
    cwd: root, encoding: 'utf8'
  })
  assert.equal(ancestry.status, 0, 'inspected commit must remain in current history')
})
