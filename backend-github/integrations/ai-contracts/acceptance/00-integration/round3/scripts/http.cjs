const fs = require('node:fs')
const path = require('node:path')
const assert = require('node:assert/strict')
const { work, evidence, run } = require('./runtime.cjs')
const base = 'http://127.0.0.1:19100/jeecg-boot'
const receipts = []
async function request(url, options = {}, token) {
  const response = await fetch(base + url, { ...options, headers: {
    ...(token ? { 'X-Access-Token': token } : {}), ...options.headers
  } })
  const bytes = Buffer.from(await response.arrayBuffer())
  const contentType = response.headers.get('content-type') || ''
  const body = contentType.includes('json') ? JSON.parse(bytes) : bytes
  receipts.push({ method: options.method || 'GET', url, status: response.status,
    ...(contentType.includes('json') && url.startsWith('/ai/') ? { body } : { contentType, size: bytes.length }) })
  return { status: response.status, body, headers: response.headers }
}
async function login(name) {
  const account = JSON.parse(fs.readFileSync(path.join(work, 'accounts.private.json'))).find(a => a.name === name)
  assert(account)
  const checkKey = 'ri00-' + Date.now() + '-' + name
  const image = await request('/sys/randomImage/' + checkKey)
  assert.equal(image.body.success, true)
  const logs = run('docker', ['logs', '--tail', '100', 'wgai-ri-00-integration-backend-1'], { stdio: ['pipe', 'pipe', 'pipe'] })
  const codes = [...logs.matchAll(/获取验证码[^\n]*checkCode = ([a-zA-Z0-9]+)/g)]
  assert(codes.length, 'Expected local acceptance captcha audit entry')
  const response = await request('/sys/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: account.username, password: account.password, checkKey, captcha: codes.at(-1)[1] }) })
  assert(response.body.success, 'Real password/captcha login must succeed')
  assert(response.body.result.token)
  return response.body.result.token
}
const jsonPost = body => ({ method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
function save(name, extra = {}) {
  fs.writeFileSync(path.join(evidence, name + '.json'), JSON.stringify({ status: 'PASS', ...extra, requests: receipts }, null, 2) + '\n')
}
module.exports = { base, request, login, jsonPost, save, receipts }
if (require.main === module) (async () => {
  const token = await login('owner_a')
  const response = await request('/ai/v1/capabilities', {}, token)
  assert.equal(response.status, 200)
  assert(response.body.result.some(c => c.available && c.simulated))
  save('spring-boot-smoke', { scope: 'Full application image, real Spring/DictAspect/Shiro/MySQL/Redis/password+captcha; no replacement business API' })
  console.log('PASS: full Spring Boot context and actual authenticated capabilities')
})().catch(error => { console.error(error.message); process.exitCode = 1 })
