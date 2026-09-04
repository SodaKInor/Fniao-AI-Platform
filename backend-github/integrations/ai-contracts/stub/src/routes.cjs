'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { authorized } = require('./auth.cjs');
const { readBody, jsonBody, multipart } = require('./body.cjs');
const { scenario } = require('./config.cjs');
const fixtures = require('./fixtures.cjs');
const reply = require('./respond.cjs');
const validation = require('./validation.cjs');

const imageBytes = fs.readFileSync(path.resolve(__dirname, '..', '..', 'examples', 'annotated.png'));
const SOURCE = 'synthetic-camera-01';

function createRouter(config, state) {
  return async function route(req, res) {
    const url = new URL(req.url, 'http://stub.invalid');
    if (req.method === 'GET' && url.pathname === '/health') {
      return reply.json(res, 200, { status: 'UP', simulated: true, provider_version: 'stub-simulated-v1' });
    }
    if (!authorized(req, config.token)) return reply.problem(res, 401, 'Stub credential rejected');
    try {
      const selected = scenario(req, url);
      if (req.method === 'GET' && url.pathname === '/capabilities') return capabilities(res);
      if (req.method === 'GET' && url.pathname === '/stream-sources') return sources(res);
      if (req.method === 'GET' && url.pathname === '/__stub/requests') {
        return reply.json(res, 200, { simulated: true, items: state.records });
      }
      if (req.method === 'POST' && url.pathname === '/__stub/reset') {
        state.reset(); return reply.json(res, 200, { simulated: true, reset: true });
      }
      if (req.method === 'GET' && url.pathname.startsWith('/artifacts/')) return artifact(res, url.pathname);
      if (req.method === 'POST' && url.pathname === '/infer') {
        return await upload(req, res, url, config, state, selected, 'image');
      }
      if (req.method === 'POST' && url.pathname === '/video-jobs') {
        return await upload(req, res, url, config, state, selected, 'video');
      }
      const startMatch = /^\/stream-sources\/([A-Za-z0-9_-]+)\/sessions$/.exec(url.pathname);
      if (req.method === 'POST' && startMatch) return await start(req, res, config, state, selected, startMatch[1]);
      const eventsMatch = /^\/stream-sessions\/([A-Za-z0-9_-]+)\/events$/.exec(url.pathname);
      if (req.method === 'GET' && eventsMatch) return events(req, res, url, state, selected, eventsMatch[1]);
      const stopMatch = /^\/stream-sessions\/([A-Za-z0-9_-]+)\/stop$/.exec(url.pathname);
      if (req.method === 'POST' && stopMatch) return stop(req, res, state, selected, stopMatch[1]);
      const sessionMatch = /^\/stream-sessions\/([A-Za-z0-9_-]+)$/.exec(url.pathname);
      if (req.method === 'GET' && sessionMatch) return session(req, res, state, selected, sessionMatch[1]);
      return reply.problem(res, 404, 'Stub route not found');
    } catch (error) {
      if (!res.headersSent) reply.problem(res, error.statusCode || 500, error.statusCode ? error.message : 'Stub failure');
      else res.destroy();
    }
  };
}

function capabilities(res) {
  reply.json(res, 200, { simulated: true, provider_version: 'stub-simulated-v1', capabilities: [
    { code: 'image-detection.v1', adapter_id: 'sync-draft-v0.1' },
    { code: 'video-file-analysis.v1', adapter_id: 'video-draft-v0.2' },
    { code: 'video-stream-analysis.v1', adapter_id: 'stream-draft-v0.2' }
  ] });
}

function sources(res) {
  reply.json(res, 200, { simulated: true, items: [
    { provider_source_ref: SOURCE, display_name: '合成演示来源', simulated: true }
  ] });
}

async function upload(req, res, url, config, state, selected, kind) {
  const value = multipart(req, await readBody(req, config.maxBodyBytes));
  const contract = kind === 'image' ? '0.1-draft' : '0.2-draft';
  const capability = kind === 'image' ? 'image-detection.v1' : 'video-file-analysis.v1';
  validation.requireMetadata(value.metadata, contract, capability);
  state.record(req.method, url.pathname, selected, value.metadata.request_id);
  if (selected === 'failed') return reply.problem(res, 500, 'SIMULATED provider failure');
  if (selected === 'response-lost') return reply.lose(res);
  let result = kind === 'image' ? fixtures.image(value.metadata, selected) : fixtures.video(value.metadata, selected);
  if (selected === 'invalid-schema') result = { ...result, unexpected_vendor_field: true };
  const send = () => reply.json(res, 200, result);
  return selected === 'delayed' ? reply.delayed(config.delayMillis, send) : send();
}

async function start(req, res, config, state, selected, sourceRef) {
  if (sourceRef !== SOURCE) return reply.problem(res, 404, 'Synthetic source not found');
  const metadata = jsonBody(await readBody(req, config.maxBodyBytes));
  validation.requireStreamStart(metadata);
  state.record(req.method, req.url, selected, metadata.request_id);
  const result = state.start(sourceRef, metadata, selected);
  if (selected === 'failed') return reply.problem(res, 500, 'SIMULATED stream start failure');
  if (selected === 'response-lost') return reply.lose(res);
  const send = () => reply.json(res, 200, result);
  return selected === 'delayed' ? reply.delayed(config.delayMillis, send) : send();
}

function session(req, res, state, selected, id) {
  state.record(req.method, req.url, selected, id);
  const value = state.session(id);
  return value ? reply.json(res, 200, value) : reply.problem(res, 404, 'Session not found');
}

function events(req, res, url, state, selected, id) {
  state.record(req.method, req.url, selected, id);
  const value = state.events(id, url.searchParams.get('cursor'), selected === 'success' ? null : selected);
  return value ? reply.json(res, 200, value) : reply.problem(res, 404, 'Session not found');
}

function stop(req, res, state, selected, id) {
  state.record(req.method, req.url, selected, id);
  if (selected === 'stop-unsupported') return reply.problem(res, 405, 'SIMULATED stop unsupported');
  if (selected === 'stop-unknown') return reply.lose(res);
  const value = state.stop(id);
  return value ? reply.json(res, 200, value) : reply.problem(res, 404, 'Session not found');
}

function artifact(res, pathname) {
  const name = pathname.slice('/artifacts/'.length);
  if (!/^[A-Za-z0-9_.-]+$/.test(name)) return reply.problem(res, 400, 'Invalid artifact name');
  return reply.binary(res, imageBytes, name === 'interrupted' || name === 'interrupted.png');
}

module.exports = { createRouter, SOURCE };
