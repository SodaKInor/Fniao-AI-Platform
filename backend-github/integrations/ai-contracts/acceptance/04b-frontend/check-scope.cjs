// Read-only audit against the assigned shared baseline; no dependencies required.
const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')
const { execFileSync } = require('node:child_process')
const root = path.resolve(__dirname, '../../../../..')
const baseline = 'ab9809d23919ea5d61dfc7d8b34d7f30bb9d607c'
const contract = '5a55ca5cc6ea8fde09898f44519d62c715af12db'
const acceptance = 'backend-github/integrations/ai-contracts/acceptance/04b-frontend/'
const areas = ['api/ai/', 'components/ai/', 'services/ai/', 'views/ai/'].map(p => 'frontend-vue/src/' + p)
const existing = ['store/modules/user.js', 'views/tab/TabAiModelBundList.vue',
  'views/video/TabAiModelBundList.vue', 'views/audio/audio.vue',
  'views/tab/live/audio.vue', 'views/teasy/TabEasyConfigList.vue'].map(p => 'frontend-vue/src/' + p)
const git = (...args) => execFileSync('git', args, { cwd: root, encoding: 'utf8' }).trim()
const lines = text => text ? text.split('\n') : []
const changed = [...new Set([...lines(git('diff', '--name-only', baseline)),
  ...lines(git('ls-files', '--others', '--exclude-standard'))])].sort()
const disallowed = changed.filter(p => !p.startsWith(acceptance) && !areas.some(a => p.startsWith(a)) && !existing.includes(p))
const files = changed.filter(p => areas.some(a => p.startsWith(a)) || existing.includes(p)).map(p => {
  const bytes = fs.readFileSync(path.join(root, p))
  const size = bytes.toString().trimEnd().split('\n').length
  return { path: p, lines: size, sha256: crypto.createHash('sha256').update(bytes).digest('hex') }
})
const oversized = files.filter(f => !existing.includes(f.path) && f.lines > (f.path.endsWith('.vue') ? 250 : 200))
const frozenChanges = lines(git('diff', '--name-only', baseline, '--',
  'frontend-vue/package.json', 'frontend-vue/package-lock.json', 'frontend-vue/yarn.lock',
  'backend-github/integrations/ai-contracts/openapi', 'backend-github/integrations/ai-contracts/examples'))
execFileSync('git', ['merge-base', '--is-ancestor', contract, 'HEAD'], { cwd: root })
execFileSync('git', ['diff', '--check', baseline], { cwd: root })
const passed = !disallowed.length && !oversized.length && !frozenChanges.length
console.log(JSON.stringify({ at: new Date().toISOString(), baseline, contract,
  branch: git('branch', '--show-current'), node: process.version, passed,
  disallowed, oversized, frozenChanges, runtimeFiles: files }, null, 2))
if (!passed) process.exitCode = 1
