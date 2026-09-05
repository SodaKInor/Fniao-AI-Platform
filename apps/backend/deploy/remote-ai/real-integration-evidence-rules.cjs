const crypto = require('node:crypto')
const fs = require('node:fs')
const path = require('node:path')

const capabilityCodes = {
  image: 'image-detection.v1',
  video: 'video-file-analysis.v1',
  stream: 'video-stream-analysis.v1'
}
const businessEndpoints = {
  image: 'POST /ai/v1/infer',
  video: 'POST /ai/v1/video-jobs',
  stream: 'POST /ai/v1/stream-sessions'
}
const forbiddenKey = /(password|secret|token|api.?key|private.?key|authorization|credential|rtsp|base.?url|provider.?url)/i
const forbiddenText = /rtsps?:\/\/|https?:\/\/|^Bearer\s+/i
const sha256Pattern = /^[a-f0-9]{64}$/
const commitPattern = /^[a-f0-9]{40}$/
const imageDigestPattern = /^sha256:[a-f0-9]{64}$/

function validateRealIntegrationEvidence(doc, intake, options = {}) {
  const issues = []
  const checkFiles = options.checkFiles !== false
  const evidenceBaseDir = options.evidenceBaseDir || process.cwd()
  const add = (field, message) => issues.push({ field, message })
  const obj = (value, field) => {
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      add(field, 'must be an object'); return {}
    }
    return value
  }
  const text = (value, field) => {
    if (typeof value !== 'string' || !value.trim()) add(field, 'must be confirmed text')
    else if (/UNCONFIRMED|TO[_ -]?DO|待提供/i.test(value)) add(field, 'must not be a placeholder')
    return typeof value === 'string' ? value.trim() : ''
  }
  const exact = (value, expected, field) => {
    if (value !== expected) add(field, `must be ${JSON.stringify(expected)}`)
  }
  const explicit = (value, expected, field) => {
    if (typeof value !== 'boolean' || value !== expected) add(field, `must be explicitly ${expected}`)
  }
  const integer = (value, field, minimum = 0, maximum = Number.MAX_SAFE_INTEGER) => {
    if (!Number.isSafeInteger(value) || value < minimum || value > maximum) {
      add(field, `must be an integer from ${minimum} to ${maximum}`)
    }
  }
  const opaque = (value, field) => {
    const candidate = text(value, field)
    if (candidate && (candidate.length > 256 || candidate.includes('://'))) add(field, 'must be an opaque bounded identifier')
    return candidate
  }
  const sha256 = (value, field) => {
    if (typeof value !== 'string' || !sha256Pattern.test(value)) add(field, 'must be a lowercase SHA-256 digest')
  }
  const isoDate = (value, field) => {
    if (typeof value !== 'string' || Number.isNaN(Date.parse(value)) || !/T/.test(value)) add(field, 'must be an ISO-8601 timestamp')
  }
  const fileEvidence = (value, field) => {
    const item = obj(value, field)
    const file = text(item.file, `${field}.file`)
    integer(item.bytes, `${field}.bytes`, 1)
    sha256(item.sha256, `${field}.sha256`)
    explicit(item.redacted, true, `${field}.redacted`)
    if (!file || !checkFiles) return
    const resolved = path.isAbsolute(file) ? file : path.resolve(evidenceBaseDir, file)
    if (!path.isAbsolute(file) && !isWithin(evidenceBaseDir, resolved)) {
      add(`${field}.file`, 'relative evidence path must remain inside the evidence directory'); return
    }
    try {
      const stat = fs.statSync(resolved)
      if (!stat.isFile()) return add(`${field}.file`, 'must reference a regular file')
      if (stat.size !== item.bytes) add(`${field}.bytes`, 'must match the referenced file')
      const digest = crypto.createHash('sha256').update(fs.readFileSync(resolved)).digest('hex')
      if (digest !== item.sha256) add(`${field}.sha256`, 'must match the referenced file')
    } catch (_) { add(`${field}.file`, 'referenced evidence file is unavailable') }
  }

  if (!doc || typeof doc !== 'object' || Array.isArray(doc)) return [{ field: '$', message: 'must be a JSON object' }]
  if (!intake || typeof intake !== 'object' || Array.isArray(intake)) return [{ field: '$intake', message: 'must be a validated contract intake' }]
  scanSensitive(doc, '$', add)

  exact(doc.schemaVersion, 1, 'schemaVersion')
  exact(doc.status, 'COMPLETE', 'status')
  exact(doc.evidenceKind, 'REAL_RTX5070_APPLICATION_FLOW', 'evidenceKind')
  exact(doc.environment, 'development', 'environment')
  exact(doc.contractVersion, '1.1.0', 'contractVersion')
  isoDate(doc.recordedAt, 'recordedAt')
  if (typeof doc.acceptedCommonStart !== 'string' || !commitPattern.test(doc.acceptedCommonStart)) {
    add('acceptedCommonStart', 'must be a lowercase 40-character Git commit')
  }
  sha256(doc.intakeSha256, 'intakeSha256')
  if (options.intakeSha256 && doc.intakeSha256 !== options.intakeSha256) add('intakeSha256', 'must match the validated private intake bytes')
  exact(doc.providerApiVersion, intake.confirmation && intake.confirmation.providerApiVersion, 'providerApiVersion')

  validateRuntime(obj(doc.runtime, 'runtime'), { add, exact, explicit, opaque, text })
  const flows = obj(doc.flows, 'flows')
  validateFileFlow('image', flows.image, intake, { add, obj, text, exact, explicit, integer, opaque, sha256, fileEvidence })
  validateFileFlow('video', flows.video, intake, { add, obj, text, exact, explicit, integer, opaque, sha256, fileEvidence })
  validateStreamFlow(flows.stream, intake, { add, obj, text, exact, explicit, integer, opaque, sha256, isoDate, fileEvidence })
  validateSupportingEvidence(doc.supportingEvidence, { add, obj, text, fileEvidence })
  validateIntegrity(obj(doc.integrity, 'integrity'), { explicit })
  validateFallback(obj(doc.fallback, 'fallback'), { exact, explicit, text })
  return issues
}

