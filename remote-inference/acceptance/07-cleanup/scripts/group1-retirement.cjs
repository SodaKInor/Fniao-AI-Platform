const fs = require('node:fs')
const path = require('node:path')
const assert = require('node:assert/strict')
const { root, sql } = require('../../00-integration/round3/scripts/runtime.cjs')
const { request, login } = require('../../00-integration/round3/scripts/http.cjs')
const { loadSource } = require('../../04b-frontend/tests/load-source.cjs')

const output = path.resolve(__dirname, '..', 'group1-retirement.actual.json')
const { prepareAiMenus } = loadSource('modules/ai/legacy/navigation.js')
const flatten = rows => rows.flatMap(row => [row, ...flatten(row.children || [])])
const retired = /^(maxkb|tchat|teasy)\//

function tableCounts() {
  const names = [
    'pic_config', 'tab_chat_keyword', 'tab_chat_qa', 'tab_chat_type',
    'tab_easy_config', 'tab_easy_pic', 'tab_easy_type', 'tab_maxkb_model',
    'tab_message_list', 'tab_message_train_log', 'tab_message_train_model',
    'tab_message_type'
  ]
  const query = names.map(name =>
    `SELECT '${name}',COUNT(*) FROM ${name};`).join('\n')
  return Object.fromEntries(sql(query).trim().split('\n').map(line => {
    const [name, count] = line.split('\t')
    return [name, Number(count)]
  }))
}

async function main() {
  assert.equal(process.cwd(), root)
  const before = tableCounts()
  const token = await login('owner_a')
  const permission = await request('/sys/permission/getUserPermissionByToken', {}, token)
  assert.equal(permission.status, 200)
  assert.equal(permission.body.success, true)

  const raw = flatten(permission.body.result.menu || [])
  const rawRetired = raw.filter(item => retired.test((item.component || '').replace(/^\//, '')))
  const prepared = flatten(prepareAiMenus(permission.body.result.menu || []))
  const leaked = prepared.filter(item => retired.test((item.component || '').replace(/^\//, '')))
  assert.equal(leaked.length, 0, 'Retired components must not reach dynamic imports')
  for (const item of rawRetired) {
    const resolved = prepared.find(candidate => candidate.id === item.id)
    assert(resolved)
    assert.equal(resolved.component, 'modules/ai/legacy/DisabledEntryPage')
  }

  const anonymous = await request('/maxkb/tabMaxkbModel/testConnect', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}'
  })
  const authorized = await request('/maxkb/tabMaxkbModel/testConnect', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}'
  }, token)
  assert.equal(anonymous.status, 401)
  assert.equal(authorized.status, 409)
  assert.equal(authorized.body.result.errorCode, 'CAPABILITY_UNAVAILABLE')

  const after = tableCounts()
  assert.deepEqual(after, before, 'Retiring UI and execution bindings must not rewrite legacy history')
  const report = {
    status: 'PASS',
    simulated: true,
    realProviderValidated: false,
    rawRetiredMenuEntries: rawRetired.length,
    retiredComponentsAfterTransform: leaked.length,
    directExecution: { anonymous: anonymous.status, authorized: authorized.status },
    legacyTableRowsBefore: before,
    legacyTableRowsAfter: after,
    note: 'Management tables and rows are retained; only retired UI and the MaxKB outbound test action are disabled.'
  }
  fs.writeFileSync(output, JSON.stringify(report, null, 2) + '\n')
  console.log(JSON.stringify(report, null, 2))
}

main().catch(error => { console.error(error.stack || error); process.exitCode = 1 })
