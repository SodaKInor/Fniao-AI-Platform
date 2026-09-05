'use strict';

function json(res, status, value) {
  const body = Buffer.from(JSON.stringify(value));
  res.writeHead(status, { 'Content-Type': 'application/json', 'Content-Length': body.length,
    'X-WGAI-Simulated': 'true', 'Cache-Control': 'no-store' });
  res.end(body);
}

function problem(res, status, message) {
  json(res, status, { simulated: true, error: { code: `STUB_${status}`, message } });
}

function binary(res, value, interrupted) {
  res.writeHead(200, { 'Content-Type': 'image/png', 'Content-Length': value.length,
    'X-WGAI-Simulated': 'true', 'Cache-Control': 'no-store' });
  if (!interrupted) return res.end(value);
  res.flushHeaders();
  res.write(value.subarray(0, Math.max(1, Math.floor(value.length / 2))), () => res.destroy());
}

function lose(res) { res.destroy(); }
function delayed(milliseconds, action) { setTimeout(action, milliseconds); }

module.exports = { json, problem, binary, lose, delayed };
