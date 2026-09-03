const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')
const examples = path.resolve(__dirname, '../../../examples')
const read = name => JSON.parse(fs.readFileSync(path.join(examples, name + '.json'), 'utf8'))
const input = fs.readFileSync(path.join(examples, 'input.png'))
const output = fs.readFileSync(path.join(examples, 'annotated.png'))
const clone = value => JSON.parse(JSON.stringify(value))
const now = () => new Date().toISOString()

function asset(id, bytes, fileName, mediaType = 'image/png') {
  return { assetId: id, fileName, mediaType, sizeBytes: bytes.length,
    sha256: crypto.createHash('sha256').update(bytes).digest('hex'),
    createdAt: now(), expiresAt: new Date(Date.now() + 86400000).toISOString() }
}
function envelope(result, code = 200) {
  return { success: true, message: 'SIMULATED fixture; no GPU execution', code, result, timestamp: Date.now() }
}
function errorResult(errorCode, message, requestId) {
  return { errorCode, message, simulated: true, ...(requestId ? { requestId } : {}) }
}
function createState() {
  const state = { jobs: new Map(), assets: new Map(), keys: new Map(), requests: [], sequence: 0,
    config: { scenario: 'success', available: true, download: 'normal', queryDelay: 0 } }
  state.assets.set('mock_input_0001', { meta: asset('mock_input_0001', input, 'input.png'), bytes: input, owner: 'demo' })
  for (const name of ['success', 'empty', 'unknown', 'error']) {
    const job = clone(read(name === 'error' ? 'unknown' : name).result)
    if (name === 'error') {
      job.state = 'FAILED'
      job.error = errorResult('PROVIDER_AUTH', '模拟服务鉴权失败')
    }
    job.requestId = 'sample_' + name
    job.createdAt = now(); job.updatedAt = now()
    if (job.error) job.error.requestId = job.requestId
    if (job.result && job.result.artifacts.length) {
      const meta = asset('sample_output', output, 'annotated.png')
      job.result.artifacts = [meta]
      state.assets.set(meta.assetId, { meta, bytes: output, owner: 'demo' })
    }
    state.jobs.set(job.requestId, { job, owner: 'demo' })
  }
  return state
}
module.exports = { read, input, output, clone, now, asset, envelope, errorResult, createState }
