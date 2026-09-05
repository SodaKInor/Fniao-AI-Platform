'use strict';

const SCENARIOS = new Set([
  'success', 'empty', 'failed', 'invalid-schema', 'delayed', 'response-lost',
  'artifact-interrupted', 'duplicate-events', 'out-of-order-events',
  'stop-unsupported', 'stop-unknown'
]);

function integer(value, fallback, minimum, maximum, name) {
  const parsed = value === undefined ? fallback : Number(value);
  if (!Number.isInteger(parsed) || parsed < minimum || parsed > maximum) {
    throw new Error(`Invalid ${name}`);
  }
  return parsed;
}

function loadConfig(env = process.env) {
  const token = env.WGAI_STUB_TOKEN || 'wgai-explicit-development-stub-token';
  if (!token || token.length > 512 || /[\r\n]/.test(token)) throw new Error('Invalid WGAI_STUB_TOKEN');
  const defaultScenario = env.WGAI_STUB_DEFAULT_SCENARIO || 'success';
  if (!SCENARIOS.has(defaultScenario)) throw new Error('Invalid WGAI_STUB_DEFAULT_SCENARIO');
  return Object.freeze({
    host: env.WGAI_STUB_HOST || '0.0.0.0',
    port: integer(env.WGAI_STUB_PORT, 18080, 1, 65535, 'WGAI_STUB_PORT'),
    token,
    maxBodyBytes: integer(env.WGAI_STUB_MAX_BODY_BYTES, 32 * 1024 * 1024, 1024, 512 * 1024 * 1024,
      'WGAI_STUB_MAX_BODY_BYTES'),
    delayMillis: integer(env.WGAI_STUB_DELAY_MS, 1500, 1, 30000, 'WGAI_STUB_DELAY_MS'),
    defaultScenario
  });
}

function scenario(req, url, fallback = 'success') {
  const value = req.headers['x-wgai-stub-scenario'] || url.searchParams.get('scenario') || fallback;
  if (!SCENARIOS.has(value)) {
    const error = new Error('Unsupported stub scenario');
    error.statusCode = 400;
    throw error;
  }
  return value;
}

module.exports = { loadConfig, scenario, SCENARIOS };
