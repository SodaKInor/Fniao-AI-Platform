export function unwrapResponse(response) {
  if (response && response.success === true && response.result !== undefined) return response.result
  const detail = response && response.result
  const error = new Error((detail && detail.message) || (response && response.message) || '业务请求失败')
  error.detail = detail
  throw error
}

export function resourceId(id) {
  if (!/^[A-Za-z0-9_-]{1,80}$/.test(id || '')) throw new Error('任务或资产编号无效')
  return encodeURIComponent(id)
}
