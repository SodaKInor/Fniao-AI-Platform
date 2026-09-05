const test = require('node:test')
const assert = require('node:assert/strict')
const crypto = require('node:crypto')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { spawnSync } = require('node:child_process')

const root = path.resolve(__dirname, '../../../../../../')
const rules = require(path.join(root, 'backend-github/deploy/remote-ai/real-integration-evidence-rules.cjs'))
const digest = 'a'.repeat(64)
const commit = 'b23f2fc8c5d1911af61dd0f55ad6a89d73c0d09d'

function endpoint(method, route) { return { method, path: route } }
function sampleRefs(file) {
  return Object.fromEntries(['request', 'success', 'empty', 'error', 'representativeInput', 'sourceList',
    'start', 'session', 'events', 'emptyEvents', 'stop', 'snapshot'].map(name => [name, file]))
}
function fileCapability(name, file) {
  const video = name === 'video'
  return {
    status: 'ENABLED', capabilityCode: video ? 'video-file-analysis.v1' : 'image-detection.v1',
    providerCapabilityCode: `${name}-detector`, providerModelVersion: '2026.09',
    submit: endpoint('POST', video ? '/video-jobs' : '/infer'),
    input: { mimeTypes: [video ? 'video/mp4' : 'image/png'], maxBytes: video ? 536870912 : 10485760,
      ...(video ? { codecs: ['H.264'], maxDurationMs: 600000 } : {}) },
    result: { mediaTypes: ['application/json', 'image/png', 'image/jpeg', 'video/mp4'],
      maxArtifactBytes: video ? 536870912 : 10485760 },
    features: { query: false, cancel: false, deduplication: false },
    samples: sampleRefs(file)
  }
}
function validIntake(file = '/private/evidence.json') {
  return {
    schemaVersion: 1, status: 'CONFIRMED', environment: 'development',
    confirmation: { confirmedBy: 'provider owner', confirmedAt: '2026-09-04T08:30:00Z', providerApiVersion: '2026.09' },
    service: {
      baseUrl: 'https://gpu.dev.internal', approvedOrigin: 'https://gpu.dev.internal',
      tls: { trustMode: 'PRIVATE_CA', caFile: file, certificateSan: 'gpu.dev.internal' },
      authentication: { scheme: 'bearer', headerName: 'Authorization', credentialFile: file },
      limits: { maxConcurrent: 1, artifactRetentionSeconds: 3600 },
      errorSemantics: { responseLostOutcome: 'UNKNOWN', transparentPostRetry: false,
        providerAuthClearsBusinessSession: false, notStartedEvidence: 'failure before body send',
        terminalFailureEvidence: 'typed terminal response' }
    },
    capabilities: {
      image: fileCapability('image', file), video: fileCapability('video', file),
      stream: {
        status: 'ENABLED', capabilityCode: 'video-stream-analysis.v1', providerCapabilityCode: 'stream-events',
        providerModelVersion: '2026.09', sourceMappings: [{ localStreamSourceId: 'camera-01', providerSourceId: 'source-77' }],
        features: { sessionQuery: true, eventQuery: true, stop: true, deduplication: true },
        start: endpoint('POST', '/stream-sources/{sourceId}/sessions'),
        sessionQuery: endpoint('GET', '/stream-sessions/{sessionId}'),
        eventQuery: endpoint('GET', '/stream-sessions/{sessionId}/events'),
        stop: endpoint('POST', '/stream-sessions/{sessionId}/stop'),
        stopSemantics: { confirmedField: 'confirmed', unconfirmedOutcome: 'UNKNOWN', onlyConfirmedStops: true },
        limits: { maxConcurrent: 1, maxEventsPerPage: 100, maxSnapshotBytes: 10485760,
          snapshotMimeTypes: ['image/jpeg'] },
        samples: sampleRefs(file)
      }
    }
  }
}
function capture(file = 'trace.txt', bytes = 8, sha256 = digest) {
  return { file, bytes, sha256, redacted: true }
}
function artifact(assetId, resultType, mediaType, file) {
  return { assetId, resultType, mediaType, bytes: 8, sha256: digest, persisted: true,
    ownerAccessVerified: true, downloadVerified: true, capture: capture(file) }
}
function commonFileFlow(name, file) {
  return {
    status: 'PASS', capabilityCode: name === 'image' ? 'image-detection.v1' : 'video-file-analysis.v1',
    providerCapabilityCode: `${name}-detector`, providerModelVersion: '2026.09',
    businessEndpoint: name === 'image' ? 'POST /ai/v1/infer' : 'POST /ai/v1/video-jobs',
    containerNetworkRequest: true, encryptedTransport: true, serviceAuthenticationApplied: true,
    acceptedHttpStatus: 202, requestId: `${name}-request-1`, providerCorrelationId: `${name}-provider-1`,
    dispatchCount: 1, terminalState: 'SUCCEEDED', durationMs: 2500,
    input: { mediaType: name === 'image' ? 'image/png' : 'video/mp4', bytes: 8, sha256: digest,
      ...(name === 'video' ? { codec: 'H.264', durationMs: 10000 } : {}) },
    representativeResultCount: 1,
    validEmptyResult: { requestId: `${name}-empty-1`, dispatchCount: 1, terminalState: 'SUCCEEDED',
      resultCount: 0, displayedAsSuccess: true },
    artifacts: [artifact(`${name}-asset-1`, name === 'image' ? 'DETECTION_JSON' : 'SNAPSHOT',
      name === 'image' ? 'application/json' : 'image/jpeg', file)],
    pageDisplayed: true, historyRead: true,
    ...(name === 'video' ? { events: [{ eventId: 'video-event-1', offsetMs: 1200, snapshotAssetId: 'video-asset-1' }],
      annotatedVideoPresent: false } : {})
  }
}
function validEvidence(file = 'trace.txt') {
  return {
    schemaVersion: 1, status: 'COMPLETE', evidenceKind: 'REAL_RTX5070_APPLICATION_FLOW',
    environment: 'development', recordedAt: '2026-09-04T09:00:00Z', acceptedCommonStart: commit,
    contractVersion: '1.1.0', providerApiVersion: '2026.09', intakeSha256: digest,
    runtime: { backendContainerId: 'container-05', backendContainerName: 'wgai-ri-05-backend-1',
      backendImageDigest: `sha256:${digest}`, codeCommit: commit, requestOrigin: 'APPLICATION_BACKEND_CONTAINER',
      providerMode: 'remote', simulated: false, mock: false, networkRouteVerified: true, tlsVerified: true,
      serviceAuthenticationVerified: true, approvedOriginVerified: true },
    flows: {
      image: commonFileFlow('image', file),
      video: commonFileFlow('video', file),
      stream: {
        status: 'PASS', capabilityCode: 'video-stream-analysis.v1', providerCapabilityCode: 'stream-events',
        providerModelVersion: '2026.09', businessEndpoint: 'POST /ai/v1/stream-sessions',
        browserSubmittedOpaqueSourceIdOnly: true, containerNetworkRequest: true, encryptedTransport: true,
        serviceAuthenticationApplied: true, acceptedHttpStatus: 202, sessionId: 'session-1',
        providerSessionId: 'provider-session-1', startDispatchCount: 1, durationMs: 5000,
        artifacts: [artifact('stream-snapshot-1', 'SNAPSHOT', 'image/jpeg', file)],
        events: [{ eventId: 'stream-event-1', occurredAt: '2026-09-04T09:00:01Z', snapshotAssetId: 'stream-snapshot-1' }],
        validEmptyEventPageObserved: true, pageDisplayed: true, historyRead: true,
        stop: { supported: true, attempted: true, dispatchCount: 1, providerConfirmed: true, terminalState: 'STOPPED' }
      }
    },
    supportingEvidence: ['CONTAINER_REQUEST', 'BROWSER_FLOW', 'HISTORY_READ', 'SERVICE_VERSION']
      .map(kind => ({ kind, capture: capture(file) })),
    integrity: { unknownMarkedSuccessful: false, unconfirmedStopMarkedStopped: false,
      duplicateDispatchObserved: false, hostRequestUsedAsAcceptance: false,
      unconfirmedCapabilityEnabled: false, sensitiveDataPresent: false },
    fallback: { mode: 'disabled', procedure: 'Switch provider mode to disabled and retain local assets.',
      unconfirmedFormatsRemainDisabled: true, historicalAssetsPreserved: true }
  }
}