function validateRuntime(runtime, h) {
  h.opaque(runtime.backendContainerId, 'runtime.backendContainerId')
  h.opaque(runtime.backendContainerName, 'runtime.backendContainerName')
  if (typeof runtime.backendImageDigest !== 'string' || !imageDigestPattern.test(runtime.backendImageDigest)) {
    h.add('runtime.backendImageDigest', 'must be a sha256 image digest')
  }
  if (typeof runtime.codeCommit !== 'string' || !commitPattern.test(runtime.codeCommit)) {
    h.add('runtime.codeCommit', 'must be a lowercase 40-character Git commit')
  }
  h.exact(runtime.requestOrigin, 'APPLICATION_BACKEND_CONTAINER', 'runtime.requestOrigin')
  h.exact(runtime.providerMode, 'remote', 'runtime.providerMode')
  h.explicit(runtime.simulated, false, 'runtime.simulated')
  h.explicit(runtime.mock, false, 'runtime.mock')
  h.explicit(runtime.networkRouteVerified, true, 'runtime.networkRouteVerified')
  h.explicit(runtime.tlsVerified, true, 'runtime.tlsVerified')
  h.explicit(runtime.serviceAuthenticationVerified, true, 'runtime.serviceAuthenticationVerified')
  h.explicit(runtime.approvedOriginVerified, true, 'runtime.approvedOriginVerified')
}

