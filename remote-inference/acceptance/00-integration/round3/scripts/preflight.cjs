const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')
const { execFileSync } = require('node:child_process')
const assert = require('node:assert/strict')
const root = path.resolve(__dirname, '../../../../../../..')
const base = path.resolve(root, '../..')
const git = (cwd, ...args) => execFileSync('git', args, { cwd, encoding: 'utf8' }).trim()
const sha = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex')
const start = 'ab9809d23919ea5d61dfc7d8b34d7f30bb9d607c'
const contract = '5a55ca5cc6ea8fde09898f44519d62c715af12db'
const ai = 'backend-github/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/ai/'
const acceptance = 'backend-github/integrations/ai-contracts/acceptance/'
assert.equal(git(root, 'rev-parse', '--show-toplevel'), root)
assert.equal(git(root, 'branch', '--show-current'), 'feature/remote-inference')
const deliveries = { '03-client': '2e74b32c438895895249f0da22ff37c591153d74',
  '04a-assets-jobs': '561c9dfd1a479dad4bef9ea1854fbd5cf1bc95b7',
  '04b-frontend': 'ce5671aac656077fc78b3877608a16b1173315b1' }
const report = { at: new Date().toISOString(), root, start, contract, packages: [] }
for (const [id, commit] of Object.entries(deliveries)) {
  const cwd = path.join(base, id, 'code')
  assert.equal(git(cwd, 'rev-parse', 'HEAD'), commit)
  assert.equal(git(cwd, 'status', '--porcelain'), '')
  assert.equal(git(cwd, 'branch', '--show-current'), 'work/remote-inference/' + id)
  assert.equal(git(cwd, 'merge-base', commit, start), start)
  const frozen = [ai + 'domain', ai + 'port', ai + 'api/dto',
    'backend-github/integrations/ai-contracts/v1', 'backend-github/integrations/ai-contracts/provider-draft',
    'backend-github/integrations/ai-contracts/examples']
  assert.equal(git(cwd, 'diff', '--name-only', contract, commit, '--', ...frozen), '')
  assert.equal(git(cwd, 'diff', '--name-only', start, commit, '--', 'openspec'), '')
  const files = git(cwd, 'diff', '--name-only', start, commit).split('\n')
  assert(files.every(p => !/(^backend-master\/|(^|\/)(pom.xml|package.json|package-lock.json|yarn.lock)$)/.test(p)))
  const read = file => JSON.parse(fs.readFileSync(path.join(cwd, acceptance, id, file)))
  const hashes = id === '03-client' ? Object.entries(read('java8-tests.json').source_sha256)
    : (id === '04a-assets-jobs' ? read('layer-checks.json').files : read('evidence/scope.json').runtimeFiles)
      .map(f => [f.path, f.sha256])
  for (const [file, expected] of hashes) assert.equal(sha(path.join(cwd, file)), expected, file)
  report.packages.push({ id, commit, clean: true, contractUnchanged: true, hashesVerified: hashes.length, files })
}
const seen = new Set()
for (const item of report.packages) for (const file of item.files) { assert(!seen.has(file), file); seen.add(file) }
report.status = 'PASS'
report.limitation = 'Package evidence verified; combined runtime acceptance still required.'
fs.writeFileSync(path.resolve(__dirname, '../preflight.json'), JSON.stringify(report, null, 2) + '\n')
console.log(JSON.stringify({ status: report.status, packages: report.packages.map(({ id, commit, hashesVerified }) => ({ id, commit, hashesVerified })) }))
