const fs = require('node:fs')
const path = require('node:path')
const assert = require('node:assert/strict')
const { root, sql } = require('../../00-integration/round3/scripts/runtime.cjs')
const { request, login } = require('../../00-integration/round3/scripts/http.cjs')

const output = path.resolve(__dirname, '..', 'group3-direct-retirement.actual.json')
const tables = [
  'pic_config', 'tab_ai_base', 'tab_ai_history', 'tab_ai_model',
  'tab_ai_model_bund', 'tab_ai_subscription'
]
const retired = [
  { method: 'POST', url: '/tab/tabAiHistory/addIdentify', body: '{}' },
  { method: 'GET', url: '/tab/tabAiHistory/addAudio?path=retired.wav' },
  { method: 'POST', url: '/tab/tabAiHistory/addIdentifyClose', body: '{}' },
  { method: 'POST', url: '/video/tabVideoUtil/startVideoUtil', body: '{}' },
  { method: 'POST', url: '/video/tabVideoUtil/stopVideoUtil', body: '{}' },
  { method: 'GET', url: '/tab/tabAiSubscription/subInfo' }
]

function tableCounts() {
  const query = tables.map(name => `SELECT '${name}',COUNT(*) FROM ${name};`).join('\n')
  return Object.fromEntries(sql(query).trim().split('\n').map(line => {
    const [name, count] = line.split('\t')
    return [name, Number(count)]
  }))
}

function options(route) {
  if (route.method === 'GET') return {}
  return { method: route.method, headers: { 'Content-Type': 'application/json' }, body: route.body }
}

async function main() {
  assert.equal(process.cwd(), root)
  const before = tableCounts()
  const token = await login('owner_a')
  const results = []
  for (const route of retired) {
    const anonymous = await request(route.url, options(route))
    const authorized = await request(route.url, options(route), token)
    assert.equal(anonymous.status, 401, route.url)
    assert.equal(authorized.status, 409, route.url)
    assert.equal(authorized.body.result.errorCode, 'CAPABILITY_UNAVAILABLE', route.url)
    results.push({ method: route.method, url: route.url, anonymous: 401, authorized: 409 })
  }
  const after = tableCounts()
  assert.deepEqual(after, before, 'Retiring execution paths must not rewrite retained tables')
  const report = {
    status: 'PASS',
    simulated: true,
    realProviderValidated: false,
    retiredRoutes: results,
    retainedTableRowsBefore: before,
    retainedTableRowsAfter: after,
    note: 'Old execution routes are rejected for direct calls; management and history rows remain unchanged.'
  }
  fs.writeFileSync(output, JSON.stringify(report, null, 2) + '\n')
  console.log(JSON.stringify(report, null, 2))
}

main().catch(error => { console.error(error.stack || error); process.exitCode = 1 })
