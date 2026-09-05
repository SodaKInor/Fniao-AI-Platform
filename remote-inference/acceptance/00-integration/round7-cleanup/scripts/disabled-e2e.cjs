const fs = require('node:fs')
const path = require('node:path')
const assert = require('node:assert/strict')
const { root, sql } = require('../../round3/scripts/runtime.cjs')
const { request, login, jsonPost } = require('../../round3/scripts/http.cjs')

const evidence = path.resolve(__dirname, '..')
const accepted = JSON.parse(fs.readFileSync(path.join(evidence, 'runtime.json')))
const output = path.join(evidence, 'disabled.json')

async function main() {
  assert.equal(process.cwd(), root)
  const before = Number(sql('SELECT COUNT(*) FROM ai_job'))
  const token = await login('owner_a')
  const capabilities = await request('/ai/v1/capabilities', {}, token)
  assert.equal(capabilities.status, 200)
  assert.ok(capabilities.body.result.length >= 3)
  assert.ok(capabilities.body.result.every(item => item.available === false))

  const jobId = accepted.image.requestId
  const detail = await request('/ai/v1/jobs/' + jobId, {}, token)
  assert.equal(detail.status, 200)
  assert.equal(detail.body.result.state, 'SUCCEEDED')
  const history = await request('/ai/v1/jobs?limit=100', {}, token)
  assert.equal(history.status, 200)
  assert.ok(history.body.result.items.some(item => item.requestId === jobId))

  const download = await request('/ai/v1/assets/' + accepted.downloaded[0].assetId + '/content', {}, token)
  assert.equal(download.status, 200)
  assert.equal(download.body.length, accepted.downloaded[0].sizeBytes)

  const body = {
    capabilityCode: detail.body.result.capabilityCode,
    inputAssetId: detail.body.result.inputAssetId,
    parameters: detail.body.result.parameters
  }
  const rejected = await request('/ai/v1/infer', {
    ...jsonPost(body),
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': 'round7-disabled-' + Date.now()
    }
  }, token)
  assert.equal(rejected.status, 409)
  assert.equal(Number(sql('SELECT COUNT(*) FROM ai_job')), before)

  const report = {
    status: 'PASS',
    mode: 'disabled',
    realProviderValidated: false,
    capabilitiesAvailable: capabilities.body.result.filter(item => item.available).length,
    historicalJobId: jobId,
    historicalState: detail.body.result.state,
    historicalAssetSha256: accepted.downloaded[0].sha256,
    newSubmissionStatus: rejected.status,
    jobCountBefore: before,
    jobCountAfter: Number(sql('SELECT COUNT(*) FROM ai_job')),
    note: 'Disabled mode fails closed while authenticated history and stored results remain readable.'
  }
  fs.writeFileSync(output, JSON.stringify(report, null, 2) + '\n')
  console.log(JSON.stringify(report, null, 2))
}

main().catch(error => { console.error(error.stack || error); process.exitCode = 1 })
