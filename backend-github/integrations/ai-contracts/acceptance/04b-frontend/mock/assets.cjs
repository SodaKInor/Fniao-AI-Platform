const crypto = require('node:crypto')
const { asset, envelope } = require('./fixtures.cjs')
const { body, json, fail } = require('./http.cjs')

async function upload(req, res, state, owner) {
  const raw = await body(req)
  const boundary = /boundary=(?:"([^"]+)"|([^;]+))/.exec(req.headers['content-type'] || '')
  if (!boundary) { fail(res, 400, 'INVALID_REQUEST', '需要 multipart file'); return }
  const marker = Buffer.from('\r\n--' + (boundary[1] || boundary[2]))
  const headerEnd = raw.indexOf('\r\n\r\n')
  const end = raw.indexOf(marker, headerEnd + 4)
  const header = raw.subarray(0, headerEnd).toString()
  if (headerEnd < 0 || end < 0 || !/name="file"/.test(header)) { fail(res, 400, 'INVALID_REQUEST', '缺少 file'); return }
  const bytes = raw.subarray(headerEnd + 4, end)
  const fileName = (/filename="([^"]+)"/.exec(header) || [])[1] || 'input.png'
  const type = (/Content-Type:\s*([^\r\n]+)/i.exec(header) || [])[1]
  if (!['image/png', 'image/jpeg'].includes(type)) { fail(res, 415, 'UNSUPPORTED_MEDIA', '只接受图片'); return }
  if (!bytes.length || bytes.length > 10485760) { fail(res, 413, 'LIMIT_EXCEEDED', '输入超限'); return }
  const meta = asset('input_' + crypto.randomBytes(8).toString('hex'), bytes, fileName, type)
  state.assets.set(meta.assetId, { meta, bytes, owner }); json(res, envelope(meta, 201), 201)
}
function download(req, res, state, user, id) {
  const stored = state.assets.get(id)
  if (!stored || stored.owner !== user) { fail(res, 404, 'NOT_FOUND', '记录不存在或无权访问'); return }
  if (state.config.download === 'expired') { fail(res, 410, 'ASSET_EXPIRED', '模拟成果过期'); return }
  if (state.config.download === 'denied') { fail(res, 404, 'NOT_FOUND', '模拟权限拒绝'); return }
  res.writeHead(200, { 'Content-Type': stored.meta.mediaType, 'Content-Length': stored.bytes.length,
    'Content-Disposition': 'attachment; filename="' + encodeURIComponent(stored.meta.fileName) + '"',
    'Cache-Control': 'private, no-store', 'X-Content-Type-Options': 'nosniff' })
  if (state.config.download === 'interrupted') {
    res.write(stored.bytes.subarray(0, 8)); setTimeout(() => res.destroy(), 100); return
  }
  res.end(stored.bytes)
}
module.exports = { upload, download }
