const fs = require('node:fs')
const path = require('node:path')

const capabilityCodes = {
  image: 'image-detection.v1',
  video: 'video-file-analysis.v1',
  stream: 'video-stream-analysis.v1'
}
const reservedHosts = /(^localhost$)|(^127\.)|(^0\.0\.0\.0$)|(^\[?::1\]?$)|(\.(invalid|example|test)$)/i
const forbiddenKey = /(password|secret|token|api.?key|private.?key|authorization|credential)/i
const allowedSecretReferenceKey = /^credentialFile$/i
const forbiddenText = /rtsps?:\/\/|^Bearer\s+/i

function validateIntake(doc, options = {}) {
  const issues = []
  const checkFiles = options.checkFiles !== false
  const testMode = options.testMode === true
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
  const bool = (value, field) => {
    if (typeof value !== 'boolean') add(field, 'must be an explicit boolean')
    return value === true
  }
  const positive = (value, field, max) => {
    if (!Number.isInteger(value) || value <= 0 || value > max) add(field, `must be an integer from 1 to ${max}`)
  }
  const fileRef = (value, field, sensitive = false) => {
    const candidate = text(value, field)
    if (!candidate) return
    if (!path.isAbsolute(candidate)) add(field, 'must be an absolute file reference')
    if (sensitive && candidate.startsWith(process.cwd() + path.sep)) add(field, 'must remain outside the repository')
    if (checkFiles) {
      try { if (!fs.statSync(candidate).isFile()) add(field, 'must reference a regular file') }
      catch (_) { add(field, 'referenced file is unavailable') }
    }
  }
  const endpoint = (value, field, method) => {
    const ep = obj(value, field)
    if (ep.method !== method) add(field + '.method', `must be ${method}`)
    const route = text(ep.path, field + '.path')
    if (route && (!route.startsWith('/') || route.includes('://') || route.includes('?') || route.includes('..'))) {
      add(field + '.path', 'must be a relative absolute-path without URL, query, or traversal')
    }
  }
  const texts = (value, field, required) => {
    if (!Array.isArray(value) || (required && !value.length)) return add(field, 'must be a non-empty string array')
    value.forEach((item, index) => text(item, `${field}[${index}]`))
  }
  const samples = (value, field, names) => {
    const refs = obj(value, field)
    names.forEach(name => fileRef(refs[name], `${field}.${name}`))
  }

  if (!doc || typeof doc !== 'object' || Array.isArray(doc)) return [{ field: '$', message: 'must be a JSON object' }]
  scanSecrets(doc, '$', add)
  if (doc.schemaVersion !== 1) add('schemaVersion', 'must be 1')
  if (doc.status !== 'CONFIRMED') add('status', 'must be CONFIRMED')
  if (doc.environment !== 'development') add('environment', 'must be development')

  const confirmation = obj(doc.confirmation, 'confirmation')
  text(confirmation.confirmedBy, 'confirmation.confirmedBy')
  text(confirmation.providerApiVersion, 'confirmation.providerApiVersion')
  if (!validDate(confirmation.confirmedAt)) add('confirmation.confirmedAt', 'must be an ISO-8601 timestamp')

  const service = obj(doc.service, 'service')
  validateService(service, { add, obj, text, bool, positive, fileRef, testMode })
  const capabilities = obj(doc.capabilities, 'capabilities')
  validateFileCapability('image', capabilities.image, { add, obj, text, bool, positive, endpoint, texts, samples })
  validateFileCapability('video', capabilities.video, { add, obj, text, bool, positive, endpoint, texts, samples })
  validateStream(capabilities.stream, { add, obj, text, bool, positive, endpoint, texts, samples })
  return issues
}

