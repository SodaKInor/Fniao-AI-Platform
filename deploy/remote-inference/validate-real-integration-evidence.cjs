#!/usr/bin/env node
const crypto = require('node:crypto')
const fs = require('node:fs')
const path = require('node:path')
const { validateIntake } = require('./contract-intake-rules.cjs')
const { validateRealIntegrationEvidence } = require('./real-integration-evidence-rules.cjs')

function main(argv) {
  if (argv.length !== 2 || argv.some(value => value.startsWith('-'))) {
    console.error('Usage: node validate-real-integration-evidence.cjs /absolute/path/to/private-contract-intake.json /path/to/real-evidence.json')
    return 2
  }
  const intakePath = path.resolve(argv[0])
  const evidencePath = path.resolve(argv[1])
  let intakeRaw
  let intake
  let evidence
  try {
    intakeRaw = fs.readFileSync(intakePath)
    intake = JSON.parse(intakeRaw.toString('utf8'))
  } catch (_) { console.error('FAIL: private contract intake is unavailable or invalid JSON'); return 1 }
  const intakeIssues = validateIntake(intake, { checkFiles: true })
  if (intakeIssues.length) {
    console.error(`FAIL: private contract intake has ${intakeIssues.length} issue(s)`)
    intakeIssues.forEach(issue => console.error(`- ${issue.field}: ${issue.message}`))
    return 1
  }
  try { evidence = JSON.parse(fs.readFileSync(evidencePath, 'utf8')) }
  catch (_) { console.error('FAIL: real integration evidence is unavailable or invalid JSON'); return 1 }
  const intakeSha256 = crypto.createHash('sha256').update(intakeRaw).digest('hex')
  const issues = validateRealIntegrationEvidence(evidence, intake, {
    checkFiles: true,
    evidenceBaseDir: path.dirname(evidencePath),
    intakeSha256
  })
  if (issues.length) {
    console.error(`FAIL: real integration evidence has ${issues.length} issue(s)`)
    issues.forEach(issue => console.error(`- ${issue.field}: ${issue.message}`))
    return 1
  }
  console.log('PASS: complete real RTX 5070 application-flow evidence for image, video, and stream')
  return 0
}

if (require.main === module) process.exitCode = main(process.argv.slice(2))
module.exports = { main }
