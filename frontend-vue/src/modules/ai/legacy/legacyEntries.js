// Scope follows the round-6.5 retain/retire decision. Database history and
// management APIs remain intact, but retired UI families never reach dynamic imports.
const missingVideoPages = [
  'video/TabAiWarningList',
  'video/TabAiVideoSettingList',
  'video/TabAiClickpicSettingList',
  'video/TabAiSubscriptionNewList'
]

export function isDisabledEntry(component = '') {
  const name = component.replace(/^\//, '').replace(/\.vue$/, '')
  return /^(maxkb|tchat|teasy|train|face|szr|audio)\//.test(name) ||
    name === 'easy' || name === 'tab/live/audio' ||
    name.startsWith('tab/testAI') || missingVideoPages.includes(name)
}

export function disableLegacyMenus(menus) {
  return (menus || []).map(item => {
    const menu = { ...item, meta: { ...item.meta } }
    if (isDisabledEntry(menu.component)) {
      menu.component = 'modules/ai/legacy/DisabledEntryPage'
      menu.meta.url = ''
      menu.meta.internalOrExternal = false
      delete menu.redirect
    }
    if (item.children) menu.children = disableLegacyMenus(item.children)
    return menu
  })
}