function validateFileFlow(name, value, intake, h) {
  const field = `flows.${name}`
  const flow = h.obj(value, field)
  const cap = intake.capabilities && intake.capabilities[name]
  if (!cap || cap.status !== 'ENABLED') h.add(field, `private intake must enable ${name} before real evidence can pass`)
  h.exact(flow.status, 'PASS', `${field}.status`)
  h.exact(flow.capabilityCode, capabilityCodes[name], `${field}.capabilityCode`)
  if (cap) {
    h.exact(flow.providerCapabilityCode, cap.providerCapabilityCode, `${field}.providerCapabilityCode`)
    h.exact(flow.providerModelVersion, cap.providerModelVersion, `${field}.providerModelVersion`)
  }
  h.exact(flow.businessEndpoint, businessEndpoints[name], `${field}.businessEndpoint`)
  h.explicit(flow.containerNetworkRequest, true, `${field}.containerNetworkRequest`)
  h.explicit(flow.encryptedTransport, true, `${field}.encryptedTransport`)
  h.explicit(flow.serviceAuthenticationApplied, true, `${field}.serviceAuthenticationApplied`)
  h.integer(flow.acceptedHttpStatus, `${field}.acceptedHttpStatus`, 200, 202)
  if (![200, 202].includes(flow.acceptedHttpStatus)) h.add(`${field}.acceptedHttpStatus`, 'must be 200 or 202')
  h.opaque(flow.requestId, `${field}.requestId`)
  h.opaque(flow.providerCorrelationId, `${field}.providerCorrelationId`)
  h.integer(flow.dispatchCount, `${field}.dispatchCount`, 1, 1)
  h.exact(flow.terminalState, 'SUCCEEDED', `${field}.terminalState`)
  h.integer(flow.durationMs, `${field}.durationMs`, 1, 86400000)
  validateInput(name, h.obj(flow.input, `${field}.input`), cap, field, h)
  h.integer(flow.representativeResultCount, `${field}.representativeResultCount`, 1, 1000000)
  validateEmptyResult(h.obj(flow.validEmptyResult, `${field}.validEmptyResult`), field, h)
  const artifacts = validateArtifacts(flow.artifacts, field, cap, h)
  if (name === 'video') validateVideo(flow, field, artifacts, h)
  h.explicit(flow.pageDisplayed, true, `${field}.pageDisplayed`)
  h.explicit(flow.historyRead, true, `${field}.historyRead`)
}

function validateInput(name, input, cap, field, h) {
  const mimeType = h.text(input.mediaType, `${field}.input.mediaType`)
  h.integer(input.bytes, `${field}.input.bytes`, 1, cap && cap.input ? cap.input.maxBytes : Number.MAX_SAFE_INTEGER)
  h.sha256(input.sha256, `${field}.input.sha256`)
  if (cap && cap.input && Array.isArray(cap.input.mimeTypes) && !cap.input.mimeTypes.includes(mimeType)) {
    h.add(`${field}.input.mediaType`, 'must be one of the confirmed provider input types')
  }
  if (name === 'video') {
    h.exact(input.codec, 'H.264', `${field}.input.codec`)
    h.integer(input.durationMs, `${field}.input.durationMs`, 1,
      cap && cap.input ? cap.input.maxDurationMs : Number.MAX_SAFE_INTEGER)
  }
}

function validateEmptyResult(empty, field, h) {
  h.opaque(empty.requestId, `${field}.validEmptyResult.requestId`)
  h.integer(empty.dispatchCount, `${field}.validEmptyResult.dispatchCount`, 1, 1)
  h.exact(empty.terminalState, 'SUCCEEDED', `${field}.validEmptyResult.terminalState`)
  h.integer(empty.resultCount, `${field}.validEmptyResult.resultCount`, 0, 0)
  h.explicit(empty.displayedAsSuccess, true, `${field}.validEmptyResult.displayedAsSuccess`)
}

function validateArtifacts(value, field, cap, h) {
  if (!Array.isArray(value) || !value.length) {
    h.add(`${field}.artifacts`, 'must contain at least one persisted result'); return []
  }
  return value.map((entry, index) => {
    const artifactField = `${field}.artifacts[${index}]`
    const artifact = h.obj(entry, artifactField)
    h.opaque(artifact.assetId, `${artifactField}.assetId`)
    h.text(artifact.resultType, `${artifactField}.resultType`)
    const mediaType = h.text(artifact.mediaType, `${artifactField}.mediaType`)
    h.integer(artifact.bytes, `${artifactField}.bytes`, 1,
      cap && cap.result ? cap.result.maxArtifactBytes : Number.MAX_SAFE_INTEGER)
    h.sha256(artifact.sha256, `${artifactField}.sha256`)
    if (cap && cap.result && Array.isArray(cap.result.mediaTypes) && !cap.result.mediaTypes.includes(mediaType)) {
      h.add(`${artifactField}.mediaType`, 'must be one of the confirmed provider result types')
    }
    h.explicit(artifact.persisted, true, `${artifactField}.persisted`)
    h.explicit(artifact.ownerAccessVerified, true, `${artifactField}.ownerAccessVerified`)
    h.explicit(artifact.downloadVerified, true, `${artifactField}.downloadVerified`)
    h.fileEvidence(artifact.capture, `${artifactField}.capture`)
    return artifact
  })
}

