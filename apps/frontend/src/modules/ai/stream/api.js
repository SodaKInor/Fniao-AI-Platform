import { axios } from '@/utils/request'
import { resourceId, unwrapResponse } from '../result/response'

export function listStreamSources() {
  return axios({ url: '/ai/v1/stream-sources',
method: 'get',
    params: { _t: undefined } }).then(unwrapResponse)
}

export function startStreamSession(request, idempotencyKey) {
  return axios({ url: '/ai/v1/stream-sessions',
method: 'post',
data: request,
    headers: { 'Idempotency-Key': idempotencyKey } }).then(unwrapResponse)
}

export function getStreamSession(id) {
  return axios({ url: '/ai/v1/stream-sessions/' + resourceId(id),
method: 'get',
    params: { _t: undefined } }).then(unwrapResponse)
}

export function getStreamEvents(id, { cursor, limit = 50 } = {}) {
  return axios({ url: '/ai/v1/stream-sessions/' + resourceId(id) + '/events',
method: 'get',
    params: { cursor: cursor || undefined, limit: Math.min(200, Math.max(1, limit)), _t: undefined } }).then(unwrapResponse)
}

export function stopStreamSession(id) {
  return axios({ url: '/ai/v1/stream-sessions/' + resourceId(id) + '/stop',
    method: 'post' }).then(unwrapResponse)
}