test('complete real application-flow evidence passes the release-level rules', () => {
  assert.deepEqual(rules.validateRealIntegrationEvidence(validEvidence(), validIntake(), {
    checkFiles: false, intakeSha256: digest
  }), [])
})

test('mock, host acceptance, duplicate dispatch, and UNKNOWN-as-success are rejected', () => {
  const evidence = validEvidence()
  evidence.runtime.mock = true
  evidence.runtime.requestOrigin = 'HOST'
  evidence.flows.image.dispatchCount = 2
  evidence.flows.video.terminalState = 'UNKNOWN'
  evidence.integrity.unknownMarkedSuccessful = true
  evidence.integrity.hostRequestUsedAsAcceptance = true
  const fields = rules.validateRealIntegrationEvidence(evidence, validIntake(), {
    checkFiles: false, intakeSha256: digest
  }).map(issue => issue.field)
  assert.ok(fields.includes('runtime.mock'))
  assert.ok(fields.includes('runtime.requestOrigin'))
  assert.ok(fields.includes('flows.image.dispatchCount'))
  assert.ok(fields.includes('flows.video.terminalState'))
  assert.ok(fields.includes('integrity.unknownMarkedSuccessful'))
  assert.ok(fields.includes('integrity.hostRequestUsedAsAcceptance'))
})

