#!/usr/bin/env node
'use strict';

const fs = require('node:fs');
const path = require('node:path');

const remoteRoot = __dirname;
const repositoryRoot = path.resolve(remoteRoot, '..');
const failures = [];

function fail(message) { failures.push(message); }
function exists(relative) { return fs.existsSync(path.join(repositoryRoot, relative)); }
function read(relative) { return fs.readFileSync(path.join(repositoryRoot, relative), 'utf8'); }

function filesBelow(relative) {
  const root = path.join(repositoryRoot, relative);
  const output = [];
  function visit(current) {
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const target = path.join(current, entry.name);
      if (entry.isDirectory()) visit(target);
      else if (entry.isFile()) output.push(path.relative(repositoryRoot, target));
    }
  }
  visit(root);
  return output.sort();
}

function validateLayout() {
  const expected = [
    'remote-inference/contracts/business/v1/business.openapi.json',
    'remote-inference/contracts/business/v1.1/business.openapi.json',
    'remote-inference/contracts/provider/v0.1.openapi.json',
    'remote-inference/contracts/provider/v0.2.openapi.json',
    'remote-inference/contracts/provider/provider-stub.v1.json',
    'remote-inference/fixtures/manifest.json',
    'remote-inference/stub/index.cjs',
    'remote-inference/stub/src/server.cjs',
    'remote-inference/stub/src/config.cjs',
    'remote-inference/stub/src/auth.cjs',
    'remote-inference/stub/src/body.cjs',
    'remote-inference/stub/src/routes.cjs',
    'remote-inference/stub/src/validation.cjs',
    'remote-inference/stub/src/scenarios.cjs',
    'remote-inference/stub/src/respond.cjs',
    'remote-inference/stub/src/fixtures.cjs',
    'remote-inference/stub/src/state.cjs',
    'remote-inference/stub/test/stub.test.cjs',
    'docs/remote-inference/ARCHITECTURE.md',
    'docs/remote-inference/FILE_OWNERSHIP.md',
    'docs/remote-inference/PARALLEL_PLAN.md',
    'docs/remote-inference/PROMPTS.md',
    'deploy/remote-inference/application-remote-ai.yml',
    'deploy/remote-inference/core.override.yml',
    'deploy/remote-inference/stub.override.yml'
  ];
  expected.filter(relative => !exists(relative)).forEach(relative => fail(`missing ${relative}`));

  const retiredRoots = [
    'apps/backend/integrations/ai-contracts',
    'apps/backend/development/remote-inference'
  ];
  retiredRoots.filter(exists).forEach(relative => fail(`duplicate active source remains at ${relative}`));

  const expectedDatabaseOwned = [
    'apps/backend/deploy/remote-ai/migrations/V001__04a_assets_jobs.sql',
    'apps/backend/deploy/remote-ai/migrations/V002__04a_video_stream.sql',
    'apps/backend/deploy/remote-ai/stub-bindings.example.sql'
  ];
  if (exists('apps/backend/deploy/remote-ai')) {
    const databaseOwned = filesBelow('apps/backend/deploy/remote-ai');
    if (JSON.stringify(databaseOwned) !== JSON.stringify(expectedDatabaseOwned)) {
      fail(`legacy remote-ai must contain only database-owned files: ${databaseOwned.join(', ')}`);
    }
  } else {
    const finalDatabaseOwned = [
      'database/migrations/ai-core/V001__04a_assets_jobs.sql',
      'database/migrations/stream/V002__04a_video_stream.sql',
      'database/seeds/stub/stub-bindings.example.sql'
    ];
    finalDatabaseOwned.filter(relative => !exists(relative))
      .forEach(relative => fail(`database package file is missing after integration: ${relative}`));
  }

  const deployFiles = filesBelow('deploy/remote-inference');
  if (deployFiles.length !== 16) fail(`expected 16 non-database deployment files, found ${deployFiles.length}`);
  if (filesBelow('remote-inference/fixtures').length !== 38) fail('fixture inventory must contain 38 files');
  if (filesBelow('remote-inference/acceptance').length !== 266) fail('acceptance inventory must contain 266 files');
}

function validateJsonEvidence() {
  const roots = [
    'remote-inference/contracts',
    'remote-inference/fixtures',
    'remote-inference/acceptance',
    'deploy/remote-inference'
  ];
  let parsed = 0;
  for (const root of roots) {
    for (const relative of filesBelow(root).filter(file => file.endsWith('.json'))) {
      try { JSON.parse(read(relative)); parsed += 1; }
      catch (error) { fail(`invalid JSON ${relative}: ${error.message}`); }
    }
  }
  if (parsed < 160) fail(`unexpectedly small JSON evidence inventory: ${parsed}`);

  const manifest = JSON.parse(read('remote-inference/fixtures/manifest.json'));
  if (manifest.simulated !== true) fail('fixture manifest must be simulated');
  for (const [name, item] of Object.entries(manifest.cases || {})) {
    if (item.simulated !== true) fail(`fixture ${name} is not marked simulated in the manifest`);
    if (!exists(`remote-inference/fixtures/${name}`)) fail(`fixture manifest entry is missing: ${name}`);
  }
  const stubContract = JSON.parse(read('remote-inference/contracts/provider/provider-stub.v1.json'));
  if (stubContract.simulated !== true || !String(stubContract.version).startsWith('stub-')) {
    fail('stub provider contract must remain explicitly simulated/stub');
  }
}

