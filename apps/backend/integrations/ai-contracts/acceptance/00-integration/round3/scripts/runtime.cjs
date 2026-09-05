const fs = require('node:fs')
const path = require('node:path')
const { execFileSync } = require('node:child_process')
const assert = require('node:assert/strict')
const root = path.resolve(__dirname, '../../../../../../..')
const work = path.resolve(root, '../drafts/round3')
const evidence = path.resolve(__dirname, '..')
const profile = path.join(work, 'compose.json')
const database = 'wgai_ri_00_integration'
const mysql = 'wgai-ri-00-integration-mysql-1'
const run = (cmd, args, opts = {}) => execFileSync(cmd, args, { cwd: root, encoding: 'utf8', ...opts })
function prepare() {
  assert.equal(run('git', ['rev-parse', '--show-toplevel']).trim(), root)
  const original = path.resolve(root, '../../01-foundation/drafts/runtimes/00-integration/compose.json')
  const config = JSON.parse(fs.readFileSync(original))
  assert.equal(config.name, 'wgai-ri-00-integration')
  assert.equal(config.services.mysql.environment.MYSQL_DATABASE, database)
  const backend = config.services.backend
  backend.image = 'wgai-integration-backend:round3'
  config.services.frontend.image = 'wgai-integration-frontend:round3'
  const values = JSON.parse(backend.environment.SPRING_APPLICATION_JSON)
  values.wgai = { inference: { mode: 'mock', 'provider-key': 'mock' },
    ai: { jobs: { 'private-root': '/data/ai-private' } } }
  backend.environment.SPRING_APPLICATION_JSON = JSON.stringify(values)
  backend.volumes.push('ai_private:/data/ai-private')
  config.volumes.ai_private = { name: 'wgai-ri-00-integration_ai_private',
    labels: { 'wgai.foundation.package': '00-integration', 'wgai.integration.purpose': 'round3-private-assets' } }
  for (const volume of Object.values(config.volumes)) assert(volume.name.startsWith('wgai-ri-00-integration_'))
  fs.mkdirSync(work, { recursive: true, mode: 0o700 })
  fs.writeFileSync(profile, JSON.stringify(config, null, 2), { mode: 0o600 })
  const secrets = fs.readFileSync(path.join(path.dirname(original), '.env'), 'utf8')
  fs.writeFileSync(path.join(work, '.env'), secrets, { mode: 0o600 })
  const passwordLine = secrets.split('\n').find(line => line.startsWith('MYSQL_PASSWORD='))
  assert(passwordLine)
  fs.writeFileSync(path.join(work, 'tests.env'), passwordLine + '\n', { mode: 0o600 })
  console.log('Prepared isolated 00 runtime; secrets remain in private drafts.')
}
function sql(statement, db = database) {
  assert(db === database || /^ai_00_verify_[a-f0-9]+$/.test(db))
  const mounts = JSON.parse(run('docker', ['inspect', mysql]))[0].Mounts
  assert(mounts.some(m => m.Name === 'wgai-ri-00-integration_mysql_data'))
  return run('docker', ['exec', '-i', mysql, 'sh', '-c',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --batch --skip-column-names ' + db], { input: statement })
}
function compose(...args) { return run('docker', ['compose', '-f', profile, ...args]) }
module.exports = { root, work, evidence, profile, database, mysql, run, sql, compose }
if (require.main === module) {
  if (process.argv[2] === 'prepare') prepare()
  else throw new Error('Use prepare; lifecycle operations require explicit compose targets.')
}
