const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { spawnSync } = require('node:child_process')
const root = path.resolve(__dirname, '../../../../../../')
const rules = require(path.join(root, 'backend-github/deploy/remote-ai/contract-intake-rules.cjs'))

function endpoint(method, route) { return { method, path: route } }
function samples(names) { return Object.fromEntries(names.map(name => [name, `/private/evidence/${name}.json`])) }
function fileCapability(name) {
  const video = name === 'video'
  return {
    status: 'ENABLED', capabilityCode: video ? 'video-file-analysis.v1' : 'image-detection.v1',
    providerCapabilityCode: 'detector', providerModelVersion: '2026.09', submit: endpoint('POST', video ? '/video-jobs' : '/infer'),
    input: { mimeTypes: [video ? 'video/mp4' : 'image/png'], maxBytes: video ? 536870912 : 10485760,
      ...(video ? { codecs: ['H.264'], maxDurationMs: 600000 } : {}) },
    result: { mediaTypes: ['application/json', 'image/png'], maxArtifactBytes: video ? 536870912 : 10485760 },
    features: { query: false, cancel: false, deduplication: false },
    samples: samples(['request', 'success', 'empty', 'error', 'representativeInput'])
  }
}
function validIntake() {
  return {
    schemaVersion: 1, status: 'CONFIRMED', environment: 'development',
    confirmation: { confirmedBy: 'provider owner', confirmedAt: '2026-09-04T08:30:00Z', providerApiVersion: '2026.09' },
    service: {
      baseUrl: 'https://gpu.dev.internal', approvedOrigin: 'https://gpu.dev.internal',
      tls: { trustMode: 'PRIVATE_CA', caFile: '/private/ca.crt', certificateSan: 'gpu.dev.internal' },
      authentication: { scheme: 'bearer', headerName: 'Authorization', credentialFile: '/private/token' },
      limits: { maxConcurrent: 1, artifactRetentionSeconds: 3600 },
      errorSemantics: { responseLostOutcome: 'UNKNOWN', transparentPostRetry: false,
        providerAuthClearsBusinessSession: false, notStartedEvidence: 'connect failure before body send',
        terminalFailureEvidence: 'typed terminal provider response' }
    },
    capabilities: {
      image: fileCapability('image'), video: fileCapability('video'),
      stream: {
        status: 'ENABLED', capabilityCode: 'video-stream-analysis.v1', providerCapabilityCode: 'events',
        providerModelVersion: '2026.09', sourceMappings: [{ localStreamSourceId: 'camera-01', providerSourceId: 'source-77' }],
        features: { sessionQuery: true, eventQuery: true, stop: true, deduplication: true },
        start: endpoint('POST', '/stream-sources/{sourceId}/sessions'),
        sessionQuery: endpoint('GET', '/stream-sessions/{sessionId}'),
        eventQuery: endpoint('GET', '/stream-sessions/{sessionId}/events'),
        stop: endpoint('POST', '/stream-sessions/{sessionId}/stop'),
        stopSemantics: { confirmedField: 'confirmed', unconfirmedOutcome: 'UNKNOWN', onlyConfirmedStops: true },
        limits: { maxConcurrent: 1, maxEventsPerPage: 100, maxSnapshotBytes: 10485760,
          snapshotMimeTypes: ['image/jpeg'] },
        samples: samples(['sourceList', 'start', 'session', 'events', 'emptyEvents', 'stop', 'error', 'snapshot'])
      }
    }
  }
}

test('a complete confirmed intake passes structural rules', () => {
  assert.deepEqual(rules.validateIntake(validIntake(), { checkFiles: false, testMode: true }), [])
})

test('the repository example remains deliberately unconfirmed', () => {
  const example = JSON.parse(fs.readFileSync(path.join(root, 'backend-github/deploy/remote-ai/contract-intake.example.json')))
  const issues = rules.validateIntake(example, { checkFiles: false })
  assert.ok(issues.some(issue => issue.field === 'status'))
  assert.ok(issues.some(issue => issue.field === 'service.baseUrl'))
  assert.ok(issues.some(issue => issue.field === 'capabilities.video.status'))
})

test('production validation rejects placeholder host, plaintext, URL credentials and inline secrets', () => {
  for (const baseUrl of ['https://gpu.invalid', 'http://10.0.0.8', 'https://user:pass@gpu.dev.internal']) {
    const value = validIntake(); value.service.baseUrl = baseUrl
    value.service.approvedOrigin = (() => { try { return new URL(baseUrl).origin } catch (_) { return '' } })()
    assert.notDeepEqual(rules.validateIntake(value, { checkFiles: false }), [])
  }
  const secret = validIntake(); secret.service.authentication.tokenValue = 'do-not-print-this'
  assert.ok(rules.validateIntake(secret, { checkFiles: false, testMode: true }).some(issue => issue.field.endsWith('tokenValue')))
  const disguised = validIntake(); disguised.service.authentication.clientSecret = 'also-hidden'
  assert.ok(rules.validateIntake(disguised, { checkFiles: false, testMode: true }).some(issue => issue.field.endsWith('clientSecret')))
})

test('stream identities cannot smuggle RTSP and confirmed stop is required for STOPPED', () => {
  const value = validIntake()
  value.capabilities.stream.sourceMappings[0].providerSourceId = 'rtsp://camera/private'
  value.capabilities.stream.stopSemantics.onlyConfirmedStops = false
  const fields = rules.validateIntake(value, { checkFiles: false, testMode: true }).map(issue => issue.field)
  assert.ok(fields.includes('capabilities.stream.sourceMappings[0].providerSourceId'))
  assert.ok(fields.includes('capabilities.stream.stopSemantics.onlyConfirmedStops'))
})

test('enabled video requires MP4 H.264, duration, bounds and evidence references', () => {
  const value = validIntake()
  value.capabilities.video.input = { mimeTypes: ['video/avi'], maxBytes: 0 }
  delete value.capabilities.video.samples.empty
  const fields = rules.validateIntake(value, { checkFiles: false, testMode: true }).map(issue => issue.field)
  assert.ok(fields.includes('capabilities.video.input.mimeTypes'))
  assert.ok(fields.includes('capabilities.video.input.codecs'))
  assert.ok(fields.includes('capabilities.video.input.maxDurationMs'))
  assert.ok(fields.includes('capabilities.video.samples.empty'))
})

test('CLI fails closed without echoing the intake contents', () => {
  const cli = path.join(root, 'backend-github/deploy/remote-ai/validate-contract-intake.cjs')
  const example = path.join(root, 'backend-github/deploy/remote-ai/contract-intake.example.json')
  const result = spawnSync(process.execPath, [cli, example], { encoding: 'utf8' })
  assert.equal(result.status, 1)
  assert.match(result.stderr, /FAIL: contract intake/)
  assert.doesNotMatch(result.stderr, /gpu-development\.invalid/)
})
