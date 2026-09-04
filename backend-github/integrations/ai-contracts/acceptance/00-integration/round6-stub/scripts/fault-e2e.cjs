const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { root, run } = require('../../round3/scripts/runtime.cjs')
const { request, login, jsonPost } = require('../../round3/scripts/http.cjs')

const evidence = path.resolve(__dirname, '..')
const backend = 'wgai-ri-00-integration-backend-1'
const frontend = 'wgai-ri-00-integration-frontend-1'
const stub = 'wgai-ri-00-integration-remote-ai-stub-1'
const prefix = 'round6-stub-' + Date.now()
const sleep = millis => new Promise(resolve => setTimeout(resolve, millis))

async function healthy(container, attempts = 60) {
  for (let attempt = 0; attempt < attempts; attempt++) {
    const state = run('docker', ['inspect', '-f', '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}', container]).trim()
    if (state === 'healthy') return
    if (state === 'unhealthy' || state === 'exited') throw new Error(container + ' entered ' + state)
    await sleep(1000)
  }
  throw new Error(container + ' did not become healthy')
}

function stubCall(pathname, options = {}) {
  const source = "const path=process.argv[1],method=process.argv[2],body=process.argv[3];fetch('http://127.0.0.1:18080'+path,{method,headers:{Authorization:'Bearer '+process.env.WGAI_STUB_TOKEN,'Content-Type':'application/json'},body:body||undefined}).then(async r=>{const text=await r.text();if(!r.ok)throw new Error(r.status+' '+text);console.log(text)})"
  return JSON.parse(run('docker', ['exec', stub, 'node', '-e', source, pathname,
    options.method || 'GET', options.body ? JSON.stringify(options.body) : '']))
}

async function setScenario(value, reset = true) {
  const response = stubCall('/__stub/scenario', { method: 'POST', body: { scenario: value } })
  assert.equal(response.scenario, value)
  if (reset) stubCall('/__stub/reset', { method: 'POST' })
}

async function ensureStubRunning() {
  const status = run('docker', ['inspect', '-f', '{{.State.Status}}', stub]).trim()
  if (status !== 'running') run('docker', ['start', stub])
  await healthy(stub)
}

function stubRequests() {
  const source = "fetch('http://127.0.0.1:18080/__stub/requests',{headers:{Authorization:'Bearer '+process.env.WGAI_STUB_TOKEN}}).then(r=>r.json()).then(v=>console.log(JSON.stringify(v.items)))"
  return JSON.parse(run('docker', ['exec', stub, 'node', '-e', source]))
}

async function upload(token, bytes, mediaType, fileName) {
  const form = new FormData()
  form.append('file', new Blob([bytes], { type: mediaType }), fileName)
  const response = await request('/ai/v1/assets', { method: 'POST', body: form }, token)
  assert.equal(response.status, 201)
  return response.body.result
}

async function terminalJob(requestId, token) {
  for (let attempt = 0; attempt < 100; attempt++) {
    const response = await request('/ai/v1/jobs/' + requestId, {}, token)
    assert.equal(response.status, 200)
    if (['SUCCEEDED', 'FAILED', 'UNKNOWN', 'CANCELLED'].includes(response.body.result.state)) return response.body.result
    await sleep(200)
  }
  throw new Error('Job did not terminate: ' + requestId)
}

async function sessionState(sessionId, token, expected) {
  for (let attempt = 0; attempt < 100; attempt++) {
    const response = await request('/ai/v1/stream-sessions/' + sessionId, {}, token)
    assert.equal(response.status, 200)
    if (response.body.result.state === expected) return response.body.result
    await sleep(200)
  }
  throw new Error('Stream did not reach ' + expected + ': ' + sessionId)
}