function validateVideo(flow, field, artifacts, h) {
  const snapshotIds = new Set(artifacts.filter(item => item.resultType === 'SNAPSHOT').map(item => item.assetId))
  if (!snapshotIds.size) h.add(`${field}.artifacts`, 'video evidence must include at least one SNAPSHOT')
  if (!Array.isArray(flow.events) || !flow.events.length) return h.add(`${field}.events`, 'must contain a timestamped event timeline')
  let previous = -1
  flow.events.forEach((entry, index) => {
    const eventField = `${field}.events[${index}]`
    const event = h.obj(entry, eventField)
    h.opaque(event.eventId, `${eventField}.eventId`)
    h.integer(event.offsetMs, `${eventField}.offsetMs`, 0, flow.input && flow.input.durationMs)
    if (Number.isSafeInteger(event.offsetMs) && event.offsetMs < previous) h.add(`${eventField}.offsetMs`, 'must be in nondecreasing order')
    previous = event.offsetMs
    const assetId = h.opaque(event.snapshotAssetId, `${eventField}.snapshotAssetId`)
    if (assetId && !snapshotIds.has(assetId)) h.add(`${eventField}.snapshotAssetId`, 'must reference a persisted SNAPSHOT artifact')
  })
  if (flow.annotatedVideoPresent === true && !artifacts.some(item => item.resultType === 'ANNOTATED_VIDEO')) {
    h.add(`${field}.annotatedVideoPresent`, 'requires an ANNOTATED_VIDEO artifact')
  }
  if (typeof flow.annotatedVideoPresent !== 'boolean') h.add(`${field}.annotatedVideoPresent`, 'must be an explicit boolean')
}

function validateStreamFlow(value, intake, h) {
  const field = 'flows.stream'
  const flow = h.obj(value, field)
  const cap = intake.capabilities && intake.capabilities.stream
  if (!cap || cap.status !== 'ENABLED') h.add(field, 'private intake must enable stream before real evidence can pass')
  h.exact(flow.status, 'PASS', `${field}.status`)
  h.exact(flow.capabilityCode, capabilityCodes.stream, `${field}.capabilityCode`)
  if (cap) {
    h.exact(flow.providerCapabilityCode, cap.providerCapabilityCode, `${field}.providerCapabilityCode`)
    h.exact(flow.providerModelVersion, cap.providerModelVersion, `${field}.providerModelVersion`)
  }
  h.exact(flow.businessEndpoint, businessEndpoints.stream, `${field}.businessEndpoint`)
  h.explicit(flow.browserSubmittedOpaqueSourceIdOnly, true, `${field}.browserSubmittedOpaqueSourceIdOnly`)
  h.explicit(flow.containerNetworkRequest, true, `${field}.containerNetworkRequest`)
  h.explicit(flow.encryptedTransport, true, `${field}.encryptedTransport`)
  h.explicit(flow.serviceAuthenticationApplied, true, `${field}.serviceAuthenticationApplied`)
  h.integer(flow.acceptedHttpStatus, `${field}.acceptedHttpStatus`, 200, 202)
  if (![200, 202].includes(flow.acceptedHttpStatus)) h.add(`${field}.acceptedHttpStatus`, 'must be 200 or 202')
  h.opaque(flow.sessionId, `${field}.sessionId`)
  h.opaque(flow.providerSessionId, `${field}.providerSessionId`)
  h.integer(flow.startDispatchCount, `${field}.startDispatchCount`, 1, 1)
  h.integer(flow.durationMs, `${field}.durationMs`, 1, 86400000)
  const artifacts = validateArtifacts(flow.artifacts, field, streamArtifactLimits(cap), h)
  const snapshotIds = new Set(artifacts.filter(item => item.resultType === 'SNAPSHOT').map(item => item.assetId))
  if (!snapshotIds.size) h.add(`${field}.artifacts`, 'stream evidence must include at least one SNAPSHOT')
  if (!Array.isArray(flow.events) || !flow.events.length) h.add(`${field}.events`, 'must contain at least one real event')
  else flow.events.forEach((entry, index) => {
    const eventField = `${field}.events[${index}]`
    const event = h.obj(entry, eventField)
    h.opaque(event.eventId, `${eventField}.eventId`)
    h.isoDate(event.occurredAt, `${eventField}.occurredAt`)
    const assetId = h.opaque(event.snapshotAssetId, `${eventField}.snapshotAssetId`)
    if (assetId && !snapshotIds.has(assetId)) h.add(`${eventField}.snapshotAssetId`, 'must reference a persisted SNAPSHOT artifact')
  })
  h.explicit(flow.validEmptyEventPageObserved, true, `${field}.validEmptyEventPageObserved`)
  h.explicit(flow.pageDisplayed, true, `${field}.pageDisplayed`)
  h.explicit(flow.historyRead, true, `${field}.historyRead`)
  validateStop(h.obj(flow.stop, `${field}.stop`), cap, field, h)
}

