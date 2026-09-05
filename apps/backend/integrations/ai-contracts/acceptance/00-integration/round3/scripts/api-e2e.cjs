const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')
const assert = require('node:assert/strict')
const { root, work, evidence, sql } = require('./runtime.cjs')
const { request, login, jsonPost, save } = require('./http.cjs')
const prefix = 'round3-' + Date.now()
const control = value => fs.writeFileSync(path.join(work, 'control/mode'), value)
const jobs = {}
const digest = bytes => crypto.createHash('sha256').update(bytes).digest('hex')
const dispatches = id => fs.readFileSync(path.join(work, 'control/events.tsv'), 'utf8').split('\n').filter(l => l.startsWith('dispatch\t' + id + '\t')).length
async function terminal(id, token) {
  for (let n = 0; n < 60; n++) {
    const response = await request('/ai/v1/jobs/' + id, {}, token)
    assert.equal(response.status, 200)
    if (['SUCCEEDED', 'FAILED', 'UNKNOWN'].includes(response.body.result.state)) return response.body.result
    await new Promise(resolve => setTimeout(resolve, 250))
  }
  throw new Error('Job did not finish: ' + id)
}
async function upload(token, bytes, filename = 'input.png') {
  const form = new FormData(); form.append('file', new Blob([bytes], { type: 'image/png' }), filename)
  return request('/ai/v1/assets', { method: 'POST', body: form }, token)
}
async function main() {
  const tokens = {}
  for (const name of ['owner_a', 'owner_b', 'viewer', 'nomenu']) tokens[name] = await login(name)
  const source = fs.readFileSync(path.join(root, 'backend-github/integrations/ai-contracts/examples/input.png'))
  for (const url of ['/ai/v1/capabilities', '/ai/v1/jobs', '/ai/v1/assets/absent/content', '/tab/testAI/predict'])
    assert.equal((await request(url)).status, 401)
  for (const name of ['viewer', 'nomenu']) {
    assert.equal((await upload(tokens[name], source)).status, 403)
    for (const url of ['/ai/v1/infer', '/ai/v1/jobs']) assert.equal((await request(url, jsonPost({}), tokens[name])).status, 403)
  }
  const assets = {}
  for (const name of ['owner_a', 'owner_b']) {
    const response = await upload(tokens[name], source)
    assert.equal(response.status, 201); assets[name] = response.body.result
    assert.equal(assets[name].sha256, digest(source))
  }
  const body = { capabilityCode: 'image-detection.v1', inputAssetId: assets.owner_a.assetId,
    parameters: { threshold: 0.5, maxDetections: 10, annotate: true } }
  const submit = (name, payload = body, wait = 1500, token = tokens.owner_a) => request('/ai/v1/infer?waitMillis=' + wait,
    { ...jsonPost(payload), headers: { 'Content-Type': 'application/json', 'Idempotency-Key': prefix + '-' + name } }, token)
  control('normal')
  const immediate = await submit('immediate')
  assert.equal(immediate.status, 200); assert.equal(immediate.body.result.state, 'SUCCEEDED')
  jobs.immediate = immediate.body.result
  control('delay')
  const delayed = await submit('delayed', body, 0)
  assert.equal(delayed.status, 202)
  jobs.delayed = await terminal(delayed.body.result.requestId, tokens.owner_a)
  assert.equal(jobs.delayed.state, 'SUCCEEDED')
  control('normal')
  const duplicate = await submit('delayed', body, 0)
  assert.equal(duplicate.status, 200); assert.equal(duplicate.body.result.requestId, jobs.delayed.requestId)
  assert.equal(dispatches(jobs.delayed.requestId), 1)
  jobs.empty = (await submit('empty', { ...body, parameters: { ...body.parameters, threshold: 0.99 } })).body.result
  assert.equal(jobs.empty.state, 'SUCCEEDED'); assert.deepEqual(jobs.empty.result.data.detections, [])
  assert.deepEqual(jobs.empty.result.artifacts, [])
  for (const [scenario, state, code] of [['auth', 'FAILED', 'PROVIDER_AUTH'], ['unknown', 'UNKNOWN', 'RESULT_UNKNOWN'], ['truncate', 'FAILED', 'ARTIFACT_TRANSFER']]) {
    control(scenario)
    const accepted = await submit(scenario, body, 0)
    jobs[scenario] = await terminal(accepted.body.result.requestId, tokens.owner_a)
    assert.equal(jobs[scenario].state, state); assert.equal(jobs[scenario].error.errorCode, code)
    assert.equal((await request('/sys/user/getUserInfo', {}, tokens.owner_a)).body.success, true)
    assert.equal((await submit(scenario, body, 0)).body.result.requestId, jobs[scenario].requestId)
    assert.equal(dispatches(jobs[scenario].requestId), 1)
    assert.equal(Number(sql("SELECT COUNT(*) FROM ai_asset WHERE asset_id LIKE 'out_" + jobs[scenario].requestId + "_%'")), 0)
  }
  control('normal')
  const result = jobs.delayed.result.artifacts[0]
  const file = await request('/ai/v1/assets/' + result.assetId + '/content', {}, tokens.owner_a)
  assert.equal(file.status, 200); assert.equal(file.body.length, result.sizeBytes); assert.equal(digest(file.body), result.sha256)
  assert.equal(file.headers.get('x-content-type-options'), 'nosniff')
  for (const name of ['owner_b', 'viewer', 'nomenu']) {
    assert.equal((await request('/ai/v1/jobs/' + jobs.delayed.requestId, {}, tokens[name])).status, 404)
    assert.equal((await request('/ai/v1/assets/' + result.assetId + '/content', {}, tokens[name])).status, 404)
    assert.equal((await request('/ai/v1/assets/' + assets.owner_a.assetId + '/content', {}, tokens[name])).status, 404)
  }
  assert.equal((await submit('cross-input', body, 0, tokens.owner_b)).status, 404)
  jobs.ownerB = (await submit('owner-b', { ...body, inputAssetId: assets.owner_b.assetId }, 1500, tokens.owner_b)).body.result
  assert.equal(jobs.ownerB.state, 'SUCCEEDED')
  assert.equal((await request('/ai/v1/jobs/' + jobs.ownerB.requestId, {}, tokens.owner_a)).status, 404)
  const binding = sql("SELECT descriptor_json FROM ai_capability_binding WHERE capability_code='image-detection.v1'").trim()
  try {
    sql("UPDATE ai_capability_binding SET descriptor_json=JSON_SET(descriptor_json,'$.enabled',false)")
    assert.equal((await request('/ai/v1/capabilities', {}, tokens.owner_a)).body.result[0].available, false)
    assert.equal((await submit('disabled')).status, 409)
    assert.equal((await submit('delayed')).body.result.requestId, jobs.delayed.requestId)
    assert.equal((await request('/ai/v1/jobs/' + jobs.delayed.requestId, {}, tokens.owner_a)).status, 200)
    assert.equal((await request('/ai/v1/assets/' + result.assetId + '/content', {}, tokens.owner_a)).status, 200)
    sql("UPDATE ai_capability_binding SET descriptor_json=JSON_SET(descriptor_json,'$.enabled',true,'$.maxInputBytes',1)")
    assert.equal((await submit('over-limit')).status, 413)
  } finally { sql("UPDATE ai_capability_binding SET descriptor_json='" + binding.replaceAll("'", "''") + "'") }
  for (const url of ['/tab/tabAiHistory/addIdentify', '/tab/tabAiHistory/addIdentifyClose', '/tab/tabAiHistory/addAudio',
    '/video/tabVideoUtil/startVideoUtil', '/video/tabVideoUtil/stopVideoUtil', '/tab/tabAiSubscription/subInfo', '/tab/testAI/predict']) {
    assert.equal((await request(url, jsonPost({}), tokens.owner_a)).status, 409)
    assert.equal((await request(url, jsonPost({}), tokens.viewer)).status, 403)
    assert.equal((await request(url, jsonPost({}))).status, 401)
  }
  for (const url of ['/tab/tabAiHistory/list', '/tab/tabAiModel/list', '/tab/tabAiSubscription/list']) {
    const response = await request(url, {}, tokens.owner_a)
    assert.equal(response.status, 200); assert.equal(response.body.success, true)
  }
  for (const name of ['owner_a', 'owner_b']) {
    const history = (await request('/ai/v1/jobs?limit=100', {}, tokens[name])).body.result.items
    const other = name === 'owner_a' ? jobs.ownerB.requestId : jobs.delayed.requestId
    assert(!history.some(job => job.requestId === other))
  }
  for (const [name, job] of Object.entries(jobs)) {
    await request('/ai/v1/jobs/' + job.requestId, {}, name === 'ownerB' ? tokens.owner_b : tokens.owner_a)
    assert.equal(dispatches(job.requestId), 1)
  }
  const ids = Object.values(jobs).map(job => "'" + job.requestId + "'").join(',')
  save('api-e2e', { jobs: Object.fromEntries(Object.entries(jobs).map(([name, job]) => [name, job.requestId])),
    file: { assetId: result.assetId, bytes: file.body.length, sha256: digest(file.body) },
    database: { jobs: sql('SELECT request_id,owner_id,state,version FROM ai_job WHERE request_id IN (' + ids + ') ORDER BY created_at').trim().split('\n'),
      events: sql('SELECT request_id,version,state,occurred_at FROM ai_job_event WHERE request_id IN (' + ids + ') ORDER BY request_id,version').trim().split('\n') },
    dispatchCounts: Object.fromEntries(Object.entries(jobs).map(([name, job]) => [name, dispatches(job.requestId)])) })
  console.log('PASS: real login/Shiro API, ownership, durable jobs, interruption, histories, legacy guards and hashes')
}
main().catch(error => { control('normal'); console.error(error); process.exitCode = 1 })
