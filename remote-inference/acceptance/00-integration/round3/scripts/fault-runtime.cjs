const { root, work, profile, run } = require('./runtime.cjs')
const fs = require('node:fs')
const path = require('node:path')
const control = path.join(work, 'control')
const classes = path.join(work, 'fault-classes')
fs.mkdirSync(control, { recursive: true })
fs.mkdirSync(classes, { recursive: true })
fs.writeFileSync(path.join(control, 'mode'), 'normal')
fs.writeFileSync(path.join(control, 'download-mode'), 'normal')
run('docker', ['run', '--rm', '-v', root + ':/workspace:ro', '-v', work + ':/validation',
  'maven:3.8.8-eclipse-temurin-8', 'javac', '-source', '8', '-target', '8', '-encoding', 'UTF-8',
  '-cp', '/validation/java/classes:/validation/java/libs/*', '-d', '/validation/fault-classes',
  '/workspace/backend-github/integrations/ai-contracts/acceptance/00-integration/round3/scripts/FaultInjection.java',
  '/workspace/backend-github/integrations/ai-contracts/acceptance/00-integration/round3/scripts/DownloadFaultFilter.java'])
const config = JSON.parse(fs.readFileSync(profile))
config.services.backend.entrypoint = ['java', '-Djava.security.egd=file:/dev/./urandom',
  '-Dloader.path=/acceptance/classes', '-cp', '/app/app.jar', 'org.springframework.boot.loader.PropertiesLauncher',
  '--spring.config.additional-location=file:/app/config/']
config.services.backend.volumes.push(classes + ':/acceptance/classes:ro', control + ':/acceptance/control')
fs.writeFileSync(path.join(work, 'compose-acceptance.json'), JSON.stringify(config, null, 2), { mode: 0o600 })
console.log('Prepared 00-only classpath overlay; standard demo profile and application image unchanged.')
