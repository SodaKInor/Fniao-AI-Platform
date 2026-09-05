'use strict';

const { timingSafeEqual } = require('node:crypto');

function equal(left, right) {
  const a = Buffer.from(left || '', 'utf8');
  const b = Buffer.from(right || '', 'utf8');
  return a.length === b.length && timingSafeEqual(a, b);
}

function authorized(req, token) {
  const expected = `Bearer ${token}`;
  return equal(req.headers.authorization, expected);
}

module.exports = { authorized };
