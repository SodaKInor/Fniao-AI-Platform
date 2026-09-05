const fs = require('node:fs')
const path = require('node:path')
const assert = require('node:assert/strict')
const { work, evidence, run, sql } = require('./runtime.cjs')
const { request, login, jsonPost, save } = require('./http.cjs')
const accepted = JSON.parse(fs.readFileSync(path.join(evidence, 'api-e2e.json')))
const standard = path.join(work, 'compose-acceptance.json')
const temporary = path.join(work, 'compose-mode-check.json')
async function healthy() {
  for (let n = 0; n < 60; n++) {
    const state = JSON.parse(run('docker', ['inspect', 'wgai-ri-00-integration-backend-1']))[0].State
    if (state.Health && state.Health.Status === 'healthy') return
    if (!state.Running) throw new Error('Full application startup failed')
    await new Promise(resolve => setTimeout(resolve, 1000))
  }
  throw new Error('Full application health timeout')
}
async function main() {
  const before = Number(sql('SELECT COUNT(*) FROM ai_job'))
  try {
    for (const mode of ['disabled', 'remote']) {
      const config = JSON.parse(fs.readFileSync(standard))
      const values = JSON.parse(config.services.backend.environment.SPRING_APPLICATION_JSON)
      values.wgai.inference.mode = mode
      config.services.backend.environment.SPRING_APPLICATION_JSON = JSON.stringify(values)
      fs.writeFileSync(temporary, JSON.stringify(config, null, 2), { mode: 0o600 })
      run('docker', ['compose', '-f', temporary, 'up', '-d', '--no-deps', 'backend'], { stdio: ['pipe', 'pipe', 'pipe'] })
      await healthy()
      const token = await login('owner_a')
      const capabilities = await request('/ai/v1/capabilities', {}, token)
      assert.equal(capabilities.body.result[0].available, false)
      const detail = (await request('/ai/v1/jobs/' + accepted.jobs.delayed, {}, token)).body.result
      assert.equal(detail.state, 'SUCCEEDED')
      assert.equal((await request('/ai/v1/jobs', {}, token)).status, 200)
      assert.equal((await request('/ai/v1/assets/' + accepted.file.assetId + '/content', {}, token)).body.length, accepted.file.bytes)
      const body = { capabilityCode: detail.capabilityCode, inputAssetId: detail.inputAssetId, parameters: detail.parameters }
      assert.equal((await request('/ai/v1/infer', { ...jsonPost(body),
        headers: { 'Content-Type': 'application/json', 'Idempotency-Key': 'mode-' + mode + '-' + Date.now() } }, token)).status, 409)
      assert.equal(Number(sql('SELECT COUNT(*) FROM ai_job')), before)
    }
    save('runtime-modes', { modes: ['disabled', 'unconfirmed remote'], unchangedJobCount: before,
      conclusion: 'Core healthy; no new persistence/dispatch; own historical results readable with real login in both modes.' })
  } finally {
    run('docker', ['compose', '-f', standard, 'up', '-d', '--no-deps', 'backend'], { stdio: ['pipe', 'pipe', 'pipe'] })
    await healthy()
  }
  console.log('PASS: full backend disabled and unconfirmed remote fail closed; history preserved; mock restored')
}
main().catch(error => { console.error(error); process.exitCode = 1 })
