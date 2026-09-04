const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')
const assert = require('node:assert/strict')
const { root, sql } = require('../../round3/scripts/runtime.cjs')
const { request, login, jsonPost, receipts } = require('../../round3/scripts/http.cjs')

const evidence = path.resolve(__dirname, '..')
const prefix = 'round5-stub-' + Date.now()
const sleep = millis => new Promise(resolve => setTimeout(resolve, millis))
const digest = bytes => crypto.createHash('sha256').update(bytes).digest('hex')

async function upload(token, bytes, mediaType, fileName) {
  const form = new FormData()
  form.append('file', new Blob([bytes], { type: mediaType }), fileName)
  return request('/ai/v1/assets', { method: 'POST', body: form }, token)
}

async function terminalJob(requestId, token) {
  for (let attempt = 0; attempt < 80; attempt++) {
    const response = await request('/ai/v1/jobs/' + requestId, {}, token)
    assert.equal(response.status, 200)
    if (['SUCCEEDED', 'FAILED', 'UNKNOWN', 'CANCELLED'].includes(response.body.result.state)) {
      return response.body.result
    }
    await sleep(250)
  }
  throw new Error('Job did not reach a terminal state: ' + requestId)
}

async function sessionState(sessionId, token, expected) {
  for (let attempt = 0; attempt < 80; attempt++) {
    const response = await request('/ai/v1/stream-sessions/' + sessionId, {}, token)
    assert.equal(response.status, 200)
    if (response.body.result.state === expected) return response.body.result
    if (['FAILED', 'UNKNOWN'].includes(response.body.result.state)) {
      throw new Error('Stream entered ' + response.body.result.state)
    }
    await sleep(250)
  }
  throw new Error('Stream did not reach ' + expected + ': ' + sessionId)
}

