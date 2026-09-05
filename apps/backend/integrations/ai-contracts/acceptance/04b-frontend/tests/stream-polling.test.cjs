const { test } = require('node:test')
const assert = require('node:assert/strict')
const { loadSource } = require('./load-source.cjs')
const { createStreamPolling, streamTerminalStates } = loadSource('modules/ai/stream/polling.js')
const flush = () => new Promise(resolve => setImmediate(resolve))

function harness() {
  const sessions = [], pages = [], updates = [], batches = [], errors = [], timers = new Map(); let sequence = 0
  const poll = createStreamPolling({
    getSession(id) { return new Promise((resolve, reject) => sessions.push({ id, resolve, reject })) },
    getEvents(id, query) { return new Promise((resolve, reject) => pages.push({ id, query, resolve, reject })) },
    onSession(value) { updates.push(value) }, onEvents(value, cursor) { batches.push({ value, cursor }) }, onError(error) { errors.push(error) },
    schedule(fn, ms) { timers.set(++sequence, { fn, ms }); return sequence }, unschedule(id) { timers.delete(id) }
  })
  return { poll, sessions, pages, updates, batches, errors, timers }
}
function session(id, state = 'RUNNING') { return { sessionId: id, state, parameters: { maxEventsPerPoll: 50, pollIntervalMillis: 500 } } }

test('stream polling is serial, advances stable cursor and deduplicates events', async () => {
  const h = harness(); h.poll.start('A'); assert.equal(h.pages.length, 0)
  h.sessions[0].resolve(session('A')); await flush(); assert.equal(h.pages.length, 1); assert.equal(h.pages[0].query.cursor, null)
  const event = { eventId: 'one' }; h.pages[0].resolve({ sessionId: 'A', items: [event], nextCursor: 'next' }); await flush()
  assert.deepEqual(h.batches[0].value, [event]); assert.equal(h.timers.size, 1)
  const next = [...h.timers.values()][0]; assert.equal(next.ms, 500); next.fn(); await flush()
  h.sessions[1].resolve(session('A')); await flush(); assert.equal(h.pages[1].query.cursor, 'next')
  h.pages[1].resolve({ sessionId: 'A', items: [event], nextCursor: 'next' }); await flush()
  assert.equal(h.batches[1].value.length, 0); h.poll.stop()
})

test('leave and session switch discard late session, events and errors', async () => {
  const h = harness(); h.poll.start('A'); h.sessions[0].resolve(session('A')); await flush(); h.poll.start('B')
  h.pages[0].resolve({ sessionId: 'A', items: [{ eventId: 'old' }] }); h.sessions[1].resolve(session('B')); await flush()
  h.pages[1].resolve({ sessionId: 'B', items: [{ eventId: 'new' }] }); await flush()
  assert.equal(h.batches.length, 1); assert.equal(h.batches[0].value[0].eventId, 'new')
  h.poll.stop(); const timer = [...h.timers.values()][0]; if (timer) timer.fn(); assert.equal(h.errors.length, 0)
})

test('all stream terminal states stop only after collecting their final event page', async () => {
  for (const state of streamTerminalStates) {
    const h = harness(); h.poll.start('A'); h.sessions[0].resolve(session('A', state)); await flush()
    h.pages[0].resolve({ sessionId: 'A', items: [], nextCursor: null }); await flush()
    assert.equal(h.updates[0].state, state); assert.equal(h.timers.size, 0)
  }
})

test('identity mismatch pauses without inventing a terminal state', async () => {
  const h = harness(); h.poll.start('A'); h.sessions[0].resolve(session('B')); await flush()
  assert.equal(h.updates.length, 0); assert.equal(h.errors.length, 1); assert.equal(h.pages.length, 0)
})
