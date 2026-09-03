const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { root, frontend } = require('./load-source.cjs')
const Ajv = require(path.join(frontend, 'node_modules/ajv'))
const { startServers } = require('../mock/server.cjs')
const { input } = require('../mock/fixtures.cjs')
const contracts = path.join(root, 'backend-github/integrations/ai-contracts')
const document = JSON.parse(fs.readFileSync(path.join(contracts, 'v1/business.openapi.json')))

function expand(value) {
  if (Array.isArray(value)) return value.map(expand)
  if (!value || typeof value !== 'object') return value
  if (value.$ref) return expand(document.components.schemas[value.$ref.split('/').pop()])
  const copy = Object.fromEntries(Object.entries(value).filter(([k]) => k !== 'nullable').map(([k, v]) => [k, expand(v)]))
  if (value.nullable) copy.type = [value.type, 'null']
  return copy
}
const ajv = new Ajv({ allErrors: true, formats: { int64: { type: 'number', validate: Number.isSafeInteger } } })
const validators = Object.fromEntries(['CapabilityListResponse', 'AssetResponse', 'JobResponse', 'JobPageResponse', 'ErrorResponse']
  .map(name => [name, ajv.compile(expand(document.components.schemas[name]))]))

test('live mock HTTP responses obey frozen v1, including upload, idempotency, faults and cursor history', async t => {
  const servers = await startServers({ apiPort: 0, frontendPort: 0 }); t.after(() => servers.close())
  const base = 'http://127.0.0.1:' + servers.api.address().port
  async function call(route, schema, options = {}) {
    const response = await fetch(base + '/jeecg-boot/ai/v1' + route, {
      ...options, headers: { 'X-Access-Token': 'mock-demo', ...options.headers } })
    const value = await response.json()
    assert.equal(validators[schema](value), true, JSON.stringify(validators[schema].errors))
    assert.equal(value.code, response.status)
    return value
  }
  await call('/capabilities', 'CapabilityListResponse')
  const form = new FormData(); form.append('file', new Blob([input], { type: 'image/png' }), 'input.png')
  const uploaded = await call('/assets', 'AssetResponse', { method: 'POST', body: form })
  const request = { capabilityCode: 'image-detection.v1', inputAssetId: uploaded.result.assetId,
    parameters: { threshold: 0.5, maxDetections: 10, annotate: true } }
  const options = { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': 'contract_1234' }, body: JSON.stringify(request) }
  const accepted = await call('/infer?waitMillis=1500', 'JobResponse', options)
  assert.equal(accepted.code, 202)
  const repeated = await call('/infer?waitMillis=1500', 'JobResponse', options)
  assert.equal(repeated.result.requestId, accepted.result.requestId); assert.equal(servers.state.sequence, 1)
  await call('/infer', 'ErrorResponse', { ...options, body: JSON.stringify({ ...request, parameters: { ...request.parameters, threshold: 0.9 } }) })
  const record = servers.state.jobs.get(accepted.result.requestId); record.finishAt = 1
  const success = await call('/jobs/' + record.job.requestId, 'JobResponse'); assert.equal(success.result.state, 'SUCCEEDED')
  const contentPath = '/assets/' + success.result.result.artifacts[0].assetId + '/content'
  const binary = await fetch(base + '/jeecg-boot/ai/v1' + contentPath, { headers: { 'X-Access-Token': 'mock-demo' } })
  assert.equal((await binary.arrayBuffer()).byteLength, success.result.result.artifacts[0].sizeBytes)
  servers.state.config.download = 'expired'; assert.equal((await call(contentPath, 'ErrorResponse')).code, 410)
  servers.state.config.download = 'normal'
  await call(contentPath, 'ErrorResponse', { headers: { 'X-Access-Token': 'mock-other' } })
  const history = await call('/jobs?limit=2', 'JobPageResponse'); assert.ok(history.result.nextCursor)
  const next = await call('/jobs?limit=2&cursor=' + encodeURIComponent(history.result.nextCursor), 'JobPageResponse')
  assert.ok(next.result.items.every(j => !history.result.items.some(old => old.requestId === j.requestId)))
  for (const scenario of ['empty', 'failed', 'unknown', 'immediate']) {
    servers.state.config.scenario = scenario
    const result = await call('/infer', 'JobResponse', { ...options,
      headers: { ...options.headers, 'Idempotency-Key': 'contract_' + scenario } })
    const item = servers.state.jobs.get(result.result.requestId); item.finishAt = 1
    const finished = await call('/jobs/' + result.result.requestId, 'JobResponse')
    if (scenario === 'empty') assert.equal(finished.result.result.data.detections.length, 0)
    if (scenario === 'failed') assert.equal(finished.result.error.errorCode, 'PROVIDER_AUTH')
    if (scenario === 'unknown') assert.equal(finished.result.state, 'UNKNOWN')
    if (scenario === 'immediate') assert.equal(result.code, 200)
  }
  await call('/jobs', 'JobPageResponse')
  servers.state.config.available = false
  assert.equal((await call('/capabilities', 'CapabilityListResponse')).result[0].available, false)
})
