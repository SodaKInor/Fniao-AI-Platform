const { test } = require('node:test')
const assert = require('node:assert/strict')
const { loadSource } = require('./load-source.cjs')
const { createJobPolling, terminalStates } = loadSource('services/ai/jobPolling.js')
const flush = () => new Promise(resolve => setImmediate(resolve))

function harness() {
  const timers = new Map(), requests = [], updates = [], errors = []
  let sequence = 0
  const poll = createJobPolling({
    getJob(id) { return new Promise((resolve, reject) => requests.push({ id, resolve, reject })) },
    onUpdate(job) { updates.push(job) }, onError(error) { errors.push(error) },
    schedule(fn, ms) { assert.equal(ms, 2000); timers.set(++sequence, fn); return sequence },
    unschedule(id) { timers.delete(id) }
  })
  return { poll, timers, requests, updates, errors }
}

test('leaving during an in-flight query discards it and never schedules another poll', async () => {
  const h = harness(); h.poll.start('A'); h.poll.stop()
  h.requests[0].resolve({ requestId: 'A', state: 'WAITING' }); await flush()
  assert.equal(h.updates.length, 0); assert.equal(h.timers.size, 0)
})

test('A → B isolates both late success and late failure', async () => {
  for (const fail of [false, true]) {
    const h = harness(); h.poll.start('A'); h.poll.start('B')
    h.requests[1].resolve({ requestId: 'B', state: 'SUCCEEDED' }); await flush()
    if (fail) h.requests[0].reject(new Error('late A'))
    else h.requests[0].resolve({ requestId: 'A', state: 'WAITING' })
    await flush()
    assert.equal(h.updates.length, 1); assert.equal(h.updates[0].requestId, 'B')
    assert.equal(h.errors.length, 0); assert.equal(h.timers.size, 0)
  }
})

test('queries do not overlap, stopping clears timers, resuming queries only the current task', async () => {
  const h = harness(); h.poll.start('A')
  assert.equal(h.timers.size, 0)
  h.requests[0].resolve({ requestId: 'A', state: 'WAITING' }); await flush()
  assert.equal(h.timers.size, 1)
  const staleTimer = [...h.timers.values()][0]
  h.poll.stop(); assert.equal(h.timers.size, 0)
  h.poll.start('B'); h.requests[1].resolve({ requestId: 'B', state: 'WAITING' }); await flush()
  staleTimer(); h.poll.stop()
  assert.equal(h.timers.size, 0); assert.equal(h.requests.length, 2)
})

test('all observation terminal states stop, including UNKNOWN', async () => {
  for (const state of terminalStates) {
    const h = harness(); h.poll.start('A'); h.requests[0].resolve({ requestId: 'A', state }); await flush()
    assert.equal(h.updates.length, 1); assert.equal(h.timers.size, 0)
  }
})

test('network failures and mismatched identities pause without inventing a job state', async () => {
  for (const mismatch of [false, true]) {
    const h = harness(); h.poll.start('A')
    if (mismatch) h.requests[0].resolve({ requestId: 'B', state: 'SUCCEEDED' })
    else h.requests[0].reject(new Error('offline'))
    await flush(); assert.equal(h.updates.length, 0); assert.equal(h.errors.length, 1)
    assert.equal(h.timers.size, 0); assert.equal(h.requests.length, 1)
  }
})