async function imageRequest(token, suffix, waitMillis = 0) {
  const bytes = fs.readFileSync(path.join(root, 'backend-github/integrations/ai-contracts/examples/input.png'))
  const asset = await upload(token, bytes, 'image/png', suffix + '.png')
  const body = { capabilityCode: 'image-detection.v1', inputAssetId: asset.assetId,
    parameters: { threshold: 0.5, maxDetections: 10, annotate: true } }
  const key = prefix + '-' + suffix
  const response = await request('/ai/v1/infer?waitMillis=' + waitMillis, {
    ...jsonPost(body), headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key }
  }, token)
  assert([200, 202].includes(response.status))
  const requestId = response.body.result.requestId
  return { body, key, requestId,
    job: response.status === 200 ? response.body.result : await terminalJob(requestId, token) }
}

async function startStream(token, suffix) {
  const body = { capabilityCode: 'video-stream-analysis.v1', streamSourceId: 'stub-source-01',
    parameters: { maxEventsPerPoll: 50, pollIntervalMillis: 500, includeSnapshots: true } }
  const response = await request('/ai/v1/stream-sessions', {
    ...jsonPost(body), headers: { 'Content-Type': 'application/json', 'Idempotency-Key': prefix + '-' + suffix }
  }, token)
  assert([200, 202].includes(response.status))
  const sessionId = response.body.result.sessionId
  await sessionState(sessionId, token, 'RUNNING')
  return sessionId
}

