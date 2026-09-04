const { test } = require('node:test')
const assert = require('node:assert/strict')
const { loadSource } = require('./load-source.cjs')

test('independent navigation inherits exact AI management menus and is idempotent', () => {
  const { prepareAiMenus } = loadSource('modules/ai/legacy/navigation.js')
  const input = [{ component: 'layouts/RouteView', path: '/tab', children: [
    { component: 'tab/TabAiModelList', path: '/tab/TabAiModelList' }] }]
  const output = prepareAiMenus(input)
  assert.equal(output[1].path, '/ai'); assert.equal(output[1].children.length, 6)
  assert.deepEqual(Array.from(output[1].children.map(item => item.name)), ['AiInference', 'AiVideoInference', 'AiStreamStart', 'AiHistory', 'AiJobDetail', 'AiStreamSession'])
  assert.equal(output[1].children[4].hidden, true); assert.equal(output[1].children[5].hidden, true)
  assert.equal(JSON.stringify(prepareAiMenus(output)), JSON.stringify(output))
  assert.equal(input.length, 1)
  assert.equal(prepareAiMenus([{ component: 'tab/TabChatQaList' }]).some(m => m.path === '/ai'), false)
  assert.equal(prepareAiMenus([]).length, 0)
})

test('video and stream APIs use only frozen paths, bounded query values and exact request bodies', async () => {
  const calls = []
  const axios = config => { calls.push(config); return Promise.resolve({ success: true, result: config.data || { items: [] } }) }
  const jobs = loadSource('modules/ai/job/api.js', { '@/utils/request': { axios } })
  const streams = loadSource('modules/ai/stream/api.js', { '@/utils/request': { axios } })
  const video = { capabilityCode: 'video-file-analysis.v1', inputAssetId: 'asset_A', parameters: { threshold: 0.5 } }
  await jobs.submitVideoJob(video, 'key_12345678'); await jobs.cancelJob('job_A')
  const start = { capabilityCode: 'video-stream-analysis.v1', streamSourceId: 'source_A', parameters: { maxEventsPerPoll: 50 } }
  await streams.startStreamSession(start, 'key_87654321'); await streams.getStreamEvents('session_A', { cursor: 'cursorA', limit: 999 })
  await streams.stopStreamSession('session_A')
  assert.equal(calls[0].url, '/ai/v1/video-jobs'); assert.equal(calls[0].data, video)
  assert.equal(calls[1].url, '/ai/v1/jobs/job_A/cancel')
  assert.equal(calls[2].url, '/ai/v1/stream-sessions'); assert.equal(calls[2].data, start)
  assert.deepEqual(JSON.parse(JSON.stringify(calls[3].params)), { cursor: 'cursorA', limit: 200 })
  assert.equal(calls[4].url, '/ai/v1/stream-sessions/session_A/stop')
})

test('event screenshots require a non-empty authorized image response', async () => {
  let response = new Blob(['x'], { type: 'image/jpeg' })
  const { downloadSnapshotAsset } = loadSource('modules/ai/asset/api.js', { '@/utils/request': { axios: () => Promise.resolve(response) } })
  assert.equal(await downloadSnapshotAsset('snapshot_A'), response)
  response = new Blob([], { type: 'image/png' }); await assert.rejects(downloadSnapshotAsset('snapshot_A'), /为空/)
  response = new Blob(['x'], { type: 'video/mp4' }); await assert.rejects(downloadSnapshotAsset('snapshot_A'), /格式/)
})

test('downloads preserve authentication wrapper, validate bytes, and decode JSON failures', async () => {
  let response = new Blob(['abc'], { type: 'image/png' })
  const { downloadAsset } = loadSource('modules/ai/asset/api.js', {
    '@/utils/request': { axios(config) {
      assert.equal(config.url, '/ai/v1/assets/asset_A/content')
      assert.equal(config.responseType, 'blob')
      return response instanceof Error ? Promise.reject(response) : Promise.resolve(response)
    } }
  })
  const asset = { assetId: 'asset_A', mediaType: 'image/png', sizeBytes: 3 }
  assert.equal(await downloadAsset(asset), response)
  response = new Blob(['ab'], { type: 'image/png' }); await assert.rejects(downloadAsset(asset), /不完整/)
  response = new Blob(['abc'], { type: 'text/html' }); await assert.rejects(downloadAsset(asset), /格式/)
  response = new Error('410')
  response.response = { data: new Blob([JSON.stringify({ result: { errorCode: 'ASSET_EXPIRED' } })], { type: 'application/json' }) }
  await assert.rejects(downloadAsset(asset), error => error.detail.errorCode === 'ASSET_EXPIRED')
  assert.throws(() => downloadAsset({ assetId: '../../outside' }), /编号无效/)
})
