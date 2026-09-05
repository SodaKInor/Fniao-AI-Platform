const { root, evidence, sql } = require('./runtime.cjs')
const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')
const assert = require('node:assert/strict')
const digest = data => crypto.createHash('sha256').update(data).digest('hex')
const tables = ['ai_asset', 'ai_job', 'ai_capability_binding', 'ai_job_event', 'ai_job_capacity']
function inventory() {
  return Object.fromEntries(sql('SHOW TABLES').trim().split('\n').filter(t => !tables.includes(t)).map(t => {
    assert(/^\w+$/.test(t))
    const rows = sql('SELECT * FROM ' + t).trimEnd().split('\n').sort()
    return [t, { schema: digest(sql('SHOW CREATE TABLE ' + t)), rows: digest(rows.join('\n')), count: rows.length }]
  }))
}
const before = inventory()
const ddl = fs.readFileSync(path.join(root, 'backend-github/deploy/remote-ai/migrations/V001__04a_assets_jobs.sql'), 'utf8')
const scratch = 'ai_00_verify_' + crypto.randomBytes(8).toString('hex')
sql('CREATE DATABASE ' + scratch + ' CHARACTER SET utf8mb4')
try {
  sql(ddl, scratch)
  const expected = Object.fromEntries(tables.map(t => [t, sql('SHOW CREATE TABLE ' + t, scratch)]))
  const existing = sql('SHOW TABLES').trim().split('\n')
  for (const t of tables.filter(t => existing.includes(t))) assert.equal(sql('SHOW CREATE TABLE ' + t), expected[t])
  for (let n = 0; n < 2; n++) {
    sql(ddl)
    for (const t of tables) assert.equal(sql('SHOW CREATE TABLE ' + t), expected[t])
    assert.deepEqual(inventory(), before)
  }
  const result = { status: 'PASS', historicalTables: before, executions: 2, migrationSha256: digest(ddl), oldDataUnchanged: true }
  fs.writeFileSync(path.join(evidence, 'migration.json'), JSON.stringify(result, null, 2) + '\n')
  console.log('Migration verified twice; ' + Object.keys(before).length + ' historical tables unchanged.')
} finally { sql('DROP DATABASE ' + scratch) }
