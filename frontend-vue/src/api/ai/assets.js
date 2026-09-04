import { axios } from '@/utils/request'
import { resourceId, unwrapResponse } from './response'

export function uploadAsset(file) {
  const data = new FormData()
  data.append('file', file)
  // The browser supplies the multipart boundary; never use the old public upload.
  return axios({ url: '/ai/v1/assets', method: 'post', data }).then(unwrapResponse)
}

export function downloadAsset(asset) {
  return axios({ url: '/ai/v1/assets/' + resourceId(asset.assetId) + '/content',
    method: 'get',
    responseType: 'blob',
    params: { _t: undefined } }).then(blob => {
    if (!(blob instanceof Blob) || !['image/png', 'image/jpeg', 'video/mp4'].includes(blob.type) ||
        blob.type !== asset.mediaType || blob.size !== asset.sizeBytes) {
      throw new Error('成果文件不完整或格式不匹配，请重新下载')
    }
    return blob
  }).catch(async error => {
    const blob = error.response && error.response.data
    if (blob instanceof Blob && blob.type.includes('json')) {
      try { error.detail = JSON.parse(await blob.text()).result } catch (_) { /* keep transport error */ }
    }
    throw error
  })
}

export function downloadSnapshotAsset(assetId) {
  return axios({ url: '/ai/v1/assets/' + resourceId(assetId) + '/content',
    method: 'get',
responseType: 'blob',
params: { _t: undefined } }).then(blob => {
    if (!(blob instanceof Blob) || !['image/png', 'image/jpeg'].includes(blob.type) || !blob.size) {
      throw new Error('截图文件为空或格式不匹配，请重新下载')
    }
    return blob
  }).catch(async error => {
    const blob = error.response && error.response.data
    if (blob instanceof Blob && blob.type.includes('json')) {
      try { error.detail = JSON.parse(await blob.text()).result } catch (_) { /* keep transport error */ }
    }
    throw error
  })
}
