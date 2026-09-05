const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { loadSource, frontend } = require('./load-source.cjs')
const presentation = loadSource('modules/ai/result/presentation.js')
const flush = () => new Promise(resolve => setImmediate(resolve))

function page(feature, file, api, extra = {}, globals = {}) {
  const component = loadSource('modules/ai/' + feature + '/' + file, { '@/modules/ai': api, '@/modules/ai/result/presentation': presentation }, globals).default
  const instance = { ...component.data(), ...extra }
  for (const [name, method] of Object.entries(component.methods)) instance[name] = method.bind(instance)
  for (const [name, getter] of Object.entries(component.computed || {})) Object.defineProperty(instance, name, { get: getter.bind(instance) })
  component.created.call(instance); return { component, instance }
}
const videoCapability = { code: 'video-file-analysis.v1', parametersSchema: 'video-analysis.v1', available: true,
  inputMediaTypes: ['video/mp4'], maxInputBytes: 1000, maxWaitMillis: 0 }
const streamCapability = { code: 'video-stream-analysis.v1', parametersSchema: 'stream-analysis.v1', available: true, inputMediaTypes: [] }

test('video retry preserves exact key and body; leaving rejects a late success', async () => {
  const calls = [], pushes = []
  const { component: c, instance: v } = page('video', 'VideoInferencePage.vue', { listCapabilities: async () => [videoCapability],
    submitVideoJob(request, key) { return new Promise((resolve, reject) => calls.push({ request, key, resolve, reject })) } },
  { $router: { push(value) { pushes.push(value) } } }, { window: { crypto: { getRandomValues(bytes) { bytes.fill(9) } } } })
  try {
    c.mounted.call(v); await flush(); v.asset = { assetId: 'video_A' }
    const first = v.submit(); calls[0].reject(new Error('lost')); await first
    const second = v.submit(); assert.equal(calls[1].key, calls[0].key); assert.deepEqual(calls[1].request, calls[0].request)
    assert.deepEqual(Object.keys(calls[1].request).sort(), ['capabilityCode', 'inputAssetId', 'parameters'])
    c.beforeRouteLeave.call(v, {}, {}, () => {}); calls[1].resolve({ requestId: 'late' }); await second; assert.equal(pushes.length, 0)
  } finally { c.beforeDestroy.call(v) }
})

test('stream start chooses only available opaque source and retry preserves exact payload', async () => {
  const calls = [], pushes = [], sources = [{ streamSourceId: 'disabled', displayName: '停用', available: false }, { streamSourceId: 'ready', displayName: '可用', available: true }]
  const { component: c, instance: v } = page('stream', 'StreamStartPage.vue', { listCapabilities: async () => [streamCapability], listStreamSources: async () => sources,
    startStreamSession(request, key) { return new Promise((resolve, reject) => calls.push({ request, key, resolve, reject })) } }, { $router: { push(value) { pushes.push(value) } } }, { window: { crypto: { getRandomValues(bytes) { bytes.fill(4) } } } })
  try {
    c.mounted.call(v); await flush(); assert.equal(v.sourceId, 'ready'); v.sourceId = 'disabled'; assert.equal(v.canStart, false); v.sourceId = 'ready'
    const first = v.start(); calls[0].reject(new Error('lost')); await first; const second = v.start()
    assert.equal(calls[1].key, calls[0].key); assert.deepEqual(calls[1].request, calls[0].request)
    assert.deepEqual(Object.keys(calls[1].request).sort(), ['capabilityCode', 'parameters', 'streamSourceId'])
    assert.deepEqual(Object.keys(calls[1].request.parameters).sort(), ['includeSnapshots', 'maxEventsPerPoll', 'pollIntervalMillis'])
    calls[1].resolve({ sessionId: 'session_A' }); await second; assert.equal(pushes[0].name, 'AiStreamSession')
  } finally { c.beforeDestroy.call(v) }
})

test('video result validation accepts empty timeline and rejects unbounded shapes', () => {
  const valid = { resultType: 'VIDEO_TIMELINE', simulated: true, events: [], snapshots: [] }
  assert.equal(presentation.supportedVideoResult(valid), true)
  assert.equal(presentation.supportedVideoResult({ ...valid, resultType: 'OTHER' }), false)
  assert.equal(presentation.supportedVideoResult({ ...valid, events: [{ eventId: 'x', offsetMillis: -1, eventType: 'person' }] }), false)
})

test('frontend runtime has no connection-secret fields and no direct provider addresses', () => {
  const root = path.join(frontend, 'src/modules/ai')
  const files = []
  const visit = directory => fs.readdirSync(directory, { withFileTypes: true }).forEach(entry => {
    const target = path.join(directory, entry.name)
    if (entry.isDirectory()) visit(target)
    else files.push(target)
  })
  visit(root)
  const source = files.map(file => fs.readFileSync(file, 'utf8')).join('\n').toLowerCase()
  for (const forbidden of ['providersessionid', 'provider_source_id', 'gpuurl', 'rtspurl', 'streamcredential', 'authorization: bearer']) assert.equal(source.includes(forbidden), false, forbidden)
})

test('leaving a stream page never sends stop; only explicit stop resumes an unconfirmed session', async () => {
  const calls = { start: [], resume: [], stop: 0, remote: 0 }
  const polling = { createStreamPolling() { return { start(id) { calls.start.push(id) }, resume(id) { calls.resume.push(id) }, stop() { calls.stop++ } } }, streamTerminalStates: ['STOPPED', 'FAILED', 'UNKNOWN'] }
  const response = { sessionId: 'session_A', state: 'STOP_REQUESTED' }
  const api = { getStreamSession() {}, getStreamEvents() {}, downloadSnapshotAsset() {},
    stopStreamSession() { calls.remote++; return Promise.resolve(response) } }
  const component = loadSource('modules/ai/stream/StreamSessionPage.vue', { '@/modules/ai': api,
    '@/modules/ai/stream/polling': polling, '@/modules/ai/result/presentation': presentation }).default
  const instance = { ...component.data(), $route: { params: { sessionId: 'session_A' } } }
  for (const [name, method] of Object.entries(component.methods)) instance[name] = method.bind(instance)
  for (const [name, getter] of Object.entries(component.computed || {})) Object.defineProperty(instance, name, { get: getter.bind(instance) })
  component.created.call(instance); component.mounted.call(instance); component.beforeRouteLeave.call(instance, {}, {}, () => {})
  assert.equal(calls.remote, 0); assert.ok(calls.stop >= 1)
  instance.viewActive = true; instance.session = { sessionId: 'session_A', state: 'RUNNING' }
  await instance.stopRemote(); assert.equal(calls.remote, 1); assert.equal(instance.session.state, 'STOP_REQUESTED')
  assert.deepEqual(calls.resume, ['session_A'])
})
