// Scope follows acceptance/01-foundation/ENTRYPOINTS.md. Pending businesses
// (continuous video, MaxKB and existing configuration/history) are retained.
const missingVideoPages = [
  'video/TabAiWarningList',
  'video/TabAiVideoSettingList',
  'video/TabAiClickpicSettingList',
  'video/TabAiSubscriptionNewList'
]

export function isDisabledEntry(component = '') {
  const name = component.replace(/^\//, '').replace(/\.vue$/, '')
  return /^(train|face|szr|audio)\//.test(name) ||
    name === 'easy' || name === 'tab/live/audio' ||
    name.startsWith('tab/testAI') || missingVideoPages.includes(name)
}

export function disableLegacyMenus(menus) {
  return (menus || []).map(item => {
    const menu = { ...item, meta: { ...item.meta } }
    if (isDisabledEntry(menu.component)) {
      menu.component = 'ai/DisabledEntryPage'
      menu.meta.url = ''
      menu.meta.internalOrExternal = false
      delete menu.redirect
    }
    if (item.children) menu.children = disableLegacyMenus(item.children)
    return menu
  })
}
