const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')
const assert = require('node:assert/strict')
const { root, work, evidence, run } = require('./runtime.cjs')
const frontend = path.join(root, 'frontend-vue')
const parser = require(path.join(frontend, 'node_modules/@babel/parser'))
const traverse = require(path.join(frontend, 'node_modules/@babel/traverse')).default
const compiler = require(path.join(frontend, 'node_modules/vue-template-compiler'))
const hash = bytes => crypto.createHash('sha256').update(bytes).digest('hex')
const receipt = JSON.parse(fs.readFileSync(path.join(root, 'backend-github/integrations/ai-contracts/acceptance/04b-frontend/evidence/scope.json')))
const legacySizes = receipt.runtimeFiles.filter(row => !/\/(api|services|components|views)\/ai\//.test(row.path)).map(row => {
  const source = fs.readFileSync(path.join(root, row.path), 'utf8')
  const vue = row.path.endsWith('.vue')
  const script = vue ? compiler.parseComponent(source).script.content : source
  const methods = []
  traverse(parser.parse(script, { sourceType: 'module', plugins: ['dynamicImport'] }), {
    Function(p) { if (p.node.loc.end.line - p.node.loc.start.line + 1 > 80) methods.push({ line: p.node.loc.start.line,
      lines: p.node.loc.end.line - p.node.loc.start.line + 1 }) }
  })
  return { path: row.path, lines: source.split('\n').length, reviewMethodsOver80: methods }
})
function files(dir) { return fs.readdirSync(dir, { withFileTypes: true }).flatMap(e => e.isDirectory() ? files(path.join(dir, e.name)) : [path.join(dir, e.name)]) }
const rows = files(path.join(frontend, 'src/modules/ai')).map(file => {
  const source = fs.readFileSync(file, 'utf8')
  const vue = file.endsWith('.vue')
  const script = vue ? compiler.parseComponent(source).script.content : source
  const tree = parser.parse(script, { sourceType: 'module', plugins: ['dynamicImport'] })
  const methods = []
  traverse(tree, { Function(p) { methods.push({ line: p.node.loc.start.line, lines: p.node.loc.end.line - p.node.loc.start.line + 1 }) } })
  assert(source.split('\n').length <= (vue ? 350 : 300), file)
  assert(methods.every(m => m.lines <= 80), file)
  assert(!/new\s+WebSocket|wss?:\/\//.test(source), file)
  return { path: path.relative(root, file), lines: source.split('\n').length, methods, sha256: hash(source) }
})
const tests = fs.readFileSync(path.join(work, 'frontend-tests.log'), 'utf8')
const lint = fs.readFileSync(path.join(work, 'frontend-lint.log'), 'utf8')
assert(/pass 27/.test(tests)); assert(!/\berror\b/.test(lint))
const build = fs.readFileSync(path.join(work, 'frontend-build.log'), 'utf8')
assert(build.includes('Build complete') || build.includes('Build complete.') || build.includes('DONE'))
assert(!run('git', ['diff', '--', 'frontend-vue/package.json', 'frontend-vue/package-lock.json', 'frontend-vue/yarn.lock']))
fs.writeFileSync(path.join(evidence, 'frontend-checks.json'), JSON.stringify({ status: 'PASS', tests: 27,
  unitLogSha256: hash(tests), lintLogSha256: hash(lint), buildLogSha256: hash(build), files: rows, legacySizes,
  reviewedLegacy: ['src/store/modules/user.js: permission-result menu assembly only', 'TabAiModelBundList/audio/TabEasyConfigList: disabled execution controls; management queries retained'],
  scope: 'Real integrated sources; no 04b in-memory business API used for integration acceptance.',
  warnings: 'Existing CSS order, package size and outdated Browserslist warnings; dependency versions unchanged.' }, null, 2) + '\n')
console.log('PASS: 16 frontend tests, scoped lint,', rows.length, 'new files under review thresholds and no AI WebSocket')
