const { test } = require('node:test')
const assert = require('node:assert/strict')
const { loadSource } = require('./load-source.cjs')
const flush = () => new Promise(resolve => setImmediate(resolve))
const polling = loadSource('modules/ai/job/polling.js')
const presentation = loadSource('modules/ai/result/presentation.js')

function page(feature, file, api, extra = {}, globals = {}) {
  const component = loadSource('modules/ai/' + feature + '/' + file, { '@/modules/ai': api,
    '@/modules/ai/job/polling': polling, '@/modules/ai/result/presentation': presentation }, globals).default
  const instance = { ...component.data(), ...extra }
  for (const [name, method] of Object.entries(component.methods)) instance[name] = method.bind(instance)
  for (const [name, getter] of Object.entries(component.computed || {})) Object.defineProperty(instance, name, { get: getter.bind(instance) })
  component.created.call(instance)
  return { component, instance }
}

test('detail lifecycle stops in flight; activation starts once; route reuse rejects old responses', async () => {
  const requests = []
  const { component: c, instance: v } = page('job', 'JobDetailPage.vue', {
    downloadAsset() { throw new Error('unexpected download') },
    getJob(id) { return new Promise((resolve, reject) => requests.push({ id, resolve, reject })) }
  }, { $route: { params: { requestId: 'A' } } })
  c.mounted.call(v); c.activated.call(v); assert.equal(requests.length, 1)
  c.deactivated.call(v); requests[0].resolve({ requestId: 'A', state: 'SUCCEEDED' }); await flush()
  assert.equal(v.job, null)
  c.activated.call(v); assert.equal(requests.length, 2)
  c.beforeRouteUpdate.call(v, {}, {}, () => {})
  v.$route.params.requestId = 'B'; c.watch['$route.params.requestId'].call(v)
  requests[2].resolve({ requestId: 'B', state: 'SUCCEEDED' }); await flush()
  requests[1].reject(new Error('late failure')); await flush()
  assert.equal(v.job.requestId, 'B'); assert.equal(v.error, '')
  c.beforeRouteLeave.call(v, {}, {}, () => {}); c.beforeDestroy.call(v)
})

test('history filtering and leaving discard obsolete pages', async () => {
  const requests = []
  const { component: c, instance: v } = page('job', 'HistoryPage.vue', {
    listJobs(query) { return new Promise(resolve => requests.push({ query, resolve })) }
  })
  c.mounted.call(v); v.state = 'SUCCEEDED'; v.refresh()
  requests[1].resolve({ items: [{ requestId: 'new' }] }); await flush()
  requests[0].resolve({ items: [{ requestId: 'stale' }], nextCursor: 'stale-cursor' }); await flush()
  assert.equal(v.items.length, 1); assert.equal(v.items[0].requestId, 'new'); assert.equal(v.nextCursor, null)
  v.refresh(); c.deactivated.call(v); requests[2].resolve({ items: [{ requestId: 'after-leave' }] }); await flush()
  assert.equal(v.items.length, 0)
})

test('submission retry preserves key and body; late response after leaving cannot navigate', async () => {
  const calls = [], pushes = []
  const capability = { code: 'image-detection.v1', parametersSchema: 'detection.v1', available: true,
    inputMediaTypes: ['image/png'], maxWaitMillis: 1500 }
  const { component: c, instance: v } = page('image', 'InferencePage.vue', {
    listCapabilities: async () => [capability],
    submitInference(request, key) { return new Promise((resolve, reject) => calls.push({ request, key, resolve, reject })) }
  }, { $router: { push(value) { pushes.push(value) } } }, {
    window: { crypto: { getRandomValues(bytes) { bytes.fill(7) } } }
  })
  c.mounted.call(v); await flush(); v.asset = { assetId: 'inputA' }
  const attempt = v.submit(); v.submit(); assert.equal(calls.length, 1)
  calls[0].reject(new Error('response lost')); await attempt
  const repeated = v.submit(); assert.equal(calls[1].key, calls[0].key)
  assert.equal(JSON.stringify(calls[1].request), JSON.stringify(calls[0].request))
  c.beforeRouteLeave.call(v, {}, {}, () => {})
  calls[1].resolve({ requestId: 'A', state: 'WAITING' }); await repeated
  assert.equal(pushes.length, 0); assert.ok(v.draft)
})

test('upload panel blocks unsupported and oversized inputs before sending a file', () => {
  const c = loadSource('modules/ai/asset/UploadPanel.vue').default
  const emitted = []
  const v = { capability: { inputMediaTypes: ['image/png'], maxInputBytes: 10 },
    $emit(...args) { emitted.push(args) } }
  for (const file of [{ type: 'text/html', size: 1 }, { type: 'image/png', size: 11 }]) {
    c.methods.selectFile.call(v, { target: { files: [file], value: 'test' } })
  }
  assert.equal(emitted.length, 2); assert.ok(emitted.every(e => e[0] === 'invalid'))
})