function validateLinks() {
  const markdown = [
    'remote-inference/README.md',
    ...filesBelow('remote-inference/fixtures').filter(file => file.endsWith('.md')),
    ...filesBelow('remote-inference/stub').filter(file => file.endsWith('.md')),
    ...filesBelow('remote-inference/handoff').filter(file => file.endsWith('.md')),
    ...filesBelow('docs/remote-inference').filter(file => file.endsWith('.md')),
    ...filesBelow('deploy/remote-inference').filter(file => file.endsWith('.md'))
  ];
  const pattern = /\[[^\]]*\]\(([^)]+)\)/g;
  for (const relative of [...new Set(markdown)]) {
    const base = path.dirname(path.join(repositoryRoot, relative));
    for (const match of read(relative).matchAll(pattern)) {
      let target = match[1].trim().replace(/^<|>$/g, '');
      if (!target || target.startsWith('#') || /^[a-z][a-z0-9+.-]*:/i.test(target)) continue;
      target = target.split('#')[0];
      try { target = decodeURIComponent(target); }
      catch (_) { fail(`invalid encoded link in ${relative}: ${match[1]}`); continue; }
      if (!fs.existsSync(path.resolve(base, target))) fail(`broken relative link in ${relative}: ${match[1]}`);
    }
  }
}

function validateStubIsolation() {
  const source = filesBelow('remote-inference/stub/src')
    .filter(file => file.endsWith('.cjs')).map(read).join('\n');
  if (/org\.jeecg|jdbc|mysql|AIModel|opencv|onnx|cuda|child_process/i.test(source)) {
    fail('stub source imports an application, database, algorithm, GPU, or process dependency');
  }
  const responses = read('remote-inference/stub/src/respond.cjs');
  if (!responses.includes("'X-WGAI-Simulated': 'true'")) fail('stub responses lost the simulated header');
  const fixtures = read('remote-inference/stub/src/fixtures.cjs');
  if (!fixtures.includes('simulated: true') || !fixtures.includes('stub-simulated-v1')) {
    fail('stub fixtures lost explicit simulated/stub metadata');
  }
}

function validateProductionIsolation() {
  const production = read('deploy/remote-inference/prod.env.example');
  const core = read('deploy/remote-inference/core.override.yml');
  const application = read('deploy/remote-inference/application-remote-ai.yml');
  const rootCompose = read('deploy/docker-compose.yml');
  if (!production.includes('WGAI_INFERENCE_MODE=disabled')
      || !production.includes('WGAI_INFERENCE_DEVELOPMENT_STUB=false')
      || !production.includes('WGAI_INFERENCE_PROVIDER_KEY=remote')) {
    fail('production template must default to disabled, non-stub remote configuration');
  }
  if (!core.includes('WGAI_INFERENCE_MODE: ${WGAI_INFERENCE_MODE:-disabled}')
      || !core.includes('WGAI_INFERENCE_DEVELOPMENT_STUB: ${WGAI_INFERENCE_DEVELOPMENT_STUB:-false}')) {
    fail('core override must default to disabled with the development stub off');
  }
  if (!application.includes('mode: ${WGAI_INFERENCE_MODE:disabled}')
      || !application.includes('development-stub: ${WGAI_INFERENCE_DEVELOPMENT_STUB:false}')) {
    fail('application profile must default to disabled with the development stub off');
  }
  if (/^\s{2}remote-ai-stub:/m.test(rootCompose)
      || /remote-inference\/stub\/Dockerfile|stub\.override/.test(rootCompose)) {
    fail('root Compose must not start or reference the development stub by default');
  }
  if (!rootCompose.includes('WGAI_INFERENCE_MODE: ${WGAI_INFERENCE_MODE:-disabled}')
      || !rootCompose.includes('WGAI_INFERENCE_DEVELOPMENT_STUB: ${WGAI_INFERENCE_DEVELOPMENT_STUB:-false}')) {
    fail('root Compose must load remote inference in disabled, non-stub mode by default');
  }
  const stubOverride = read('deploy/remote-inference/stub.override.yml');
  if (!stubOverride.includes('profiles: [remote-ai-stub]')
      || !stubOverride.includes('WGAI_INFERENCE_DEVELOPMENT_STUB: "true"')) {
    fail('development stub must require its explicit Compose profile and marker');
  }
}

validateLayout();
validateJsonEvidence();
validateLinks();
validateStubIsolation();
validateProductionIsolation();

if (failures.length) {
  console.error(`FAIL: remote inference boundary has ${failures.length} issue(s)`);
  failures.forEach(message => console.error(`- ${message}`));
  process.exitCode = 1;
} else {
  console.log('PASS: layout, JSON evidence, active links, stub isolation, and production defaults');
}
