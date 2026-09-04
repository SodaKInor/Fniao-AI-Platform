const { root, sql } = require('../../round3/scripts/runtime.cjs')
const assert = require('node:assert/strict')
const crypto = require('node:crypto')
const fs = require('node:fs')
const path = require('node:path')

const expectedTables = [
  'ai_asset',
  'ai_capability_binding',
  'ai_job',
  'ai_job_capacity',
  'ai_job_event',
  'ai_stream_event',
  'ai_stream_session',
  'ai_stream_source'
]
const output = path.resolve(__dirname, '..', 'migration.json')

const digest = value => crypto.createHash('sha256').update(value).digest('hex')

function mainInventory() {
  const tables = sql('SHOW TABLES').trim().split('\n').filter(Boolean).sort()
  const aiTables = tables.filter(table => table.startsWith('ai_'))
  return {
    tableCount: tables.length,
    tableNamesSha256: digest(tables.join('\n')),
    aiTables: Object.fromEntries(aiTables.map(table => {
    assert(/^\w+$/.test(table))
    const schema = sql('SHOW CREATE TABLE ' + table)
    const rowCount = Number(sql('SELECT COUNT(*) FROM ' + table).trim())
    return [table, { schemaSha256: digest(schema), rowCount }]
    }))
  }
}

function scratchInventory(database) {
  const tables = sql('SHOW TABLES', database).trim().split('\n').filter(Boolean).sort()
  return {
    tables,
    schema: Object.fromEntries(tables.map(table => [table, digest(sql('SHOW CREATE TABLE ' + table, database))]))
  }
}

const migrationDirectory = path.join(root, 'backend-github/deploy/remote-ai/migrations')
const v001 = fs.readFileSync(path.join(migrationDirectory, 'V001__04a_assets_jobs.sql'), 'utf8')
const v002 = fs.readFileSync(path.join(migrationDirectory, 'V002__04a_video_stream.sql'), 'utf8')
const destructiveV002 = /\b(?:ALTER|DROP|DELETE|TRUNCATE|REPLACE)\b/i.test(
  v002.replace(/--[^\n]*/g, '').replace(/\/\*[\s\S]*?\*\//g, '')
)
assert.equal(destructiveV002, false, 'V002 must remain additive')

const before = mainInventory()
const scratch = 'ai_00_verify_' + crypto.randomBytes(8).toString('hex')
sql('CREATE DATABASE ' + scratch + ' CHARACTER SET utf8mb4')
try {
  sql(v001, scratch)
  sql(v002, scratch)
  const first = scratchInventory(scratch)
  assert.deepEqual(first.tables, expectedTables)

  sql(v001, scratch)
  sql(v002, scratch)
  const second = scratchInventory(scratch)
  assert.deepEqual(second, first, 'repeat execution changed the schema')

  const scoreNullable = sql(
    "SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() " +
    "AND TABLE_NAME = 'ai_stream_event' AND COLUMN_NAME = 'score'",
    scratch
  ).trim()
  assert.equal(scoreNullable, 'YES')

  const after = mainInventory()
  assert.deepEqual(after, before, 'the integration database changed during scratch migration verification')

  const result = {
    status: 'PASS',
    sequence: ['V001__04a_assets_jobs.sql', 'V002__04a_video_stream.sql'],
    executions: 2,
    scratchDatabaseDropped: true,
    tables: first.tables,
    schemaSha256: first.schema,
    scoreNullable: true,
    v002AdditiveOnly: true,
    migrationSha256: {
      V001: digest(v001),
      V002: digest(v002)
    },
    integrationDatabase: {
      tableCount: before.tableCount,
      beforeSha256: digest(JSON.stringify(before)),
      afterSha256: digest(JSON.stringify(after)),
      tableListAndAiRowsUnchanged: true,
      note: 'No write statement targets the integration database; the table list plus existing AI schemas and row counts are compared.'
    }
  }
  fs.writeFileSync(output, JSON.stringify(result, null, 2) + '\n')
  console.log('PASS: V001 -> V002 repeated twice in scratch; integration database unchanged.')
} finally {
  sql('DROP DATABASE IF EXISTS ' + scratch)
}
