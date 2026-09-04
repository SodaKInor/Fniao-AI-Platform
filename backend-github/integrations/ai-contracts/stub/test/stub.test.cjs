'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const test = require('node:test');
const { createStubServer } = require('../src/server.cjs');

const TOKEN = 'test-development-stub-token';
let instance;
let origin;

test.before(async () => {
  instance = createStubServer({ WGAI_STUB_HOST: '127.0.0.1', WGAI_STUB_PORT: '1', WGAI_STUB_TOKEN: TOKEN });
  await new Promise(resolve => instance.server.listen(0, '127.0.0.1', resolve));
  origin = `http://127.0.0.1:${instance.server.address().port}`;
});
test.after(async () => new Promise(resolve => instance.server.close(resolve)));

function call(pathname, options = {}) {
  return new Promise((resolve, reject) => {
    const request = http.request(origin + pathname, {
      method: options.method || 'GET', headers: { Authorization: `Bearer ${TOKEN}`, ...(options.headers || {}) }
    }, response => {
      const chunks = [];
      response.on('data', value => chunks.push(value));
      response.on('end', () => resolve({ status: response.statusCode, headers: response.headers,
        body: Buffer.concat(chunks) }));
      response.on('aborted', () => reject(new Error('Response body was interrupted')));
      response.on('error', reject);
      response.on('close', () => {
        if (!response.complete) reject(new Error('Response body was interrupted'));
      });
    });
    request.on('error', reject);
    request.setTimeout(2000, () => request.destroy(new Error('Request timed out')));
    if (options.body) request.write(options.body);
    request.end();
  });
}

function multipart(metadata) {
  const boundary = 'wgai-stub-test-boundary';
  const body = Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="metadata"\r\n` +
    `Content-Type: application/json\r\n\r\n${JSON.stringify(metadata)}\r\n` +
    `--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="input.bin"\r\n` +
    `Content-Type: application/octet-stream\r\n\r\nsynthetic-input\r\n--${boundary}--\r\n`);
  return { body, contentType: `multipart/form-data; boundary=${boundary}` };
}

test('requires the explicit development bearer token', async () => {
  const response = await call('/capabilities', { headers: { Authorization: '' } });
  assert.equal(response.status, 401);
  assert.equal(response.headers['x-wgai-simulated'], 'true');
});

test('returns strict simulated image and video results', async () => {
  const image = multipart({ contract_version: '0.1-draft', request_id: 'image-001',
    capability: 'image-detection.v1', parameters: { threshold: 0.5, max_detections: 10, annotate: true } });
  const imageResponse = await call('/infer', { method: 'POST', body: image.body,
    headers: { 'Content-Type': image.contentType } });
  assert.equal(imageResponse.status, 200);
  assert.equal(JSON.parse(imageResponse.body).simulated, true);

  const video = multipart({ contract_version: '0.2-draft', request_id: 'video-001',
    capability: 'video-file-analysis.v1', parameters: { threshold: 0.5, sample_interval_ms: 1000,
      max_events: 10, include_snapshots: true, annotate: false } });
  const videoResponse = await call('/video-jobs', { method: 'POST', body: video.body,
    headers: { 'Content-Type': video.contentType } });
  const videoBody = JSON.parse(videoResponse.body);
  assert.equal(videoResponse.status, 200);
  assert.equal(videoBody.simulated, true);
  assert.equal(videoBody.events[0].offset_ms, 1250);
});

test('runs a synthetic stream session without exposing RTSP or provider secrets', async () => {
  const sources = await call('/stream-sources');
  const sourceText = sources.body.toString('utf8');
  assert.equal(sources.status, 200);
  assert.doesNotMatch(sourceText, /rtsp|gpu.?url|password|credential/i);

  const startBody = JSON.stringify({ contract_version: '0.2-draft', request_id: 'local-session-001',
    capability: 'video-stream-analysis.v1', parameters: { max_events_per_poll: 10,
      poll_interval_ms: 1000, include_snapshots: true } });
  const started = await call('/stream-sources/synthetic-camera-01/sessions', { method: 'POST', body: startBody,
    headers: { 'Content-Type': 'application/json' } });
  const session = JSON.parse(started.body);
  assert.equal(session.provider_version, 'stub-simulated-v1');

  const events = await call(`/stream-sessions/${session.provider_session_id}/events?limit=10`);
  assert.equal(JSON.parse(events.body).items.length, 1);
  const stopped = await call(`/stream-sessions/${session.provider_session_id}/stop`, { method: 'POST' });
  assert.equal(JSON.parse(stopped.body).state, 'STOPPED');
});

test('supports empty, invalid, lost and interrupted deterministic failures', async () => {
  const empty = await call('/stream-sessions/stub-session-local-session-001/events?limit=10&scenario=empty');
  assert.deepEqual(JSON.parse(empty.body).items, []);
  const invalid = await call('/stream-sources/synthetic-camera-01/sessions', { method: 'POST',
    body: JSON.stringify({ contract_version: '0.2-draft', request_id: 'bad', capability: 'video-stream-analysis.v1',
      rtspUrl: 'prohibited', parameters: { max_events_per_poll: 10, poll_interval_ms: 1000,
        include_snapshots: true } }), headers: { 'Content-Type': 'application/json' } });
  assert.equal(invalid.status, 400);
  await assert.rejects(call('/artifacts/interrupted.png'), /interrupted|aborted/i);
});

test('contract is parseable and implementation has no application or algorithm imports', () => {
  const root = path.resolve(__dirname, '..');
  const contract = JSON.parse(fs.readFileSync(path.join(root, 'contract/provider-stub.v1.json')));
  assert.equal(contract.simulated, true);
  const source = fs.readdirSync(path.join(root, 'src')).filter(name => name.endsWith('.cjs'))
    .map(name => fs.readFileSync(path.join(root, 'src', name), 'utf8')).join('\n');
  assert.doesNotMatch(source, /org\.jeecg|jdbc|mysql|AIModel|opencv|onnx|cuda|child_process/i);
});
