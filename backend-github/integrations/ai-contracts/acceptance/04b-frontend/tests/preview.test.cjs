const { test } = require('node:test')
const assert = require('node:assert/strict')
const { loadSource } = require('./load-source.cjs')
const presentation = loadSource('services/ai/presentation.js')

test('preview rejects obsolete blobs and releases preview/download URLs on lifecycle exits', async () => {
  const pending = [], created = [], revoked = [], timers = new Map()
  const c = loadSource('components/ai/ResultPreview.vue', {
    './renderers/DetectionResult': {}, '@/services/ai/presentation': presentation
  }, {
    URL: { createObjectURL() { const url = 'blob:' + created.length; created.push(url); return url },
      revokeObjectURL(url) { revoked.push(url) } },
    document: { createElement: () => ({ click() {}, remove() {} }), body: { appendChild() {} } },
    setTimeout(callback) { const id = timers.size + 1; timers.set(id, callback); return id },
    clearTimeout(id) { timers.delete(id) }
  }).default
  const v = { ...c.data(), supported: true, loadAsset() { return new Promise(resolve => pending.push(resolve)) },
    describeError: presentation.errorMessage }
  for (const [name, method] of Object.entries(c.methods)) v[name] = method.bind(v)
  c.created.call(v)
  const stale = v.readAsset({}, false)
  c.watch.result.call(v); pending.shift()(new Blob(['old'])); await stale
  assert.equal(created.length, 0); assert.equal(v.previewUrl, '')
  const preview = v.readAsset({}, false); pending.shift()(new Blob(['image'])); await preview
  assert.equal(v.previewUrl, 'blob:0')
  const download = v.readAsset({ fileName: 'image.png' }, true)
  pending.shift()(new Blob(['image'])); await download
  assert.equal(timers.size, 1)
  c.deactivated.call(v)
  assert.deepEqual(revoked, ['blob:0', 'blob:1']); assert.equal(timers.size, 0)
  c.beforeDestroy.call(v); assert.equal(revoked.length, 2)
})

test('only agreed capability/result schemas render, including a valid empty detection list', () => {
  const result = { data: { schemaVersion: 'detection.v1', imageWidth: 16, imageHeight: 16, detections: [] }, artifacts: [] }
  assert.equal(presentation.supportedResult(result), true)
  assert.equal(presentation.supportedResult({ ...result, data: { ...result.data, schemaVersion: 'video.v1' } }), false)
  assert.equal(presentation.capabilitySupported({ code: 'audio.v1', parametersSchema: 'audio.v1', inputMediaTypes: ['audio/wav'] }), false)
})
