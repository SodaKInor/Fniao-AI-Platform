const { test } = require('node:test')
const assert = require('node:assert/strict')
const { loadSource } = require('./load-source.cjs')
const { disableLegacyMenus, isDisabledEntry } = loadSource('modules/ai/legacy/legacyEntries.js')

test('retired menu entries become inert pages while retained history and pending video stay reachable', () => {
  for (const name of ['train/TabTrainPythonList', 'face/TabFaceTest', 'szr/SzrThreeJs',
    'audio/audio', 'tab/live/audio', 'video/TabAiWarningList', 'easy', 'tab/testAI/Test',
    'maxkb/userchat', 'tchat/userchat', 'teasy/TabEasyConfigList']) {
    assert.equal(isDisabledEntry(name), true, name)
  }
  for (const name of ['video/TabVideoUtilList', 'tab/live/AddressList',
    'tab/livecanvas/AddressList']) {
    assert.equal(isDisabledEntry(name), true, name)
  }
  for (const name of ['tab/TabAiModelBundList', 'tab/TabAiHistoryList']) {
    assert.equal(isDisabledEntry(name), false, name)
  }
  const input = [{ component: 'layouts/RouteView', children: [{ component: 'easy',
    path: '/easy', redirect: '/old', meta: { url: 'old-execution', title: '在线识别' } }] }]
  const output = disableLegacyMenus(input)
  assert.equal(output[0].children[0].component, 'modules/ai/legacy/DisabledEntryPage')
  assert.equal(output[0].children[0].meta.url, '')
  assert.equal(output[0].children[0].redirect, undefined)
  assert.equal(input[0].children[0].component, 'easy')
  assert.equal(JSON.stringify(disableLegacyMenus(output)), JSON.stringify(output))
})

test('calling retired component methods directly cannot send execution or upload requests', () => {
  let notices = 0
  const context = { $message: { warning() { notices++ } },
    $router: { push() { assert.fail('unexpected legacy navigation') } } }
  const mocks = { 'ant-design-vue': { message: context.$message },
    './modules/TabAiModelBundModal': {},
    '@/api/manage': new Proxy({}, { get() { return () => assert.fail('unexpected request') } }) }
  for (const name of ['tab/TabAiModelBundList.vue', 'video/TabAiModelBundList.vue']) {
    const component = loadSource('views/' + name, mocks).default
    component.methods.handleIdentify.call(context)
    component.methods.handleIdentifyClose.call(context)
    component.methods.handleOpenVideo.call(context)
  }
  assert.equal(notices, 6)
})
