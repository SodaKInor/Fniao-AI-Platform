// Explicit, loopback-only test harness. Never imported by frontend runtime code.
const http = require('node:http')
const fs = require('node:fs')
const path = require('node:path')
const { createState, capabilities, envelope, input, videoInput } = require('./fixtures.cjs')
const { json, fail } = require('./http.cjs')
const { session, owner } = require('./session.cjs')
const { control } = require('./control.cjs')
const assets = require('./assets.cjs')
const jobs = require('./jobs.cjs')
const video = require('./video.cjs')
const streams = require('./streams.cjs')
const root = path.resolve(__dirname, '../../../../../..')

async function handleApi(req, res, state) {
  const url = new URL(req.url, 'http://127.0.0.1')
  const record = { method: req.method, path: url.pathname, query: url.search, at: new Date().toISOString() }
  if (!url.pathname.startsWith('/_demo')) {
    state.requests.push(record)
    res.on('finish', () => { record.status = res.statusCode })
    res.on('close', () => { if (!res.writableFinished) record.interrupted = true })
  }
  if (await control(req, res, url, state) || await session(req, res, url, state)) return
  if (url.pathname === '/_demo/input.png') {
    res.writeHead(200, { 'Content-Type': 'image/png' }); res.end(input); return
  }
  if (url.pathname === '/_demo/input.mp4') {
    res.writeHead(200, { 'Content-Type': 'video/mp4' }); res.end(videoInput); return
  }
  const user = owner(req)
  if (!user) { fail(res, 401, 'UNAUTHENTICATED', '请登录本地模拟账号'); return }
  const p = url.pathname.replace(/^\/jeecg-boot\/ai\/v1/, '')
  if (p === '/capabilities' && req.method === 'GET') {
    json(res, envelope(capabilities(state))); return
  }
  if (p === '/assets' && req.method === 'POST') { await assets.upload(req, res, state, user); return }
  if (p === '/infer' && req.method === 'POST') { await jobs.submit(req, res, state, user); return }
  if (p === '/video-jobs' && req.method === 'POST') { await video.submit(req, res, state, user); return }
  if (p === '/stream-sources' && req.method === 'GET') { json(res, envelope(streams.sources(state))); return }
  if (p === '/stream-sessions' && req.method === 'POST') { await streams.start(req, res, state, user); return }
  if (p === '/jobs' && req.method === 'GET') { jobs.history(res, url, state, user); return }
  let match = /^\/jobs\/([A-Za-z0-9_-]+)$/.exec(p)
  if (match && req.method === 'GET') { await jobs.get(res, state, user, match[1]); return }
  match = /^\/jobs\/([A-Za-z0-9_-]+)\/cancel$/.exec(p)
  if (match && req.method === 'POST') { jobs.cancel(res, state, user, match[1]); return }
  match = /^\/stream-sessions\/([A-Za-z0-9_-]+)$/.exec(p)
  if (match && req.method === 'GET') { streams.get(res, state, user, match[1]); return }
  match = /^\/stream-sessions\/([A-Za-z0-9_-]+)\/events$/.exec(p)
  if (match && req.method === 'GET') { streams.events(res, url, state, user, match[1]); return }
  match = /^\/stream-sessions\/([A-Za-z0-9_-]+)\/stop$/.exec(p)
  if (match && req.method === 'POST') { streams.stop(res, state, user, match[1]); return }
  match = /^\/assets\/([A-Za-z0-9_-]+)\/content$/.exec(p)
  if (match && req.method === 'GET') { assets.download(req, res, state, user, match[1]); return }
  fail(res, 404, 'NOT_FOUND', '本地模拟未提供此接口')
}

function staticHandler(req, res, apiPort) {
  const url = new URL(req.url, 'http://127.0.0.1')
  if (url.pathname.startsWith('/jeecg-boot/') || url.pathname.startsWith('/_demo')) {
    const proxy = http.request({ host: '127.0.0.1', port: apiPort, path: req.url,
      method: req.method, headers: req.headers }, upstream => {
      res.writeHead(upstream.statusCode, upstream.headers); upstream.pipe(res)
      upstream.on('aborted', () => res.destroy())
    })
    proxy.on('error', () => { if (!res.headersSent) fail(res, 502, 'INTERNAL_ERROR', '模拟服务未启动'); else res.destroy() })
    req.pipe(proxy); return
  }
  const dist = path.join(root, 'frontend-vue/dist')
  let file = path.resolve(dist, '.' + decodeURIComponent(url.pathname))
  if (!file.startsWith(dist + path.sep)) file = path.join(dist, 'index.html')
  if (!fs.existsSync(file) || !fs.statSync(file).isFile()) file = path.join(dist, 'index.html')
  const mime = { '.html': 'text/html; charset=utf-8', '.js': 'application/javascript', '.css': 'text/css',
    '.png': 'image/png', '.svg': 'image/svg+xml', '.jpg': 'image/jpeg', '.woff': 'font/woff', '.woff2': 'font/woff2' }
  res.writeHead(200, { 'Content-Type': mime[path.extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-store' })
  const stream = fs.createReadStream(file)
  stream.on('error', () => res.destroy()); stream.pipe(res)
}
async function startServers({ apiPort = 19105, frontendPort = 18105 } = {}) {
  const state = createState()
  const api = http.createServer((req, res) => {
    handleApi(req, res, state).catch(() => { if (!res.headersSent) fail(res, 400, 'INVALID_REQUEST', '模拟请求无效'); else res.destroy() })
  })
  const listen = (server, port) => new Promise((resolve, reject) => {
    server.once('error', reject); server.listen(port, '127.0.0.1', resolve)
  })
  await listen(api, apiPort)
  const frontend = http.createServer((req, res) => staticHandler(req, res, api.address().port))
  try { await listen(frontend, frontendPort) } catch (error) { api.close(); throw error }
  return { state, api, frontend, close() { api.closeAllConnections(); frontend.closeAllConnections(); api.close(); frontend.close() } }
}
if (require.main === module) {
  startServers().then(servers => {
    console.log('SIMULATED ONLY: http://127.0.0.1:18105/_demo (API 127.0.0.1:19105)')
    for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => { servers.close() })
  }).catch(error => { console.error(error.message); process.exitCode = 1 })
}
module.exports = { startServers }
