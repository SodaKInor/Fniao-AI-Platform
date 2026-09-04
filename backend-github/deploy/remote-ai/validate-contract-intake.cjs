#!/usr/bin/env node
const fs = require('node:fs')
const path = require('node:path')
const { validateIntake } = require('./contract-intake-rules.cjs')

function main(argv) {
  if (argv.length !== 1 || argv[0].startsWith('-')) {
    console.error('Usage: node validate-contract-intake.cjs /absolute/path/to/private-contract-intake.json')
    return 2
  }
  const input = path.resolve(argv[0])
  let document
  try { document = JSON.parse(fs.readFileSync(input, 'utf8')) }
  catch (_) { console.error('FAIL: contract intake is unavailable or invalid JSON'); return 1 }
  const issues = validateIntake(document, { checkFiles: true })
  if (issues.length) {
    console.error(`FAIL: contract intake has ${issues.length} issue(s)`)
    issues.forEach(issue => console.error(`- ${issue.field}: ${issue.message}`))
    return 1
  }
  const enabled = Object.entries(document.capabilities)
    .filter(([, capability]) => capability.status === 'ENABLED').map(([name]) => name)
  console.log(`PASS: confirmed development contract; enabled capabilities: ${enabled.join(', ') || 'none'}`)
  return 0
}

if (require.main === module) process.exitCode = main(process.argv.slice(2))
module.exports = { main }