async function main() {
  const tokens = {}
  for (const name of ['owner_a', 'owner_b', 'viewer', 'nomenu']) tokens[name] = await login(name)

  assert.equal((await request('/ai/v1/capabilities')).status, 401)
  const capabilities = await request('/ai/v1/capabilities', {}, tokens.owner_a)
  assert.equal(capabilities.status, 200)
  assert.deepEqual(capabilities.body.result.map(item => item.code), [
    'image-detection.v1', 'video-file-analysis.v1', 'video-stream-analysis.v1'
  ])
  assert(capabilities.body.result.every(item => item.available && item.simulated))

  const imageBytes = fs.readFileSync(path.join(root, 'backend-github/integrations/ai-contracts/examples/input.png'))
  const imageUpload = await upload(tokens.owner_a, imageBytes, 'image/png', 'synthetic-input.png')
  assert.equal(imageUpload.status, 201)
  const imageRequest = {
    capabilityCode: 'image-detection.v1',
    inputAssetId: imageUpload.body.result.assetId,
    parameters: { threshold: 0.5, maxDetections: 10, annotate: true }
  }
  const imageSubmit = await request('/ai/v1/infer?waitMillis=1500', {
    ...jsonPost(imageRequest), headers: { 'Content-Type': 'application/json', 'Idempotency-Key': prefix + '-image' }
  }, tokens.owner_a)
  assert([200, 202].includes(imageSubmit.status))
  const imageJob = imageSubmit.status === 200 ? imageSubmit.body.result
    : await terminalJob(imageSubmit.body.result.requestId, tokens.owner_a)
  assert.equal(imageJob.state, 'SUCCEEDED')
  assert.equal(imageJob.simulated, true)
  assert.equal(imageJob.result.simulated, true)
  assert(imageJob.result.data.detections.length > 0)

  // The stub needs only a deterministic ISO-BMFF/H.264 boundary fixture; it is not presented as playable footage.
  const videoBytes = Buffer.from('000000186674797069736f6d0000020069736f6d69736f3261766331', 'hex')
  const videoUpload = await upload(tokens.owner_a, videoBytes, 'video/mp4', 'synthetic-demo.mp4')
  assert.equal(videoUpload.status, 201)
  const videoRequest = {
    capabilityCode: 'video-file-analysis.v1',
    inputAssetId: videoUpload.body.result.assetId,
    parameters: { threshold: 0.5, sampleIntervalMillis: 1000, maxEvents: 20, includeSnapshots: true, annotate: false }
  }
  const videoSubmit = await request('/ai/v1/video-jobs', {
    ...jsonPost(videoRequest), headers: { 'Content-Type': 'application/json', 'Idempotency-Key': prefix + '-video' }
  }, tokens.owner_a)
  assert([200, 202].includes(videoSubmit.status))
  const videoJob = videoSubmit.status === 200 ? videoSubmit.body.result
    : await terminalJob(videoSubmit.body.result.requestId, tokens.owner_a)
  assert.equal(videoJob.state, 'SUCCEEDED')
  assert.equal(videoJob.simulated, true)
  assert.equal(videoJob.videoResult.simulated, true)
  assert(videoJob.videoResult.events.length > 0)
  assert(videoJob.videoResult.snapshots.length > 0)

  const downloaded = []
  for (const artifact of [...imageJob.result.artifacts, ...videoJob.videoResult.snapshots]) {
    const response = await request('/ai/v1/assets/' + artifact.assetId + '/content', {}, tokens.owner_a)
    assert.equal(response.status, 200)
    assert.equal(response.body.length, artifact.sizeBytes)
    assert.equal(digest(response.body), artifact.sha256)
    downloaded.push({ assetId: artifact.assetId, sizeBytes: artifact.sizeBytes, sha256: artifact.sha256 })
  }

  const sources = await request('/ai/v1/stream-sources', {}, tokens.owner_a)
  assert.equal(sources.status, 200)
  assert(sources.body.result.some(item => item.streamSourceId === 'stub-source-01' && item.available))
  const streamRequest = {
    capabilityCode: 'video-stream-analysis.v1', streamSourceId: 'stub-source-01',
    parameters: { maxEventsPerPoll: 50, pollIntervalMillis: 500, includeSnapshots: true }
  }
  const streamSubmit = await request('/ai/v1/stream-sessions', {
    ...jsonPost(streamRequest), headers: { 'Content-Type': 'application/json', 'Idempotency-Key': prefix + '-stream' }
  }, tokens.owner_a)
  assert([200, 202].includes(streamSubmit.status))
  const streamId = streamSubmit.body.result.sessionId
  const running = await sessionState(streamId, tokens.owner_a, 'RUNNING')

  const duplicate = await request('/ai/v1/stream-sessions', {
    ...jsonPost(streamRequest), headers: { 'Content-Type': 'application/json', 'Idempotency-Key': prefix + '-stream' }
  }, tokens.owner_a)
  assert.equal(duplicate.body.result.sessionId, streamId)

  let eventPage
  for (let attempt = 0; attempt < 40; attempt++) {
    eventPage = await request('/ai/v1/stream-sessions/' + streamId + '/events?limit=50', {}, tokens.owner_a)
    assert.equal(eventPage.status, 200)
    if (eventPage.body.result.items.length) break
    await sleep(250)
  }
  assert(eventPage.body.result.items.length > 0)
  const streamSnapshotId = eventPage.body.result.items[0].snapshotAssetId
  assert(streamSnapshotId)
  const streamSnapshot = await request('/ai/v1/assets/' + streamSnapshotId + '/content', {}, tokens.owner_a)
  assert.equal(streamSnapshot.status, 200)

  const stop = await request('/ai/v1/stream-sessions/' + streamId + '/stop', { method: 'POST' }, tokens.owner_a)
  assert([200, 202].includes(stop.status))
  const stopped = await sessionState(streamId, tokens.owner_a, 'STOPPED')

  const history = await request('/ai/v1/jobs?limit=100', {}, tokens.owner_a)
  assert.equal(history.status, 200)
  assert(history.body.result.items.some(item => item.requestId === imageJob.requestId))
  assert(history.body.result.items.some(item => item.requestId === videoJob.requestId))
  assert.equal((await request('/ai/v1/jobs/' + imageJob.requestId, {}, tokens.owner_b)).status, 404)
  assert.equal((await request('/ai/v1/jobs/' + videoJob.requestId, {}, tokens.viewer)).status, 404)
  assert.equal((await request('/ai/v1/stream-sessions/' + streamId, {}, tokens.owner_b)).status, 404)

  const rows = sql("SELECT request_id,state FROM ai_job WHERE request_id IN ('" +
    imageJob.requestId + "','" + videoJob.requestId + "') ORDER BY request_id").trim().split('\n')
  assert.equal(rows.length, 2)
  assert(rows.every(row => row.endsWith('\tSUCCEEDED')))

  const result = {
    status: 'PASS', simulated: true, realProviderValidated: false, prefix,
    capabilities: capabilities.body.result.map(item => ({ code: item.code, available: item.available, simulated: item.simulated })),
    image: { requestId: imageJob.requestId, detections: imageJob.result.data.detections.length },
    video: { requestId: videoJob.requestId, events: videoJob.videoResult.events.length,
      snapshots: videoJob.videoResult.snapshots.length },
    stream: { sessionId: streamId, stateBeforeStop: running.state, stateAfterStop: stopped.state,
      events: eventPage.body.result.items.length, snapshotSha256: digest(streamSnapshot.body) },
    downloaded, historyItems: history.body.result.items.length, databaseRows: rows,
    requests: receipts.map(item => ({ method: item.method, url: item.url, status: item.status }))
  }
  fs.writeFileSync(path.join(evidence, 'app-e2e.actual.json'), JSON.stringify(result, null, 2) + '\n')
  console.log(JSON.stringify(result, null, 2))
}

main().catch(error => { console.error(error.stack || error); process.exitCode = 1 })
