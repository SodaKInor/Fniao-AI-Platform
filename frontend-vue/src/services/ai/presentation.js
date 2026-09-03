export const stateLabels = {
  PENDING: '等待派发',
  DISPATCHING: '正在派发',
  WAITING: '等待处理结果',
  FETCHING_RESULT: '正在保存成果',
  SUCCEEDED: '已完成',
  FAILED: '处理失败',
  UNKNOWN: '结果未确认',
  CANCELLED: '已取消'
}

const errorLabels = {
  NOT_FOUND: '记录不存在或无权访问',
  ASSET_EXPIRED: '成果已过期，无法下载',
  LIMIT_EXCEEDED: '超过输入或任务限额',
  UNSUPPORTED_MEDIA: '不支持此文件类型',
  CAPABILITY_UNAVAILABLE: '当前能力不可用',
  IDEMPOTENCY_CONFLICT: '此提交编号已用于不同内容',
  PROVIDER_AUTH: '处理服务鉴权失败，请联系管理员；当前登录仍有效',
  PROVIDER_OFFLINE: '处理服务暂不可用',
  RESULT_UNKNOWN: '结果无法确认，原请求可能已经处理',
  PROVIDER_TIMEOUT: '处理服务响应超时',
  PROVIDER_PROTOCOL: '处理服务返回格式不兼容',
  ARTIFACT_TRANSFER: '成果传输失败',
  ARTIFACT_EXPIRED: '成果来源已过期',
  FORBIDDEN: '无权执行此操作',
  UNAUTHENTICATED: '登录已失效，请重新登录'
}

export function errorMessage(error) {
  if (error && error.message === 'Network Error') return '网络连接中断，请重新查询或下载'
  if (error && /timeout/i.test(error.message || '')) return '请求超时，请重新查询；任务不会自动重新提交'
  const detail = error && (error.detail || (error.response && error.response.data && error.response.data.result) || error)
  return (detail && errorLabels[detail.errorCode]) || (detail && detail.message) || '请求失败，请稍后重新查询'
}

export function capabilitySupported(capability) {
  return capability && capability.code === 'image-detection.v1' &&
    capability.parametersSchema === 'detection.v1' &&
    capability.inputMediaTypes.some(type => ['image/png', 'image/jpeg'].includes(type))
}

export function supportedResult(result) {
  const data = result && result.data
  return !!(data && data.schemaVersion === 'detection.v1' && Array.isArray(data.detections) &&
    Number.isInteger(data.imageWidth) && data.imageWidth > 0 &&
    Number.isInteger(data.imageHeight) && data.imageHeight > 0 && Array.isArray(result.artifacts) &&
    result.artifacts.length <= 1 && result.artifacts.every(a => ['image/png', 'image/jpeg'].includes(a.mediaType)))
}