function validateService(service, h) {
  let url
  try { url = new URL(service.baseUrl) } catch (_) { h.add('service.baseUrl', 'must be a valid URL') }
  if (url) {
    if (url.protocol !== 'https:') h.add('service.baseUrl', 'must use HTTPS')
    if (url.username || url.password) h.add('service.baseUrl', 'must not contain credentials')
    if (url.search || url.hash) h.add('service.baseUrl', 'must not contain query or fragment')
    if (!h.testMode && reservedHosts.test(url.hostname)) h.add('service.baseUrl', 'must not use a placeholder or loopback host')
    if (service.approvedOrigin !== url.origin) h.add('service.approvedOrigin', 'must exactly match the service origin')
  }
  const tls = h.obj(service.tls, 'service.tls')
  if (!['SYSTEM', 'PRIVATE_CA'].includes(tls.trustMode)) h.add('service.tls.trustMode', 'must be SYSTEM or PRIVATE_CA')
  h.text(tls.certificateSan, 'service.tls.certificateSan')
  if (tls.trustMode === 'PRIVATE_CA') h.fileRef(tls.caFile, 'service.tls.caFile', true)
  const auth = h.obj(service.authentication, 'service.authentication')
  if (!['bearer', 'api-key', 'mTLS', 'signed'].includes(auth.scheme)) h.add('service.authentication.scheme', 'must be a supported confirmed scheme')
  if (auth.scheme !== 'mTLS') h.text(auth.headerName, 'service.authentication.headerName')
  h.fileRef(auth.credentialFile, 'service.authentication.credentialFile', true)
  const limits = h.obj(service.limits, 'service.limits')
  h.positive(limits.maxConcurrent, 'service.limits.maxConcurrent', 100)
  h.positive(limits.artifactRetentionSeconds, 'service.limits.artifactRetentionSeconds', 31536000)
  const errors = h.obj(service.errorSemantics, 'service.errorSemantics')
  if (errors.responseLostOutcome !== 'UNKNOWN') h.add('service.errorSemantics.responseLostOutcome', 'must be UNKNOWN')
  if (h.bool(errors.transparentPostRetry, 'service.errorSemantics.transparentPostRetry')) h.add('service.errorSemantics.transparentPostRetry', 'must be false')
  if (h.bool(errors.providerAuthClearsBusinessSession, 'service.errorSemantics.providerAuthClearsBusinessSession')) h.add('service.errorSemantics.providerAuthClearsBusinessSession', 'must be false')
  h.text(errors.notStartedEvidence, 'service.errorSemantics.notStartedEvidence')
  h.text(errors.terminalFailureEvidence, 'service.errorSemantics.terminalFailureEvidence')
}

function validateFileCapability(name, value, h) {
  const cap = h.obj(value, `capabilities.${name}`)
  if (cap.capabilityCode !== capabilityCodes[name]) h.add(`capabilities.${name}.capabilityCode`, 'must match the frozen business code')
  if (cap.status === 'UNSUPPORTED') return h.text(cap.unavailableReason, `capabilities.${name}.unavailableReason`)
  if (cap.status !== 'ENABLED') return h.add(`capabilities.${name}.status`, 'must be ENABLED or explicitly UNSUPPORTED')
  h.text(cap.providerCapabilityCode, `capabilities.${name}.providerCapabilityCode`)
  h.text(cap.providerModelVersion, `capabilities.${name}.providerModelVersion`)
  h.endpoint(cap.submit, `capabilities.${name}.submit`, 'POST')
  const input = h.obj(cap.input, `capabilities.${name}.input`)
  h.texts(input.mimeTypes, `capabilities.${name}.input.mimeTypes`, true)
  if (name === 'video' && (!input.mimeTypes || !input.mimeTypes.includes('video/mp4'))) h.add('capabilities.video.input.mimeTypes', 'must include video/mp4')
  if (name === 'video' && (!Array.isArray(input.codecs) || !input.codecs.includes('H.264'))) h.add('capabilities.video.input.codecs', 'must include H.264')
  h.positive(input.maxBytes, `capabilities.${name}.input.maxBytes`, 2147483648)
  if (name === 'video') h.positive(input.maxDurationMs, 'capabilities.video.input.maxDurationMs', 86400000)
  const result = h.obj(cap.result, `capabilities.${name}.result`)
  h.texts(result.mediaTypes, `capabilities.${name}.result.mediaTypes`, true)
  h.positive(result.maxArtifactBytes, `capabilities.${name}.result.maxArtifactBytes`, 2147483648)
  const features = h.obj(cap.features, `capabilities.${name}.features`)
  ;['query', 'cancel', 'deduplication'].forEach(key => h.bool(features[key], `capabilities.${name}.features.${key}`))
  if (features.query) h.endpoint(cap.query, `capabilities.${name}.query`, 'GET')
  if (features.cancel) h.endpoint(cap.cancel, `capabilities.${name}.cancel`, 'POST')
  h.samples(cap.samples, `capabilities.${name}.samples`, name === 'video'
    ? ['request', 'success', 'empty', 'error', 'representativeInput'] : ['request', 'success', 'empty', 'error', 'representativeInput'])
}

