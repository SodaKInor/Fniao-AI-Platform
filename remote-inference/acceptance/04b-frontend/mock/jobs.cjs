const { read, clone, now, asset, output, envelope, errorResult } = require('./fixtures.cjs')
const { body, json, fail } = require('./http.cjs')

function current(record) {
  if (!record.finishAt || Date.now() < record.finishAt) return record.job
  record.job = record.completed; record.finishAt = null
  return record.job
}
function complete(job, scenario, state, owner) {
  const name = ({ empty: 'empty', failed: 'unknown', unknown: 'unknown' })[scenario] || 'success'
  const result = clone(read(name).result)
  result.requestId = job.requestId; result.inputAssetId = job.inputAssetId
  result.parameters = clone(job.parameters); result.createdAt = job.createdAt; result.updatedAt = now()
  if (scenario === 'failed') { result.state = 'FAILED'; result.error = errorResult('PROVIDER_AUTH', '模拟服务鉴权失败') }
  if (result.error) result.error.requestId = job.requestId
  if (result.result) {
    if (scenario === 'empty' || !job.parameters.annotate) result.result.artifacts = []
    else {
      const meta = asset('output_' + job.requestId, output, 'annotated.png')
      result.result.artifacts = [meta]
      state.assets.set(meta.assetId, { meta, bytes: output, owner })
    }
  }
  return result
}
async function submit(req, res, state, owner) {
  const request = JSON.parse(await body(req, 65536)); const key = req.headers['idempotency-key'] || ''
  const p = request.parameters || {}
  if (!/^[A-Za-z0-9_-]{8,128}$/.test(key) || request.capabilityCode !== 'image-detection.v1' ||
      Object.keys(request).some(k => !['capabilityCode', 'inputAssetId', 'parameters', 'retryOfRequestId'].includes(k)) ||
      Object.keys(p).length !== 3 || !Number.isFinite(p.threshold) || p.threshold < 0 || p.threshold > 1 ||
      !Number.isInteger(p.maxDetections) || p.maxDetections < 1 || p.maxDetections > 100 || typeof p.annotate !== 'boolean') {
    fail(res, 400, 'INVALID_REQUEST', '请求不符合冻结契约'); return
  }
  const digest = JSON.stringify([request.capabilityCode, request.inputAssetId, p.threshold, p.maxDetections, p.annotate, request.retryOfRequestId || ''])
  const existing = state.keys.get(owner + ':' + key)
  if (existing) {
    if (existing.digest !== digest) { fail(res, 409, 'IDEMPOTENCY_CONFLICT', '同 key 不同输入'); return }
    const job = current(state.jobs.get(existing.id)); const status = ['SUCCEEDED', 'FAILED', 'UNKNOWN'].includes(job.state) ? 200 : 202
    json(res, envelope(job, status), status); return
  }
  if (!state.config.available) { fail(res, 409, 'CAPABILITY_UNAVAILABLE', '模拟能力停用'); return }
  const input = state.assets.get(request.inputAssetId)
  if (!input || input.owner !== owner) { fail(res, 404, 'NOT_FOUND', '输入不存在或无权访问'); return }
  const id = 'demo_job_' + (++state.sequence).toString().padStart(4, '0')
  const job = { requestId: id, capabilityCode: request.capabilityCode, capabilityVersion: 'mock-v1',
    inputAssetId: request.inputAssetId, parameters: p, state: 'WAITING', simulated: true, createdAt: now(), updatedAt: now() }
  const completed = complete(job, state.config.scenario, state, owner)
  const immediate = state.config.scenario === 'immediate'
  state.jobs.set(id, { job: immediate ? completed : job, completed, owner,
    finishAt: immediate ? null : Date.now() + (state.config.scenario === 'slow' ? 60000 : 4500) })
  state.keys.set(owner + ':' + key, { id, digest })
  json(res, envelope(immediate ? completed : job, immediate ? 200 : 202), immediate ? 200 : 202)
}
async function get(res, state, owner, id) {
  const record = state.jobs.get(id)
  if (!record || record.owner !== owner) { fail(res, 404, 'NOT_FOUND', '任务不存在或无权访问'); return }
  const response = clone(current(record))
  if (state.config.queryDelay) await new Promise(resolve => setTimeout(resolve, state.config.queryDelay))
  json(res, envelope(response))
}
function history(res, url, state, owner) {
  const filter = url.searchParams.get('state') || ''
  let offset = 0
  try {
    if (url.searchParams.has('cursor')) {
      const decoded = JSON.parse(Buffer.from(url.searchParams.get('cursor'), 'base64url').toString())
      if (decoded.owner !== owner || decoded.filter !== filter) throw new Error('cursor')
      offset = decoded.offset
    }
  } catch (_) { fail(res, 400, 'INVALID_REQUEST', '模拟游标无效'); return }
  const all = [...state.jobs.values()].filter(r => r.owner === owner).map(current)
    .filter(job => !filter || job.state === filter).sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.requestId.localeCompare(a.requestId))
  const limit = Math.min(100, Math.max(1, Number(url.searchParams.get('limit') || 20)))
  const items = all.slice(offset, offset + limit)
  const page = { items }
  if (offset + limit < all.length) page.nextCursor = Buffer.from(JSON.stringify({ owner, filter, offset: offset + limit })).toString('base64url')
  json(res, envelope(page))
}
function cancel(res, state, owner, id) {
  const record = state.jobs.get(id)
  if (!record || record.owner !== owner) { fail(res, 404, 'NOT_FOUND', '任务不存在或无权访问'); return }
  const job = current(record)
  if (job.state !== 'PENDING') { fail(res, 409, 'CANCEL_NOT_SUPPORTED', '仅未派发任务可取消'); return }
  job.state = 'CANCELLED'; job.updatedAt = now(); record.finishAt = null; record.completed = job
  json(res, envelope(clone(job)))
}
module.exports = { submit, get, history, cancel }
