#!/usr/bin/env node

const assert = require('node:assert/strict')
const crypto = require('node:crypto')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { execFileSync } = require('node:child_process')

const run = (command, args, options = {}) => execFileSync(command, args, {
  encoding: 'utf8',
  stdio: ['pipe', 'pipe', 'pipe'],
  ...options
})
const root = run('git', ['rev-parse', '--show-toplevel']).trim()
assert.equal(process.cwd(), root, 'run-local-rc.cjs must run from the Git root')

const deploy = path.join(root, 'deploy')
const envFile = path.join(deploy, '.env')
const baseCompose = path.join(deploy, 'docker-compose.yml')
const stubCompose = path.join(deploy, 'remote-inference', 'stub.override.yml')
assert(fs.existsSync(envFile), 'deploy/.env is required; run deploy/scripts/start.sh first')

const env = Object.fromEntries(fs.readFileSync(envFile, 'utf8').split(/\r?\n/)
  .filter(line => line && !line.startsWith('#') && line.includes('='))
  .map(line => {
    const index = line.indexOf('=')
    return [line.slice(0, index), line.slice(index + 1)]
  }))
const composePrefix = ['compose', '--project-directory', deploy, '--env-file', envFile, '-f', baseCompose]
const compose = (...args) => run('docker', [...composePrefix, ...args], { cwd: root })
const composeStub = (...args) => run('docker', [...composePrefix, '-f', stubCompose,
  '--profile', 'remote-ai-stub', ...args], { cwd: root })
const mysqlDatabase = env.MYSQL_DATABASE || 'java_ai'
const mysqlUser = env.MYSQL_USER || 'wgai'
const mysqlPassword = env.MYSQL_PASSWORD
assert(mysqlPassword, 'MYSQL_PASSWORD is required in deploy/.env')

function sql(statement) {
  return run('docker', [...composePrefix, 'exec', '-T', '-e', `MYSQL_PWD=${mysqlPassword}`,
    'mysql', 'mysql', `-u${mysqlUser}`, `-D${mysqlDatabase}`, '--default-character-set=utf8mb4',
    '--batch', '--skip-column-names'], { cwd: root, input: statement })
}

function quote(value) {
  return "'" + String(value).replaceAll("'", "''") + "'"
}

function stableId(value) {
  return crypto.createHash('md5').update('final-local-rc:' + value).digest('hex')
}

function serviceContainer(service, stub = false) {
  const output = stub ? composeStub('ps', '-q', service) : compose('ps', '-q', service)
  return output.trim()
}

async function waitHealthy(service, stub = false, timeoutMillis = 300000) {
  const deadline = Date.now() + timeoutMillis
  while (Date.now() < deadline) {
    const container = serviceContainer(service, stub)
    if (container) {
      const state = run('docker', ['inspect', '--format',
        '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}', container]).trim()
      if (state === 'healthy' || state === 'running') return
      if (['unhealthy', 'exited', 'dead'].includes(state)) {
        throw new Error(`${service} entered ${state}`)
      }
    }
    await new Promise(resolve => setTimeout(resolve, 3000))
  }
  throw new Error(`Timed out waiting for ${service}`)
}

const frontendPort = env.FRONTEND_PORT || '8080'
const frontendAddress = env.FRONTEND_BIND_ADDRESS || '127.0.0.1'
const baseUrl = `http://${frontendAddress}:${frontendPort}/jeecg-boot`
const receipts = []

async function request(url, options = {}, token) {
  const response = await fetch(baseUrl + url, {
    signal: AbortSignal.timeout(30000),
    ...options,
    headers: { ...(token ? { 'X-Access-Token': token } : {}), ...options.headers }
  })
  const bytes = Buffer.from(await response.arrayBuffer())
  const contentType = response.headers.get('content-type') || ''
  const body = contentType.includes('json') ? JSON.parse(bytes) : bytes
  receipts.push({ method: options.method || 'GET', url, status: response.status })
  return { status: response.status, body, headers: response.headers }
}

const jsonPost = body => ({
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
})

async function login(account) {
  const checkKey = `final-rc-${Date.now()}-${account.name}`
  const image = await request('/sys/randomImage/' + checkKey)
  assert.equal(image.status, 200)
  assert.equal(image.body.success, true)
  const backend = serviceContainer('backend')
  const logs = run('docker', ['logs', '--tail', '250', backend])
  const codes = [...logs.matchAll(/获取验证码[^\n]*checkCode = ([a-zA-Z0-9]+)/g)]
  assert(codes.length, 'Expected the local captcha audit entry')
  const response = await request('/sys/login', jsonPost({
    username: account.username,
    password: account.password,
    checkKey,
    captcha: codes.at(-1)[1]
  }))
  assert.equal(response.status, 200)
  assert.equal(response.body.success, true,
    `Real password/captcha login must succeed: ${response.body.message || 'unknown response'}`)
  assert(response.body.result.token)
  return response.body.result.token
}