function validateStream(value, h) {
  const cap = h.obj(value, 'capabilities.stream')
  if (cap.capabilityCode !== capabilityCodes.stream) h.add('capabilities.stream.capabilityCode', 'must match the frozen business code')
  if (cap.status === 'UNSUPPORTED') return h.text(cap.unavailableReason, 'capabilities.stream.unavailableReason')
  if (cap.status !== 'ENABLED') return h.add('capabilities.stream.status', 'must be ENABLED or explicitly UNSUPPORTED')
  h.text(cap.providerCapabilityCode, 'capabilities.stream.providerCapabilityCode')
  h.text(cap.providerModelVersion, 'capabilities.stream.providerModelVersion')
  const mappings = cap.sourceMappings
  if (!Array.isArray(mappings) || !mappings.length) h.add('capabilities.stream.sourceMappings', 'must contain at least one registered mapping')
  else mappings.forEach((mapping, index) => {
    const item = h.obj(mapping, `capabilities.stream.sourceMappings[${index}]`)
    ;['localStreamSourceId', 'providerSourceId'].forEach(key => {
      const value = h.text(item[key], `capabilities.stream.sourceMappings[${index}].${key}`)
      if (value && (value.includes('://') || value.length > 256)) h.add(`capabilities.stream.sourceMappings[${index}].${key}`, 'must be an opaque bounded identifier')
    })
  })
  const features = h.obj(cap.features, 'capabilities.stream.features')
  ;['sessionQuery', 'eventQuery', 'stop', 'deduplication'].forEach(key => h.bool(features[key], `capabilities.stream.features.${key}`))
  if (features.sessionQuery !== true) h.add('capabilities.stream.features.sessionQuery', 'must be true for an enabled polling stream')
  if (features.eventQuery !== true) h.add('capabilities.stream.features.eventQuery', 'must be true for an enabled event stream')
  h.endpoint(cap.start, 'capabilities.stream.start', 'POST')
  if (features.sessionQuery) h.endpoint(cap.sessionQuery, 'capabilities.stream.sessionQuery', 'GET')
  if (features.eventQuery) h.endpoint(cap.eventQuery, 'capabilities.stream.eventQuery', 'GET')
  const stop = h.obj(cap.stopSemantics, 'capabilities.stream.stopSemantics')
  if (features.stop) {
    h.endpoint(cap.stop, 'capabilities.stream.stop', 'POST')
    h.text(stop.confirmedField, 'capabilities.stream.stopSemantics.confirmedField')
    if (stop.unconfirmedOutcome !== 'UNKNOWN') h.add('capabilities.stream.stopSemantics.unconfirmedOutcome', 'must be UNKNOWN')
    if (!h.bool(stop.onlyConfirmedStops, 'capabilities.stream.stopSemantics.onlyConfirmedStops')) h.add('capabilities.stream.stopSemantics.onlyConfirmedStops', 'must be true')
  } else if (stop.unsupportedOutcome !== 'UNSUPPORTED') h.add('capabilities.stream.stopSemantics.unsupportedOutcome', 'must be UNSUPPORTED')
  const limits = h.obj(cap.limits, 'capabilities.stream.limits')
  h.positive(limits.maxConcurrent, 'capabilities.stream.limits.maxConcurrent', 100)
  h.positive(limits.maxEventsPerPage, 'capabilities.stream.limits.maxEventsPerPage', 1000)
  h.positive(limits.maxSnapshotBytes, 'capabilities.stream.limits.maxSnapshotBytes', 104857600)
  h.texts(limits.snapshotMimeTypes, 'capabilities.stream.limits.snapshotMimeTypes', true)
  h.samples(cap.samples, 'capabilities.stream.samples', ['sourceList', 'start', 'session', 'events', 'emptyEvents', 'error', 'snapshot'])
  if (features.stop) h.samples(cap.samples, 'capabilities.stream.samples', ['stop'])
}

function scanSecrets(value, field, add) {
  if (typeof value === 'string') {
    if (forbiddenText.test(value)) add(field, 'must not contain RTSP URLs or inline authorization values')
    return
  }
  if (!value || typeof value !== 'object') return
  Object.entries(value).forEach(([key, child]) => {
    const next = field === '$' ? key : `${field}.${key}`
    if (forbiddenKey.test(key) && !allowedSecretReferenceKey.test(key)) {
      add(next, 'inline secret fields are forbidden; use a file reference')
    }
    scanSecrets(child, next, add)
  })
}

function validDate(value) { return typeof value === 'string' && !Number.isNaN(Date.parse(value)) && /T/.test(value) }

module.exports = { validateIntake }
