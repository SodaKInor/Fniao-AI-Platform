'use strict';

const SCENARIOS = new Set([
  'success', 'empty', 'failed', 'invalid-schema', 'delayed', 'response-lost',
  'artifact-interrupted', 'duplicate-events', 'out-of-order-events',
  'stop-unsupported', 'stop-unknown'
]);

function scenario(req, url, fallback = 'success') {
  const value = req.headers['x-wgai-stub-scenario'] || url.searchParams.get('scenario') || fallback;
  if (!SCENARIOS.has(value)) {
    const error = new Error('Unsupported stub scenario');
    error.statusCode = 400;
    throw error;
  }
  return value;
}

module.exports = { scenario, SCENARIOS };
