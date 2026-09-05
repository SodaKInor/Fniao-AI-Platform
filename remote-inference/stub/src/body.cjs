'use strict';

function readBody(req, maximum) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', chunk => {
      size += chunk.length;
      if (size > maximum) {
        const error = new Error('Request body exceeds stub limit');
        error.statusCode = 413;
        reject(error);
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

function jsonBody(buffer) {
  let value;
  try { value = JSON.parse(buffer.toString('utf8')); }
  catch (cause) {
    const error = new Error('Invalid JSON body');
    error.statusCode = 400;
    throw error;
  }
  if (!value || Array.isArray(value) || typeof value !== 'object') {
    const error = new Error('JSON body must be an object');
    error.statusCode = 400;
    throw error;
  }
  return value;
}

function multipart(req, buffer) {
  const contentType = req.headers['content-type'] || '';
  const match = /boundary="?([^";]+)"?/i.exec(contentType);
  if (!match) return invalid('Expected multipart/form-data');
  const segments = buffer.toString('latin1').split(`--${match[1]}`);
  const metadata = part(segments, 'metadata');
  const file = part(segments, 'file');
  if (!metadata || !file || file.length === 0) return invalid('Missing metadata or file');
  return { metadata: jsonBody(Buffer.from(metadata, 'latin1')), fileBytes: file.length };
}

function part(segments, name) {
  const marker = `name="${name}"`;
  const segment = segments.find(value => value.includes(marker));
  if (!segment) return null;
  const start = segment.indexOf('\r\n\r\n');
  if (start < 0) return null;
  return segment.slice(start + 4).replace(/\r\n$/, '');
}

function invalid(message) {
  const error = new Error(message);
  error.statusCode = 400;
  throw error;
}

module.exports = { readBody, jsonBody, multipart };
