const { envelope, errorResult } = require('./fixtures.cjs')
function json(res, result, status = 200) {
  res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' })
  res.end(JSON.stringify(result))
}
function fail(res, status, code, message) {
  json(res, { ...envelope(errorResult(code, message), status), success: false }, status)
}
async function body(req, max = 11 * 1048576) {
  const chunks = []; let length = 0
  for await (const part of req) {
    length += part.length
    if (length > max) throw new Error('Request exceeds demo limit')
    chunks.push(part)
  }
  return Buffer.concat(chunks)
}
module.exports = { json, fail, body }
