const fs = require('node:fs')
const path = require('node:path')
const assert = require('node:assert/strict')
const crypto = require('node:crypto')
const { root, work, evidence, run } = require('./runtime.cjs')
const original = '/Users/twowt88/Documents/ChatGPT/WGAI'
const git = (cwd, ...args) => run('git', ['-C', cwd, ...args]).trim()
assert.equal(git(original, 'rev-parse', 'HEAD'), 'e1ccab10fe5314c67be811c47cdbc3663e8a4b53')
assert.equal(git(original, 'status', '--porcelain'), '')
assert.equal(git(root, 'branch', '--show-current'), 'feature/remote-inference')
assert.equal(git(root, 'diff', '--check'), '')
const tasks = fs.readFileSync(path.join(root, 'openspec/changes/remote-inference-platform/tasks.md'), 'utf8')
assert.equal((tasks.match(/^- \[x\]/gm) || []).length, 23)
assert(tasks.includes('- [x] 3.4 ') && tasks.includes('- [x] 4.7 '))
const gates = ['preflight', 'migration', 'java-tests', 'frontend-checks', 'contract-checks', 'architecture',
  'spring-boot-smoke', 'api-e2e', 'runtime-modes', 'safety-e2e', 'menu-e2e', 'live-contracts', 'artifacts', 'browser-e2e']
for (const name of gates) assert.equal(JSON.parse(fs.readFileSync(path.join(evidence, name + '.json'))).status, 'PASS', name)
const dependencies = ['05-lan', '06-resilience', '07-cleanup', '08-release'].map(id => {
  const dir = path.resolve(root, '../../', id, 'code')
  assert.equal(git(dir, 'status', '--porcelain'), '')
  return { id, head: git(dir, 'rev-parse', 'HEAD'), clean: true, released: false }
})
const services = ['wgai-ri-00-integration-backend-1', 'wgai-ri-00-integration-frontend-1',
  'wgai-ri-00-integration-mysql-1', 'wgai-ri-00-integration-redis-1',
  'wgai-backend-1', 'wgai-frontend-1', 'wgai-mysql-1', 'wgai-redis-1'].map(name => {
  const info = JSON.parse(run('docker', ['inspect', name]))[0]
  assert.equal(info.State.Health.Status, 'healthy', name)
  if (name.startsWith('wgai-ri-00-')) {
    for (const bindings of Object.values(info.HostConfig.PortBindings || {}))
      for (const binding of bindings || []) assert.equal(binding.HostIp, '127.0.0.1')
    if (name.includes('backend')) assert(!info.Config.Entrypoint.join(' ').includes('PropertiesLauncher'))
  }
  return { name, healthy: true, imageId: info.Image, startedAt: info.State.StartedAt,
    ports: info.HostConfig.PortBindings,
    volumes: info.Mounts.filter(m => m.Type === 'volume').map(m => ({ name: m.Name, destination: m.Destination })) }
})
const accountsFile = path.join(work, 'accounts.private.json')
assert.equal(fs.statSync(accountsFile).mode & 0o777, 0o600)
const accounts = JSON.parse(fs.readFileSync(accountsFile))
function files(dir) { return fs.readdirSync(dir, { withFileTypes: true }).flatMap(e => e.isDirectory() ? files(path.join(dir, e.name)) : [path.join(dir, e.name)]) }
const manifest = {}
for (const file of files(evidence).filter(p => !p.endsWith('/final-checks.json'))) {
  const bytes = fs.readFileSync(file)
  for (const account of accounts) assert(!bytes.includes(Buffer.from(account.password)), 'Credential found in evidence')
  manifest[path.relative(evidence, file)] = crypto.createHash('sha256').update(bytes).digest('hex')
}
fs.writeFileSync(path.join(evidence, 'final-checks.json'), JSON.stringify({ status: 'PASS',
  passedTasks: 23, totalTasks: 41, pendingIntegration: [],
  releaseBoundary: 'round4-mock-gate',
  gatesPassed: gates, dependencies, services, evidenceSha256: manifest,
  limits: ['No real provider protocol approval', 'No production deployment/push/archive', '05/06/07/08 remain gated by their own prerequisites'] }, null, 2) + '\n')
console.log('PASS: 23/41 including real browser acceptance; round4 mock gate released, later packages remain gated')