async function upload(token, bytes, mediaType, fileName) {
  const form = new FormData()
  form.append('file', new Blob([bytes], { type: mediaType }), fileName)
  return request('/ai/v1/assets', { method: 'POST', body: form }, token)
}

async function terminalJob(requestId, token) {
  for (let attempt = 0; attempt < 100; attempt++) {
    const response = await request('/ai/v1/jobs/' + requestId, {}, token)
    assert.equal(response.status, 200)
    if (['SUCCEEDED', 'FAILED', 'UNKNOWN', 'CANCELLED'].includes(response.body.result.state)) {
      return response.body.result
    }
    await new Promise(resolve => setTimeout(resolve, 250))
  }
  throw new Error('Job did not reach a terminal state: ' + requestId)
}

async function sessionState(sessionId, token, expected) {
  for (let attempt = 0; attempt < 100; attempt++) {
    const response = await request('/ai/v1/stream-sessions/' + sessionId, {}, token)
    assert.equal(response.status, 200)
    if (response.body.result.state === expected) return response.body.result
    if (['FAILED', 'UNKNOWN'].includes(response.body.result.state)) {
      throw new Error(`Stream entered ${response.body.result.state}`)
    }
    await new Promise(resolve => setTimeout(resolve, 250))
  }
  throw new Error(`Stream did not reach ${expected}: ${sessionId}`)
}

function prepareAccounts(tempDirectory) {
  const stamp = Date.now().toString(36)
  const accounts = [
    { name: 'owner', username: `rc_owner_${stamp}`, infer: true },
    { name: 'viewer', username: `rc_viewer_${stamp}`, infer: false }
  ].map(account => ({
    ...account,
    id: stableId(stamp + ':' + account.name),
    salt: crypto.randomBytes(4).toString('hex'),
    password: crypto.randomBytes(18).toString('base64url')
  }))
  const classes = path.join(tempDirectory, 'classes')
  fs.mkdirSync(classes)
  run('javac', ['-d', classes,
    path.join(root, 'apps/backend/jeecg-boot-base-core/src/main/java/org/jeecg/common/util/PasswordUtil.java'),
    path.join(root, 'remote-inference/acceptance/00-integration/round3/scripts/AccountPassword.java')])
  const hashes = run('java', ['-cp', classes, 'AccountPassword'], {
    input: accounts.map(account => [account.username, account.password, account.salt].join('\t')).join('\n') + '\n'
  }).trim().split('\n')
  assert.equal(hashes.length, accounts.length)

  const permissionId = stableId('permission:ai:infer')
  const statements = [
    `INSERT INTO sys_permission(id,name,menu_type,perms,status,del_flag,is_route) VALUES(${quote(permissionId)},'Final RC AI inference',2,'ai:infer','1',0,0) ON DUPLICATE KEY UPDATE perms=VALUES(perms)`
  ]
  accounts.forEach((account, index) => {
    const roleId = stableId(stamp + ':role:' + account.name)
    statements.push(`INSERT INTO sys_user(id,username,realname,password,salt,status,del_flag,user_identity,create_by,create_time) VALUES(${quote(account.id)},${quote(account.username)},${quote('Final RC ' + account.name)},${quote(hashes[index])},${quote(account.salt)},1,0,1,'final-rc',NOW()) ON DUPLICATE KEY UPDATE password=VALUES(password),salt=VALUES(salt)`)
    statements.push(`INSERT INTO sys_role(id,role_name,role_code,description) VALUES(${quote(roleId)},${quote('Final RC ' + account.name)},${quote('final_rc_' + stamp + '_' + account.name)},'Disposable local RC role') ON DUPLICATE KEY UPDATE description=VALUES(description)`)
    statements.push(`INSERT INTO sys_user_role(id,user_id,role_id) VALUES(${quote(stableId(stamp + ':user-role:' + account.name))},${quote(account.id)},${quote(roleId)}) ON DUPLICATE KEY UPDATE role_id=VALUES(role_id)`)
    if (account.infer) statements.push(`INSERT INTO sys_role_permission(id,role_id,permission_id) VALUES(${quote(stableId(stamp + ':infer'))},${quote(roleId)},${quote(permissionId)}) ON DUPLICATE KEY UPDATE permission_id=VALUES(permission_id)`)
  })
  sql('START TRANSACTION;\n' + statements.join(';\n') + ';\nCOMMIT;')
  return accounts
}

