const fs = require('node:fs')
const path = require('node:path')
const assert = require('node:assert/strict')
const crypto = require('node:crypto')
const { work, evidence, run, sql } = require('./runtime.cjs')
const { request, login, jsonPost, save } = require('./http.cjs')
const accepted = JSON.parse(fs.readFileSync(path.join(evidence, 'api-e2e.json')))
const control = value => fs.writeFileSync(path.join(work, 'control/download-mode'), value)
async function main() {
  const token = await login('owner_a')
  const job = (await request('/ai/v1/jobs/' + accepted.jobs.delayed, {}, token)).body.result
  const body = { capabilityCode: job.capabilityCode, inputAssetId: job.inputAssetId, parameters: job.parameters }
  const key = 'round3-concurrent-' + Date.now()
  const options = payload => ({ ...jsonPost(payload), headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key } })
  const responses = await Promise.all(Array.from({ length: 6 }, () => request('/ai/v1/infer?waitMillis=0', options(body), token)))
  assert(responses.every(r => r.status === 200 || r.status === 202))
  const requestId = responses[0].body.result.requestId
  assert(responses.every(r => r.body.result.requestId === requestId))
  assert.equal((await request('/ai/v1/infer', options({ ...body, parameters: { ...body.parameters, threshold: 0.6 } }), token)).status, 409)
  for (let n = 0; n < 30; n++) {
    const state = (await request('/ai/v1/jobs/' + requestId, {}, token)).body.result.state
    if (state === 'SUCCEEDED') break
    await new Promise(resolve => setTimeout(resolve, 100))
  }
  const dispatches = fs.readFileSync(path.join(work, 'control/events.tsv'), 'utf8').split('\n').filter(l => l.startsWith('dispatch\t' + requestId + '\t')).length
  assert.equal(dispatches, 1)
  const url = '/ai/v1/assets/' + accepted.file.assetId + '/content'
  let interrupted = false
  try {
    control('truncate')
    try { await request(url, {}, token) } catch (error) { interrupted = true }
    assert(interrupted, 'Short binary response must fail the actual HTTP client')
    control('json')
    const failure = await request(url, {}, token)
    assert.equal(failure.status, 404); assert(failure.headers.get('content-type').includes('json'))
  } finally { control('normal') }
  const restored = await request(url, {}, token)
  assert.equal(crypto.createHash('sha256').update(restored.body).digest('hex'), accepted.file.sha256)
  assert.equal(run('docker', ['exec', 'wgai-ri-00-integration-backend-1', 'find', '/data/ai-private', '-name', 'partial-*', '-type', 'f']).trim(), '')
  const storageKey = sql("SELECT storage_key FROM ai_asset WHERE asset_id='" + accepted.file.assetId + "'").trim()
  assert(/^[a-f0-9]{32}\.bin$/.test(storageKey))
  const publicAttempt = await request('/sys/common/static/' + storageKey)
  assert.notEqual(crypto.createHash('sha256').update(Buffer.isBuffer(publicAttempt.body) ? publicAttempt.body : JSON.stringify(publicAttempt.body)).digest('hex'), accepted.file.sha256)
  const privateHash = run('docker', ['exec', 'wgai-ri-00-integration-backend-1', 'sha256sum', '/data/ai-private/' + storageKey]).split(' ')[0]
  assert.equal(privateHash, accepted.file.sha256)
  const permissions = run('docker', ['exec', 'wgai-ri-00-integration-backend-1', 'stat', '-c', '%a', '/data/ai-private', '/data/ai-private/' + storageKey]).trim().split('\n')
  assert.deepEqual(permissions, ['700', '600'])
  for (const url of ['/ai/v1/assets', '/ai/v1/infer', '/ai/v1/jobs']) assert.equal((await request(url, jsonPost({}))).status, 401)
  save('safety-e2e', { concurrency: { attempts: 6, requestId, dispatches, conflictRejected: true },
    download: { truncatedHttpRejected: interrupted, jsonNotImage: true, restoredSha256: privateHash },
    privateStorage: { partialFiles: 0, permissions, anonymousStaticDoesNotExposeFile: true } })
  console.log('PASS: concurrent idempotency, binary/JSON interruption, private storage and anonymous writes')
}
main().catch(error => { control('normal'); console.error(error); process.exitCode = 1 })
