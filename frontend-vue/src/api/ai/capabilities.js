import { axios } from '@/utils/request'
import { unwrapResponse } from './response'

export function listCapabilities() {
  return axios({ url: '/ai/v1/capabilities', method: 'get', params: { _t: undefined } })
    .then(unwrapResponse)
}
