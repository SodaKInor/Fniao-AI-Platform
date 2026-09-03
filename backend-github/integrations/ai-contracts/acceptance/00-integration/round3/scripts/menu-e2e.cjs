const fs = require('node:fs')
const path = require('node:path')
const assert = require('node:assert/strict')
const { root, evidence } = require('./runtime.cjs')
const { request, login } = require('./http.cjs')
const { loadSource } = require(path.join(root, 'backend-github/integrations/ai-contracts/acceptance/04b-frontend/tests/load-source.cjs'))
const { prepareAiMenus } = loadSource('services/ai/navigation.js')
const flatten = rows => rows.flatMap(r => [r, ...flatten(r.children || [])])
async function main() {
  const rows = []
  for (const name of ['owner_a', 'owner_b', 'viewer', 'nomenu']) {
    const token = await login(name)
    const response = await request('/sys/permission/getUserPermissionByToken', {}, token)
    assert.equal(response.body.success, true)
    const menus = prepareAiMenus(response.body.result.menu)
    const actual = flatten(menus)
    const aiMenu = actual.some(m => m.path === '/ai/inference')
    assert.equal(aiMenu, name !== 'nomenu')
    if (name !== 'nomenu') assert(actual.some(m => m.component === 'ai/DisabledEntryPage'))
    const capabilities = (await request('/ai/v1/capabilities', {}, token)).body.result
    assert.equal(capabilities.some(c => c.available), name.startsWith('owner'))
    rows.push({ account: name, aiMenu, canInfer: capabilities.some(c => c.available),
      menuPaths: actual.map(m => ({ path: m.path, component: m.component })) })
  }
  fs.writeFileSync(path.join(evidence, 'menu-e2e.json'), JSON.stringify({ status: 'PASS', rows,
    scope: 'Real Shiro permission responses processed by actual frontend navigation module; not a browser UI test.' }, null, 2) + '\n')
  console.log('PASS: real four-account permissions and frontend menu transformation; browser joint acceptance still pending')
}
main().catch(error => { console.error(error); process.exitCode = 1 })
