import { disableLegacyMenus } from './legacyEntries'

const aiManagement = ['tab/TabAiModelList', 'tab/TabAiModelBundList',
  'video/TabAiModelBundList', 'tab/TabAiBaseList', 'tab/TabAiHistoryList']

function hasAiPermission(menus) {
  return menus.some(menu => aiManagement.includes(menu.component) ||
    (menu.children && hasAiPermission(menu.children)))
}

function aiMenu(path, name, title, component, hidden = false) {
  return { path,
    name,
    component,
    hidden,
    route: '1',
    meta: { title, icon: 'experiment', keepAlive: false, internalOrExternal: false } }
}

export function prepareAiMenus(menus) {
  const permitted = hasAiPermission(menus || [])
  // Our reserved subtree is reconstructed so repeated permission loads never duplicate it.
  const result = disableLegacyMenus(menus).filter(menu => menu.path !== '/ai')
  if (permitted) {
    const group = aiMenu('/ai', 'AiWorkspace', 'AI 工作台', 'layouts/RouteView')
    group.redirect = '/ai/inference'
    group.children = [
      aiMenu('/ai/inference', 'AiInference', '图片检测', 'ai/InferencePage'),
      aiMenu('/ai/video', 'AiVideoInference', '上传视频分析', 'ai/VideoInferencePage'),
      aiMenu('/ai/streams', 'AiStreamStart', '实时事件分析', 'ai/StreamStartPage'),
      aiMenu('/ai/history', 'AiHistory', '任务历史', 'ai/HistoryPage'),
      aiMenu('/ai/jobs/:requestId', 'AiJobDetail', '任务详情', 'ai/JobDetailPage', true),
      aiMenu('/ai/streams/:sessionId', 'AiStreamSession', '实时会话', 'ai/StreamSessionPage', true)
    ]
    result.push(group)
  }
  return result
}