function streamArtifactLimits(cap) {
  if (!cap) return null
  return { result: { mediaTypes: cap.limits && cap.limits.snapshotMimeTypes, maxArtifactBytes: cap.limits && cap.limits.maxSnapshotBytes } }
}

function validateStop(stop, cap, field, h) {
  const supported = Boolean(cap && cap.features && cap.features.stop)
  h.explicit(stop.supported, supported, `${field}.stop.supported`)
  if (supported) {
    h.explicit(stop.attempted, true, `${field}.stop.attempted`)
    h.integer(stop.dispatchCount, `${field}.stop.dispatchCount`, 1, 1)
    h.explicit(stop.providerConfirmed, true, `${field}.stop.providerConfirmed`)
    h.exact(stop.terminalState, 'STOPPED', `${field}.stop.terminalState`)
  } else {
    h.explicit(stop.attempted, false, `${field}.stop.attempted`)
    h.explicit(stop.providerConfirmed, false, `${field}.stop.providerConfirmed`)
    h.exact(stop.outcome, 'UNSUPPORTED', `${field}.stop.outcome`)
    if (stop.terminalState === 'STOPPED') h.add(`${field}.stop.terminalState`, 'must not be STOPPED when provider stop is unsupported')
  }
}

function validateSupportingEvidence(value, h) {
  const required = new Set(['CONTAINER_REQUEST', 'BROWSER_FLOW', 'HISTORY_READ', 'SERVICE_VERSION'])
  if (!Array.isArray(value) || !value.length) return h.add('supportingEvidence', 'must contain redacted supporting evidence')
  value.forEach((entry, index) => {
    const field = `supportingEvidence[${index}]`
    const item = h.obj(entry, field)
    const kind = h.text(item.kind, `${field}.kind`)
    required.delete(kind)
    h.fileEvidence(item.capture, `${field}.capture`)
  })
  for (const kind of required) h.add('supportingEvidence', `must include ${kind}`)
}

function validateIntegrity(integrity, h) {
  h.explicit(integrity.unknownMarkedSuccessful, false, 'integrity.unknownMarkedSuccessful')
  h.explicit(integrity.unconfirmedStopMarkedStopped, false, 'integrity.unconfirmedStopMarkedStopped')
  h.explicit(integrity.duplicateDispatchObserved, false, 'integrity.duplicateDispatchObserved')
  h.explicit(integrity.hostRequestUsedAsAcceptance, false, 'integrity.hostRequestUsedAsAcceptance')
  h.explicit(integrity.unconfirmedCapabilityEnabled, false, 'integrity.unconfirmedCapabilityEnabled')
  h.explicit(integrity.sensitiveDataPresent, false, 'integrity.sensitiveDataPresent')
}

function validateFallback(fallback, h) {
  h.exact(fallback.mode, 'disabled', 'fallback.mode')
  h.text(fallback.procedure, 'fallback.procedure')
  h.explicit(fallback.unconfirmedFormatsRemainDisabled, true, 'fallback.unconfirmedFormatsRemainDisabled')
  h.explicit(fallback.historicalAssetsPreserved, true, 'fallback.historicalAssetsPreserved')
}

function scanSensitive(value, field, add) {
  if (typeof value === 'string') {
    if (forbiddenText.test(value)) add(field, 'must not contain provider URLs, RTSP URLs, or inline authorization values')
    return
  }
  if (!value || typeof value !== 'object') return
  Object.entries(value).forEach(([key, child]) => {
    const next = field === '$' ? key : `${field}.${key}`
    if (forbiddenKey.test(key)) add(next, 'sensitive or provider-coordinate fields are forbidden from committed evidence')
    scanSensitive(child, next, add)
  })
}

function isWithin(base, candidate) {
  const relative = path.relative(path.resolve(base), candidate)
  return relative === '' || (!relative.startsWith('..' + path.sep) && relative !== '..' && !path.isAbsolute(relative))
}

module.exports = { validateRealIntegrationEvidence }
