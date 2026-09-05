const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')
const examples = path.resolve(__dirname, '../../../examples')
const read = name => JSON.parse(fs.readFileSync(path.join(examples, name + '.json'), 'utf8'))
const input = fs.readFileSync(path.join(examples, 'input.png'))
const output = fs.readFileSync(path.join(examples, 'annotated.png'))
const videoInput = Buffer.from('SIMULATED-ONLY-MP4-UPLOAD-FIXTURE')
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
  const state = { jobs: new Map(), assets: new Map(), keys: new Map(), streams: new Map(), streamKeys: new Map(),
    requests: [], sequence: 0, streamSequence: 0,
    config: { scenario: 'success', available: true, videoAvailable: true, streamAvailable: true,
      download: 'normal', queryDelay: 0, stop: 'confirmed', streamScenario: 'normal', forceViewer: false } }
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
function capabilities(state) {
  const image = clone(read('capabilities').result[0])
  image.available = state.config.available; image.unavailableReason = image.available ? '' : '模拟图片能力停用'
  const video = { code: 'video-file-analysis.v1', version: 'mock-v1.1', displayName: '模拟上传视频分析',
    available: state.config.videoAvailable, simulated: true,
    unavailableReason: state.config.videoAvailable ? '' : '模拟视频能力停用', inputMediaTypes: ['video/mp4'],
    maxInputBytes: 10485760, maxOutputBytes: 10485760, maxWaitMillis: 0, parametersSchema: 'video-analysis.v1' }
  const stream = { code: 'video-stream-analysis.v1', version: 'mock-v1.1', displayName: '模拟实时事件分析',
    available: state.config.streamAvailable, simulated: true,
    unavailableReason: state.config.streamAvailable ? '' : '模拟实时能力停用', inputMediaTypes: [],
    maxInputBytes: 1, maxOutputBytes: 10485760, maxWaitMillis: 0, parametersSchema: 'stream-analysis.v1' }
  return [image, video, stream]
}
module.exports = { read, input, output, videoInput, clone, now, asset, envelope, errorResult, createState, capabilities }
