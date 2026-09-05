const { read, clone, now, asset, output, envelope, errorResult } = require('./fixtures.cjs')
const { body, json, fail } = require('./http.cjs')

function validate(request) {
  const p = request.parameters || {}
  return request.capabilityCode === 'video-file-analysis.v1' &&
    Object.keys(request).every(k => ['capabilityCode', 'inputAssetId', 'parameters', 'retryOfRequestId'].includes(k)) &&
    Object.keys(p).length === 5 && Number.isFinite(p.threshold) && p.threshold >= 0 && p.threshold <= 1 &&
    Number.isInteger(p.sampleIntervalMillis) && p.sampleIntervalMillis >= 100 && p.sampleIntervalMillis <= 60000 &&
    Number.isInteger(p.maxEvents) && p.maxEvents >= 1 && p.maxEvents <= 1000 &&
    typeof p.includeSnapshots === 'boolean' && typeof p.annotate === 'boolean'
}
function finish(job, state, owner) {
  const result = clone(read(state.config.scenario === 'empty' ? 'video-empty' : 'video-success').result)
  result.requestId = job.requestId; result.inputAssetId = job.inputAssetId; result.videoParameters = clone(job.videoParameters)
  result.createdAt = job.createdAt; result.updatedAt = now(); result.simulated = true
  if (state.config.scenario === 'failed') {
    result.state = 'FAILED'; delete result.videoResult
    result.error = errorResult('PROVIDER_AUTH', '模拟服务鉴权失败', job.requestId)
    return result
  }
  if (state.config.scenario === 'unknown') {
    result.state = 'UNKNOWN'; delete result.videoResult; result.unknownReason = 'PROVIDER_RESPONSE_LOST'
    result.error = errorResult('RESULT_UNKNOWN', '模拟请求结果未确认', job.requestId)
    result.error.unknownReason = 'PROVIDER_RESPONSE_LOST'
    return result
  }
  if (result.videoResult) {
    if (!job.videoParameters.includeSnapshots) { result.videoResult.snapshots = []; result.videoResult.events.forEach(e => { delete e.snapshotAssetId }) }
    else result.videoResult.snapshots = result.videoResult.snapshots.map((_, index) => {
      const meta = asset('video_snapshot_' + job.requestId + '_' + index, output, 'snapshot-' + (index + 1) + '.png')
      state.assets.set(meta.assetId, { meta, bytes: output, owner }); if (result.videoResult.events[index]) result.videoResult.events[index].snapshotAssetId = meta.assetId; return meta
    })
    if (job.videoParameters.annotate) { const stored = state.assets.get(job.inputAssetId); const meta = asset('video_output_' + job.requestId, stored.bytes, 'annotated.mp4', 'video/mp4'); state.assets.set(meta.assetId, { meta, bytes: stored.bytes, owner }); result.videoResult.annotatedVideo = meta }
  }
  return result
}
async function submit(req, res, state, owner) {
  const request = JSON.parse(await body(req, 65536)); const key = req.headers['idempotency-key'] || ''
  if (!/^[A-Za-z0-9_-]{8,128}$/.test(key) || !validate(request)) { fail(res, 400, 'INVALID_REQUEST', '视频请求不符合冻结契约'); return }
  const digest = JSON.stringify(request); const keyId = owner + ':video:' + key; const existing = state.keys.get(keyId)
  if (existing) { if (existing.digest !== digest) { fail(res, 409, 'IDEMPOTENCY_CONFLICT', '同 key 不同输入'); return }
    const existingJob = state.jobs.get(existing.id).job
    const status = ['SUCCEEDED', 'FAILED', 'UNKNOWN', 'CANCELLED'].includes(existingJob.state) ? 200 : 202
    json(res, envelope(clone(existingJob), status), status); return }
  if (!state.config.videoAvailable) { fail(res, 409, 'CAPABILITY_UNAVAILABLE', '模拟视频能力停用'); return }
  const inputAsset = state.assets.get(request.inputAssetId)
  if (!inputAsset || inputAsset.owner !== owner || inputAsset.meta.mediaType !== 'video/mp4') { fail(res, 404, 'NOT_FOUND', '视频输入不存在或无权访问'); return }
  const id = 'demo_video_' + (++state.sequence).toString().padStart(4, '0'); const createdAt = now()
  const job = { requestId: id, jobType: 'VIDEO_FILE_ANALYSIS', capabilityCode: request.capabilityCode, capabilityVersion: 'mock-v1.1', inputAssetId: request.inputAssetId,
    videoParameters: clone(request.parameters), state: 'PENDING', simulated: true, createdAt, updatedAt: createdAt }
  const completed = finish(job, state, owner); const immediate = state.config.scenario === 'immediate'
  const record = { job: immediate ? completed : job, completed, owner, finishAt: immediate ? null : Date.now() + 2000 }
  state.jobs.set(id, record); state.keys.set(keyId, { id, digest }); json(res, envelope(clone(record.job), immediate ? 200 : 202), immediate ? 200 : 202)
}
module.exports = { submit }
