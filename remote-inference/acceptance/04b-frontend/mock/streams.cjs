const { clone, now, asset, output, envelope, errorResult } = require('./fixtures.cjs')
const { body, json, fail } = require('./http.cjs')

function sources(state) {
  return [{ streamSourceId: 'mock_source_ready', displayName: '模拟已授权入口', available: state.config.streamAvailable,
    unavailableReason: state.config.streamAvailable ? null : '模拟来源映射未确认' },
  { streamSourceId: 'mock_source_disabled', displayName: '模拟停用入口', available: false, unavailableReason: '模拟来源映射未确认' }]
}
function validate(request) {
  const p = request.parameters || {}
  return request.capabilityCode === 'video-stream-analysis.v1' && Object.keys(request).length === 3 &&
    Object.keys(request).every(k => ['capabilityCode', 'streamSourceId', 'parameters'].includes(k)) && Object.keys(p).length === 3 &&
    Number.isInteger(p.maxEventsPerPoll) && p.maxEventsPerPoll >= 1 && p.maxEventsPerPoll <= 200 &&
    Number.isInteger(p.pollIntervalMillis) && p.pollIntervalMillis >= 250 && p.pollIntervalMillis <= 30000 && typeof p.includeSnapshots === 'boolean'
}
async function start(req, res, state, owner) {
  const request = JSON.parse(await body(req, 65536)); const key = req.headers['idempotency-key'] || ''
  if (!/^[A-Za-z0-9_-]{8,128}$/.test(key) || !validate(request)) { fail(res, 400, 'INVALID_REQUEST', '会话请求不符合冻结契约'); return }
  const source = sources(state).find(item => item.streamSourceId === request.streamSourceId)
  if (!source || !source.available) { fail(res, 409, 'CAPABILITY_UNAVAILABLE', '来源当前不可用'); return }
  const digest = JSON.stringify(request); const keyId = owner + ':stream:' + key; const existing = state.streamKeys.get(keyId)
  if (existing) { if (existing.digest !== digest) { fail(res, 409, 'IDEMPOTENCY_CONFLICT', '同 key 不同输入'); return }
    json(res, envelope(clone(state.streams.get(existing.id).session), 200)); return }
  const id = 'demo_stream_' + (++state.streamSequence).toString().padStart(4, '0'); const createdAt = now()
  const session = { sessionId: id, streamSourceId: request.streamSourceId, capabilityCode: request.capabilityCode,
    capabilityVersion: 'mock-v1.1', parameters: clone(request.parameters), state: 'RUNNING', createdAt, updatedAt: createdAt }
  if (state.config.streamScenario === 'failed') {
    session.state = 'FAILED'; session.error = errorResult('PROVIDER_OFFLINE', '模拟实时服务不可用', id)
  }
  const snapshot = asset('stream_snapshot_' + id, output, 'event.png'); state.assets.set(snapshot.assetId, { meta: snapshot, bytes: output, owner })
  const event = { eventId: 'event_' + id, offsetMillis: 2500, occurredAt: now(), eventType: 'person', score: 0.93,
    ...(request.parameters.includeSnapshots ? { snapshotAssetId: snapshot.assetId } : {}) }
  state.streams.set(id, { session, owner, event: state.config.streamScenario === 'normal' ? event : null }); state.streamKeys.set(keyId, { id, digest }); json(res, envelope(clone(session), 202), 202)
}
function get(res, state, owner, id) {
  const record = state.streams.get(id); if (!record || record.owner !== owner) { fail(res, 404, 'NOT_FOUND', '会话不存在或无权访问'); return }
  json(res, envelope(clone(record.session)))
}
function events(res, url, state, owner, id) {
  const record = state.streams.get(id); if (!record || record.owner !== owner) { fail(res, 404, 'NOT_FOUND', '会话不存在或无权访问'); return }
  const cursor = url.searchParams.get('cursor'); json(res, envelope({ sessionId: id,
    items: cursor || !record.event ? [] : [clone(record.event)], nextCursor: 'cursor_' + id }))
}
function stop(res, state, owner, id) {
  const record = state.streams.get(id); if (!record || record.owner !== owner) { fail(res, 404, 'NOT_FOUND', '会话不存在或无权访问'); return }
  if (state.config.stop === 'unsupported') { fail(res, 409, 'CANCEL_NOT_SUPPORTED', '模拟停止不受支持'); return }
  record.session.updatedAt = now()
  if (state.config.stop === 'unknown') { record.session.state = 'STOP_REQUESTED'; record.session.unknownReason = 'STOP_CONFIRMATION_UNKNOWN'; record.session.error = errorResult('RESULT_UNKNOWN', '模拟停止响应丢失', id); record.session.error.unknownReason = 'STOP_CONFIRMATION_UNKNOWN'; json(res, envelope(clone(record.session), 202), 202); return }
  record.session.state = 'STOPPED'; json(res, envelope(clone(record.session)))
}
module.exports = { sources, start, get, events, stop }
