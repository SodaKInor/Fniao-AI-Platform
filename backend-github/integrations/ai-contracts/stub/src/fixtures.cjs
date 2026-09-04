'use strict';

const ARTIFACT_HASH = '6a6d02559b8c8014f8840fe7faeb3c550fca2bce0303355ae07f0722089dca4d';
const ARTIFACT_SIZE = 79;

function artifact(name = 'annotated.png') {
  const referenceName = name.replace(/\.[A-Za-z0-9]+$/, '');
  return { reference: `/artifacts/${referenceName}`, media_type: 'image/png', size_bytes: ARTIFACT_SIZE,
    sha256: ARTIFACT_HASH };
}

function image(metadata, scenario) {
  const empty = scenario === 'empty';
  const reference = scenario === 'artifact-interrupted' ? 'interrupted.png' : 'annotated.png';
  return {
    contract_version: '0.1-draft', request_id: metadata.request_id, status: 'succeeded', simulated: true,
    data: { schema_version: 'detection.v1', image_width: 16, image_height: 16,
      detections: empty ? [] : [{ label: 'synthetic-square', score: 0.95,
        box: { x: 0.25, y: 0.25, width: 0.5, height: 0.5 } }] },
    artifacts: empty || metadata.parameters.annotate === false ? [] : [{ ...artifact(reference),
      file_name: reference, expires_at: new Date(Date.now() + 3600000).toISOString() }]
  };
}

function video(metadata, scenario) {
  const empty = scenario === 'empty';
  const reference = scenario === 'artifact-interrupted' ? 'interrupted.png' : 'snapshot.png';
  return {
    simulated: true,
    provider_request_id: metadata.request_id,
    provider_version: 'stub-simulated-v1',
    events: empty ? [] : [{ event_id: `stub-video-event-${metadata.request_id}`, offset_ms: 1250,
      event_type: 'synthetic-person', score: 0.91,
      ...(metadata.parameters.include_snapshots === false ? {} : { snapshot: artifact(reference) }) }]
  };
}

function streamSession(providerSessionId, state = 'RUNNING', cursor = '0') {
  return { provider_session_id: providerSessionId, state, cursor, provider_version: 'stub-simulated-v1' };
}

function streamEvents(session, scenario, cursor) {
  if (scenario === 'empty' || cursor === '1') return { items: [], next_cursor: '1' };
  const snapshot = artifact(scenario === 'artifact-interrupted' ? 'interrupted.png' : 'stream-snapshot.png');
  const first = { event_id: `stub-stream-event-${session.id}-1`, offset_ms: 2500,
    occurred_at: '2026-09-04T12:00:02.500Z', event_type: 'synthetic-person', score: 0.93, snapshot };
  if (scenario === 'duplicate-events') return { items: [first, { ...first }], next_cursor: '1' };
  if (scenario === 'out-of-order-events') {
    const earlier = { ...first, event_id: `${first.event_id}-late`, offset_ms: 1200,
      occurred_at: '2026-09-04T12:00:01.200Z' };
    return { items: [first, earlier], next_cursor: '1' };
  }
  return { items: [first], next_cursor: '1' };
}

module.exports = { ARTIFACT_HASH, ARTIFACT_SIZE, image, video, streamSession, streamEvents };