test('evidence is bound to the private intake and all three enabled capabilities', () => {
  const intake = validIntake()
  intake.capabilities.video.status = 'UNSUPPORTED'
  intake.capabilities.video.unavailableReason = 'not provided'
  const evidence = validEvidence()
  evidence.intakeSha256 = 'b'.repeat(64)
  evidence.flows.image.providerModelVersion = 'guessed-version'
  const fields = rules.validateRealIntegrationEvidence(evidence, intake, {
    checkFiles: false, intakeSha256: digest
  }).map(issue => issue.field)
  assert.ok(fields.includes('intakeSha256'))
  assert.ok(fields.includes('flows.image.providerModelVersion'))
  assert.ok(fields.includes('flows.video'))
})

test('STOPPED requires confirmed provider support and confirmation', () => {
  const evidence = validEvidence()
  evidence.flows.stream.stop.providerConfirmed = false
  const fields = rules.validateRealIntegrationEvidence(evidence, validIntake(), {
    checkFiles: false, intakeSha256: digest
  }).map(issue => issue.field)
  assert.ok(fields.includes('flows.stream.stop.providerConfirmed'))

  const unsupportedIntake = validIntake()
  unsupportedIntake.capabilities.stream.features.stop = false
  unsupportedIntake.capabilities.stream.stopSemantics = { unsupportedOutcome: 'UNSUPPORTED' }
  const unsupportedEvidence = validEvidence()
  unsupportedEvidence.flows.stream.stop = { supported: false, attempted: false, providerConfirmed: false,
    outcome: 'UNSUPPORTED', terminalState: 'UNKNOWN' }
  assert.deepEqual(rules.validateRealIntegrationEvidence(unsupportedEvidence, unsupportedIntake, {
    checkFiles: false, intakeSha256: digest
  }), [])
})

test('the CLI verifies referenced bytes and does not echo provider coordinates', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'wgai-real-evidence-'))
  const trace = path.join(dir, 'trace.txt')
  const bytes = Buffer.from('redacted')
  fs.writeFileSync(trace, bytes)
  const actualDigest = crypto.createHash('sha256').update(bytes).digest('hex')
  const intake = validIntake(trace)
  const intakeRaw = Buffer.from(JSON.stringify(intake))
  const intakePath = path.join(dir, 'intake.json')
  fs.writeFileSync(intakePath, intakeRaw)
  const evidence = validEvidence('trace.txt')
  evidence.intakeSha256 = crypto.createHash('sha256').update(intakeRaw).digest('hex')
  evidence.runtime.backendImageDigest = `sha256:${actualDigest}`
  const replaceCapture = item => { item.capture = capture('trace.txt', bytes.length, actualDigest) }
  evidence.flows.image.input.sha256 = actualDigest
  evidence.flows.video.input.sha256 = actualDigest
  evidence.flows.image.artifacts.forEach(item => { item.sha256 = actualDigest; replaceCapture(item) })
  evidence.flows.video.artifacts.forEach(item => { item.sha256 = actualDigest; replaceCapture(item) })
  evidence.flows.stream.artifacts.forEach(item => { item.sha256 = actualDigest; replaceCapture(item) })
  evidence.supportingEvidence.forEach(replaceCapture)
  const evidencePath = path.join(dir, 'evidence.json')
  fs.writeFileSync(evidencePath, JSON.stringify(evidence))
  const cli = path.join(root, 'backend-github/deploy/remote-ai/validate-real-integration-evidence.cjs')
  const pass = spawnSync(process.execPath, [cli, intakePath, evidencePath], { encoding: 'utf8' })
  assert.equal(pass.status, 0, pass.stderr)
  assert.match(pass.stdout, /PASS: complete real RTX 5070/)

  evidence.providerCoordinate = 'https://sensitive-provider.internal'
  fs.writeFileSync(evidencePath, JSON.stringify(evidence))
  const fail = spawnSync(process.execPath, [cli, intakePath, evidencePath], { encoding: 'utf8' })
  assert.equal(fail.status, 1)
  assert.match(fail.stderr, /providerCoordinate/)
  assert.doesNotMatch(fail.stderr, /sensitive-provider/)
  fs.rmSync(dir, { recursive: true, force: true })
})
