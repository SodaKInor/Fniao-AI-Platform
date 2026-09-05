'use strict';

const PROHIBITED = /(^|_)(rtsp|rtspurl|gpuurl|providerurl|credentials|token|password)($|_)/i;

function objectKeys(value, path = 'body') {
  if (Array.isArray(value)) return value.forEach((item, index) => objectKeys(item, `${path}[${index}]`));
  if (!value || typeof value !== 'object') return;
  for (const [key, nested] of Object.entries(value)) {
    if (PROHIBITED.test(key.replaceAll('-', '_'))) invalid(`Prohibited request field at ${path}.${key}`);
    objectKeys(nested, `${path}.${key}`);
  }
}

function requireMetadata(value, contract, capability) {
  objectKeys(value);
  if (value.contract_version !== contract || value.capability !== capability
      || !identifier(value.request_id) || !plainObject(value.parameters)) {
    invalid('Metadata does not match the provider stub contract');
  }
}

function requireStreamStart(value) {
  requireMetadata(value, '0.2-draft', 'video-stream-analysis.v1');
  const p = value.parameters;
  if (!Number.isInteger(p.max_events_per_poll) || p.max_events_per_poll < 1 || p.max_events_per_poll > 100
      || !Number.isInteger(p.poll_interval_ms) || p.poll_interval_ms < 100 || p.poll_interval_ms > 60000
      || typeof p.include_snapshots !== 'boolean') invalid('Invalid stream parameters');
}

function identifier(value) { return typeof value === 'string' && /^[A-Za-z0-9_-]{1,160}$/.test(value); }
function plainObject(value) { return value && !Array.isArray(value) && typeof value === 'object'; }
function invalid(message) { const error = new Error(message); error.statusCode = 400; throw error; }

module.exports = { objectKeys, requireMetadata, requireStreamStart, identifier };
