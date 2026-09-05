const { root, sql } = require('../../round3/scripts/runtime.cjs')
const assert = require('node:assert/strict')
const crypto = require('node:crypto')
const fs = require('node:fs')
const path = require('node:path')

const output = path.resolve(__dirname, '..', 'migration.json')
const expectedTables = [
  'ai_asset', 'ai_capability_binding', 'ai_job', 'ai_job_capacity', 'ai_job_event',
  'ai_stream_event', 'ai_stream_session', 'ai_stream_source'
]
const digest = value => crypto.createHash('sha256').update(value).digest('hex')

function inventory(database = '') {
  const tables = sql('SHOW TABLES', database || 'wgai_ri_00_integration').trim().split('\n').filter(Boolean).sort()
  return {
    tables,
    schema: Object.fromEntries(tables.filter(name => name.startsWith('ai_')).map(name => {
      assert(/^\w+$/.test(name))
      return [name, {
        schemaSha256: digest(sql('SHOW CREATE TABLE ' + name, database || 'wgai_ri_00_integration')),
        rowCount: Number(sql('SELECT COUNT(*) FROM ' + name, database || 'wgai_ri_00_integration').trim())
      }]
    }))
  }
}

function main() {
  assert.equal(process.cwd(), root)
  const directory = path.join(root, 'backend-github/deploy/remote-ai/migrations')
  const v001 = fs.readFileSync(path.join(directory, 'V001__04a_assets_jobs.sql'), 'utf8')
  const v002 = fs.readFileSync(path.join(directory, 'V002__04a_video_stream.sql'), 'utf8')
  const executableV002 = v002.replace(/--[^\n]*/g, '').replace(/\/\*[\s\S]*?\*\//g, '')
  assert.equal(/\b(?:ALTER|DROP|DELETE|TRUNCATE|REPLACE)\b/i.test(executableV002), false)
  const before = inventory()
  const scratch = 'ai_00_verify_' + crypto.randomBytes(8).toString('hex')
  sql('CREATE DATABASE ' + scratch + ' CHARACTER SET utf8mb4')
  try {
    sql(v001, scratch); sql(v002, scratch)
    const first = inventory(scratch)
    assert.deepEqual(first.tables, expectedTables)
    sql(v001, scratch); sql(v002, scratch)
    const second = inventory(scratch)
    assert.deepEqual(second, first)
    const after = inventory()
    assert.deepEqual(after, before)
    const result = {
      status: 'PASS', sequence: ['V001__04a_assets_jobs.sql', 'V002__04a_video_stream.sql'],
      executions: 2, scratchDatabaseDropped: true, tables: first.tables,
      migrationSha256: { V001: digest(v001), V002: digest(v002) },
      integrationDatabaseUnchanged: true,
      integrationTableCount: before.tables.length,
      integrationAiRowsBefore: Object.fromEntries(Object.entries(before.schema).map(([k, v]) => [k, v.rowCount])),
      integrationAiRowsAfter: Object.fromEntries(Object.entries(after.schema).map(([k, v]) => [k, v.rowCount]))
    }
    fs.writeFileSync(output, JSON.stringify(result, null, 2) + '\n')
    console.log('PASS: V001 -> V002 repeated twice; integration database unchanged.')
  } finally {
    sql('DROP DATABASE IF EXISTS ' + scratch)
  }
}

main()
