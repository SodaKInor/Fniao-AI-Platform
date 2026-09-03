import { axios } from '@/utils/request'
import { resourceId, unwrapResponse } from './response'

export function submitInference(request, idempotencyKey, waitMillis) {
  return axios({ url: '/ai/v1/infer',
    method: 'post',
    data: request,
    headers: { 'Idempotency-Key': idempotencyKey },
    params: { waitMillis: Math.min(1500, Math.max(0, waitMillis)) } }).then(unwrapResponse)
}

export function getJob(id) {
  return axios({ url: '/ai/v1/jobs/' + resourceId(id),
    method: 'get',
    params: { _t: undefined } }).then(unwrapResponse)
}

export function listJobs({ cursor, state } = {}) {
  return axios({ url: '/ai/v1/jobs',
    method: 'get',
    params: { cursor, state: state || undefined, limit: 20, _t: undefined } }).then(unwrapResponse)
}
