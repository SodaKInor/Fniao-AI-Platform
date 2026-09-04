'use strict';

const { streamSession, streamEvents } = require('./fixtures.cjs');

class StubState {
  constructor(defaultScenario = 'success') {
    this.defaultScenario = defaultScenario;
    this.sessions = new Map();
    this.records = [];
  }

  setScenario(value) { this.defaultScenario = value; }

  record(method, path, scenario, requestId = null) {
    this.records.push({ method, path, scenario, requestId, simulated: true, at: new Date().toISOString() });
    if (this.records.length > 1000) this.records.shift();
  }

  start(sourceRef, metadata, scenario) {
    const id = `stub-session-${metadata.request_id}`.slice(0, 160);
    const value = { id, requestId: metadata.request_id, sourceRef, scenario, state: 'RUNNING' };
    this.sessions.set(id, value);
    return streamSession(id);
  }

  session(id) {
    const value = this.sessions.get(id);
    if (!value) return null;
    return streamSession(id, value.state, value.state === 'STOPPED' ? '1' : '0');
  }

  events(id, cursor, scenarioOverride) {
    const value = this.sessions.get(id);
    if (!value) return null;
    return streamEvents(value, scenarioOverride || value.scenario, cursor);
  }

  stop(id) {
    const value = this.sessions.get(id);
    if (!value) return null;
    value.state = 'STOPPED';
    return { provider_session_id: id, confirmed: true, state: 'STOPPED' };
  }

  reset() { this.sessions.clear(); this.records.length = 0; }
}

module.exports = { StubState };