async function main() {
  const token = await login('owner_a')
  const results = { status: 'PASS', simulated: true, realProviderValidated: false, prefix }

  await setScenario('response-lost')
  const lost = await imageRequest(token, 'response-lost')
  assert.equal(lost.job.state, 'UNKNOWN')
  assert.equal(lost.job.error.errorCode, 'PROVIDER_OFFLINE')
  let calls = stubRequests().filter(item => item.method === 'POST' && item.path === '/infer' && item.requestId === lost.requestId)
  assert.equal(calls.length, 1)
  const duplicate = await request('/ai/v1/infer?waitMillis=0', {
    ...jsonPost(lost.body), headers: { 'Content-Type': 'application/json', 'Idempotency-Key': lost.key }
  }, token)
  assert.equal(duplicate.body.result.requestId, lost.requestId)
  await sleep(500)
  calls = stubRequests().filter(item => item.method === 'POST' && item.path === '/infer' && item.requestId === lost.requestId)
  assert.equal(calls.length, 1)
  results.responseLost = { requestId: lost.requestId, state: lost.job.state,
    errorCode: lost.job.error.errorCode, providerPosts: calls.length }

  await setScenario('empty')
  const empty = await imageRequest(token, 'empty', 1500)
  assert.equal(empty.job.state, 'SUCCEEDED')
  assert.deepEqual(empty.job.result.data.detections, [])
  assert.deepEqual(empty.job.result.artifacts, [])
  results.validEmpty = { requestId: empty.requestId, state: empty.job.state, detections: 0, artifacts: 0 }

  await setScenario('artifact-interrupted')
  const interrupted = await imageRequest(token, 'artifact-interrupted')
  assert.equal(interrupted.job.state, 'FAILED')
  assert.equal(interrupted.job.error.errorCode, 'ARTIFACT_TRANSFER')
  const interruptedRequests = stubRequests()
  assert.equal(interruptedRequests.filter(item => item.method === 'POST' && item.path === '/infer').length, 1)
  assert.equal(interruptedRequests.filter(item => item.method === 'GET' && item.path.startsWith('/artifacts/')).length, 3)
  results.artifactInterrupted = { requestId: interrupted.requestId, state: interrupted.job.state,
    errorCode: interrupted.job.error.errorCode, inferencePosts: 1, artifactGetAttempts: 3 }

  await setScenario('duplicate-events')
  const duplicateSession = await startStream(token, 'duplicate-events')
  let duplicateEvents
  for (let attempt = 0; attempt < 50; attempt++) {
    duplicateEvents = await request('/ai/v1/stream-sessions/' + duplicateSession + '/events?limit=50', {}, token)
    if (duplicateEvents.body.result.items.length) break
    await sleep(200)
  }
  assert.equal(duplicateEvents.body.result.items.length, 1)
  const stopped = await request('/ai/v1/stream-sessions/' + duplicateSession + '/stop', { method: 'POST' }, token)
  assert([200, 202].includes(stopped.status))
  await sessionState(duplicateSession, token, 'STOPPED')
  results.duplicateEvents = { sessionId: duplicateSession, persisted: 1 }

  await setScenario('success')
  const uncertainSession = await startStream(token, 'stop-unknown')
  await setScenario('stop-unknown', false)
  const stopUnknown = await request('/ai/v1/stream-sessions/' + uncertainSession + '/stop', { method: 'POST' }, token)
  assert([200, 202].includes(stopUnknown.status))
  const unknownSession = await sessionState(uncertainSession, token, 'UNKNOWN')
  const stopCalls = stubRequests().filter(item => item.method === 'POST' && item.path.endsWith('/stop'))
  assert.equal(stopCalls.length, 1)
  const secondStop = await request('/ai/v1/stream-sessions/' + uncertainSession + '/stop', { method: 'POST' }, token)
  assert.equal(secondStop.status, 409)
  assert.equal(stubRequests().filter(item => item.method === 'POST' && item.path.endsWith('/stop')).length, 1)
  results.stopUnknown = { sessionId: uncertainSession, state: unknownSession.state,
    reason: unknownSession.unknownReason, providerStopPosts: 1, repeatStatus: secondStop.status }

  await setScenario('success')
  const durable = await imageRequest(token, 'offline-history', 1500)
  assert.equal(durable.job.state, 'SUCCEEDED')
  const artifact = durable.job.result.artifacts[0]
  run('docker', ['stop', stub])
  const offlineHistory = await request('/ai/v1/jobs?limit=100', {}, token)
  const offlineDownload = await request('/ai/v1/assets/' + artifact.assetId + '/content', {}, token)
  assert.equal(offlineHistory.status, 200)
  assert(offlineHistory.body.result.items.some(item => item.requestId === durable.requestId))
  assert.equal(offlineDownload.status, 200)
  assert.equal(offlineDownload.body.length, artifact.sizeBytes)
  results.providerOffline = { requestId: durable.requestId, historyStatus: offlineHistory.status,
    downloadStatus: offlineDownload.status, downloadedBytes: offlineDownload.body.length }

  const metrics = {}
  for (const name of ['wgai.ai.queue.size', 'wgai.ai.inflight.size', 'wgai.ai.operation.duration',
    'wgai.ai.errors', 'wgai.ai.stream.events']) {
    const response = await request('/actuator/metrics/' + name, {}, token)
    assert.equal(response.status, 200)
    assert.equal(response.body.name, name)
    metrics[name] = response.body.measurements.length
  }
  results.metrics = metrics

  await ensureStubRunning()
  await setScenario('success')
  run('docker', ['restart', backend])
  await healthy(backend)
  run('docker', ['restart', frontend])
  const afterRestart = await request('/ai/v1/jobs/' + durable.requestId, {}, token)
  assert.equal(afterRestart.status, 200)
  assert.equal(afterRestart.body.result.state, 'SUCCEEDED')
  results.backendRestart = { existingSessionTokenAccepted: true, requestId: durable.requestId,
    state: afterRestart.body.result.state }

  fs.mkdirSync(evidence, { recursive: true })
  fs.writeFileSync(path.join(evidence, 'fault-e2e.actual.json'), JSON.stringify(results, null, 2) + '\n')
  console.log(JSON.stringify(results, null, 2))
}

;(async () => {
  try { await main() } finally {
    await ensureStubRunning()
    await setScenario('success')
  }
})().catch(error => { console.error(error.stack || error); process.exitCode = 1 })