async function main() {
  const tempDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'fniao-final-rc-'))
  try {
    const accounts = prepareAccounts(tempDirectory)
    const ownerAccount = accounts.find(account => account.name === 'owner')
    const viewerAccount = accounts.find(account => account.name === 'viewer')
    const stubSeed = fs.readFileSync(path.join(root, 'database/seeds/stub/stub-bindings.example.sql'), 'utf8')
      .replaceAll('__OWNER_ID__', ownerAccount.id)
    sql(stubSeed)

    assert.equal((await request('/ai/v1/capabilities')).status, 401)
    const disabledOwnerToken = await login(ownerAccount)
    const disabledCapabilities = await request('/ai/v1/capabilities', {}, disabledOwnerToken)
    assert.equal(disabledCapabilities.status, 200)
    assert(disabledCapabilities.body.result.length >= 3)
    assert(disabledCapabilities.body.result.every(item => item.available === false))

    composeStub('build', 'remote-ai-stub')
    composeStub('up', '-d', 'remote-ai-stub')
    await waitHealthy('remote-ai-stub', true)
    sql(stubSeed)
    composeStub('up', '-d', '--force-recreate', 'backend')
    await waitHealthy('backend', true)

    const ownerToken = await login(ownerAccount)
    const viewerToken = await login(viewerAccount)
    assert.equal((await request('/ai/v1/capabilities')).status, 401)
    const viewerCapabilities = await request('/ai/v1/capabilities', {}, viewerToken)
    assert.equal(viewerCapabilities.status, 200)
    assert(viewerCapabilities.body.result.every(item => item.available === false))
    const viewerUpload = await upload(viewerToken, Buffer.from('permission-check'), 'image/png', 'forbidden.png')
    assert.equal(viewerUpload.status, 403)

    const capabilities = await request('/ai/v1/capabilities', {}, ownerToken)
    assert.equal(capabilities.status, 200)
    assert.deepEqual(capabilities.body.result.map(item => item.code), [
      'image-detection.v1', 'video-file-analysis.v1', 'video-stream-analysis.v1'
    ])
    assert(capabilities.body.result.every(item => item.available && item.simulated))

    const prefix = 'final-local-rc-' + Date.now()
    const imageBytes = fs.readFileSync(path.join(root, 'remote-inference/fixtures/input.png'))
    const imageUpload = await upload(ownerToken, imageBytes, 'image/png', 'simulated-input.png')
    assert.equal(imageUpload.status, 201)
    const imageBody = {
      capabilityCode: 'image-detection.v1',
      inputAssetId: imageUpload.body.result.assetId,
      parameters: { threshold: 0.5, maxDetections: 10, annotate: true }
    }
    const imageSubmit = await request('/ai/v1/infer?waitMillis=1500', {
      ...jsonPost(imageBody),
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': prefix + '-image' }
    }, ownerToken)
    assert([200, 202].includes(imageSubmit.status))
    const imageJob = imageSubmit.status === 200 ? imageSubmit.body.result
      : await terminalJob(imageSubmit.body.result.requestId, ownerToken)
    assert.equal(imageJob.state, 'SUCCEEDED')
    assert.equal(imageJob.simulated, true)
    assert.equal(imageJob.result.simulated, true)
    assert(imageJob.result.data.detections.length > 0)

    const videoBytes = Buffer.from('000000186674797069736f6d0000020069736f6d69736f3261766331', 'hex')
    const videoUpload = await upload(ownerToken, videoBytes, 'video/mp4', 'simulated-demo.mp4')
    assert.equal(videoUpload.status, 201)
    const videoSubmit = await request('/ai/v1/video-jobs', {
      ...jsonPost({
        capabilityCode: 'video-file-analysis.v1',
        inputAssetId: videoUpload.body.result.assetId,
        parameters: { threshold: 0.5, sampleIntervalMillis: 1000, maxEvents: 20, includeSnapshots: true, annotate: false }
      }),
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': prefix + '-video' }
    }, ownerToken)
    assert([200, 202].includes(videoSubmit.status))
    const videoJob = videoSubmit.status === 200 ? videoSubmit.body.result
      : await terminalJob(videoSubmit.body.result.requestId, ownerToken)
    assert.equal(videoJob.state, 'SUCCEEDED')
    assert.equal(videoJob.simulated, true)
    assert.equal(videoJob.videoResult.simulated, true)
    assert(videoJob.videoResult.events.length > 0)
    assert(videoJob.videoResult.snapshots.length > 0)

    const downloaded = []
    for (const artifact of [...imageJob.result.artifacts, ...videoJob.videoResult.snapshots]) {
      const response = await request('/ai/v1/assets/' + artifact.assetId + '/content', {}, ownerToken)
      assert.equal(response.status, 200)
      assert.equal(response.body.length, artifact.sizeBytes)
      const sha256 = crypto.createHash('sha256').update(response.body).digest('hex')
      assert.equal(sha256, artifact.sha256)
      downloaded.push({ assetId: artifact.assetId, sizeBytes: artifact.sizeBytes, sha256 })
    }

    const sources = await request('/ai/v1/stream-sources', {}, ownerToken)
    assert.equal(sources.status, 200)
    assert(sources.body.result.some(item => item.streamSourceId === 'stub-source-01' && item.available))
    const streamBody = {
      capabilityCode: 'video-stream-analysis.v1',
      streamSourceId: 'stub-source-01',
      parameters: { maxEventsPerPoll: 50, pollIntervalMillis: 500, includeSnapshots: true }
    }
    const streamSubmit = await request('/ai/v1/stream-sessions', {
      ...jsonPost(streamBody),
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': prefix + '-stream' }
    }, ownerToken)
    assert([200, 202].includes(streamSubmit.status))
    const streamId = streamSubmit.body.result.sessionId
    const running = await sessionState(streamId, ownerToken, 'RUNNING')
    let events
    for (let attempt = 0; attempt < 50; attempt++) {
      events = await request(`/ai/v1/stream-sessions/${streamId}/events?limit=50`, {}, ownerToken)
      assert.equal(events.status, 200)
      if (events.body.result.items.length) break
      await new Promise(resolve => setTimeout(resolve, 250))
    }
    assert(events.body.result.items.length > 0)
    const stop = await request(`/ai/v1/stream-sessions/${streamId}/stop`, { method: 'POST' }, ownerToken)
    assert([200, 202].includes(stop.status))
    const stopped = await sessionState(streamId, ownerToken, 'STOPPED')

    const history = await request('/ai/v1/jobs?limit=100', {}, ownerToken)
    assert.equal(history.status, 200)
    assert(history.body.result.items.some(item => item.requestId === imageJob.requestId))
    assert(history.body.result.items.some(item => item.requestId === videoJob.requestId))
    for (const legacyUrl of ['/tab/tabAiHistory/list', '/tab/tabAiSubscription/list', '/tab/tabAiBase/list']) {
      const legacy = await request(legacyUrl + '?pageNo=1&pageSize=1', {}, ownerToken)
      assert.equal(legacy.status, 200, `Historical read failed: ${legacyUrl}`)
    }

    const jobsBeforeDisabled = Number(sql('SELECT COUNT(*) FROM ai_job').trim())
    composeStub('stop', 'remote-ai-stub')
    compose('up', '-d', '--force-recreate', 'backend')
    await waitHealthy('backend')
    const finalToken = await login(ownerAccount)
    const finalCapabilities = await request('/ai/v1/capabilities', {}, finalToken)
    assert.equal(finalCapabilities.status, 200)
    assert(finalCapabilities.body.result.every(item => item.available === false))
    const imageDetail = await request('/ai/v1/jobs/' + imageJob.requestId, {}, finalToken)
    assert.equal(imageDetail.status, 200)
    assert.equal(imageDetail.body.result.state, 'SUCCEEDED')
    const historicalDownload = await request('/ai/v1/assets/' + downloaded[0].assetId + '/content', {}, finalToken)
    assert.equal(historicalDownload.status, 200)
    const rejected = await request('/ai/v1/infer', {
      ...jsonPost(imageBody),
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': prefix + '-disabled' }
    }, finalToken)
    assert.equal(rejected.status, 409)
    assert.equal(Number(sql('SELECT COUNT(*) FROM ai_job').trim()), jobsBeforeDisabled)
    assert.equal(composeStub('ps', '-q', 'remote-ai-stub').trim(), '')

    const report = {
      status: 'PASS',
      proofScope: ['simulated', 'disabled'],
      realProviderValidated: false,
      authentication: { passwordCaptchaLogin: true, anonymousStatus: 401 },
      authorization: { ownerPermitted: true, viewerSubmissionStatus: viewerUpload.status },
      capabilities: capabilities.body.result.map(item => ({ code: item.code, simulated: item.simulated })),
      image: { state: imageJob.state, detections: imageJob.result.data.detections.length },
      video: { state: videoJob.state, events: videoJob.videoResult.events.length, snapshots: videoJob.videoResult.snapshots.length },
      stream: { stateBeforeStop: running.state, stateAfterStop: stopped.state, events: events.body.result.items.length },
      artifactsVerified: downloaded.length,
      historicalReads: 5,
      disabled: { capabilitiesAvailable: 0, newSubmissionStatus: rejected.status, historyReadable: true },
      requests: receipts.length
    }
    console.log(JSON.stringify(report, null, 2))
  } finally {
    fs.rmSync(tempDirectory, { recursive: true, force: true })
  }
}

main().catch(error => {
  console.error(error.stack || error)
  process.exitCode = 1
})
