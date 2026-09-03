const { envelope } = require('./fixtures.cjs')
const { json, fail, body } = require('./http.cjs')
function menu(path, component, title) {
  return { path, name: path.replace(/\W/g, '_'), component, route: '1', hidden: false,
    meta: { title, icon: 'appstore', keepAlive: false, internalOrExternal: false } }
}
function owner(req) {
  return ({ 'mock-demo': 'demo', 'mock-viewer': 'viewer', 'mock-other': 'other' })[req.headers['x-access-token']]
}
async function session(req, res, url) {
  if (url.pathname.startsWith('/jeecg-boot/sys/randomImage/')) {
    const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="110" height="40"><rect width="110" height="40" fill="#eee"/><text x="15" y="28" font-size="24">1234</text></svg>'
    json(res, envelope('data:image/svg+xml;base64,' + Buffer.from(svg).toString('base64'))); return true
  }
  if (url.pathname === '/jeecg-boot/sys/login') {
    const data = JSON.parse(await body(req))
    if (!['demo', 'viewer', 'other'].includes(data.username) || data.password !== 'demo' || data.captcha !== '1234') {
      fail(res, 401, 'UNAUTHENTICATED', '模拟账号 demo/viewer/other；密码 demo；验证码 1234'); return true
    }
    json(res, envelope({ token: 'mock-' + data.username, userInfo: { id: data.username,
      username: data.username, realname: '本地模拟用户', avatar: '', orgCode: 'mock' },
    sysAllDictItems: {}, multi_depart: 1, tenantList: [] })); return true
  }
  if (url.pathname === '/jeecg-boot/sys/permission/getUserPermissionByToken') {
    const user = owner(req)
    if (!user) { fail(res, 401, 'UNAUTHENTICATED', '请登录本地模拟账号'); return true }
    const home = menu('/dashboard/analysis', 'ai/DisabledEntryPage', '首页')
    const menus = [home]
    if (user !== 'viewer') {
      home.redirect = '/ai/inference'
      menus.push(menu('/tab/TabAiModelList', 'tab/TabAiModelList', '模型登记'),
        menu('/tab/TabAiHistoryList', 'tab/TabAiHistoryList', '旧识别历史'),
        menu('/tab/TabAiModelBundList', 'tab/TabAiModelBundList', '模型绑定'),
        menu('/train/TabTrainPythonList', 'train/TabTrainPythonList', '旧训练入口'),
        menu('/easy', 'easy', '旧在线识别'))
    }
    json(res, envelope({ menu: menus, auth: [], allAuth: [], sysSafeMode: false })); return true
  }
  const emptyRoutes = ['/sys/logout', '/sys/annountCement/listByUser', '/sys/annountCement/queryById',
    '/sys/dict/queryAllDictItems', '/sys/permission/queryListByCode', '/sys/category/loadTreeData',
    '/sys/user/getUserSectionInfoByToken']
  if (emptyRoutes.some(p => url.pathname === '/jeecg-boot' + p) ||
      /^\/jeecg-boot\/tab\/tabAi(Model|History|ModelBund)\/list$/.test(url.pathname)) {
    json(res, envelope({ records: [], total: 0, anntMsgList: [], sysMsgList: [], anntMsgTotal: 0, sysMsgTotal: 0 })); return true
  }
  return false
}
module.exports = { session, owner }
