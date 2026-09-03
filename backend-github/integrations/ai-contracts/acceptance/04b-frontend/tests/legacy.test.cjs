const { test } = require('node:test')
const assert = require('node:assert/strict')
const { loadSource } = require('./load-source.cjs')
const { disableLegacyMenus, isDisabledEntry } = loadSource('services/ai/legacyEntries.js')

test('retired menu entries become inert pages; management and pending businesses remain', () => {
  for (const name of ['train/TabTrainPythonList', 'face/TabFaceTest', 'szr/SzrThreeJs',
    'audio/audio', 'tab/live/audio', 'video/TabAiWarningList', 'easy', 'tab/testAI/Test']) {
    assert.equal(isDisabledEntry(name), true, name)
  }
  for (const name of ['tab/TabAiModelBundList', 'tab/TabAiHistoryList',
    'video/TabVideoUtilList', 'tab/live/AddressList', 'maxkb/userchat', 'teasy/TabEasyConfigList']) {
    assert.equal(isDisabledEntry(name), false, name)
  }
  const input = [{ component: 'layouts/RouteView', children: [{ component: 'easy',
    path: '/easy', redirect: '/old', meta: { url: 'old-execution', title: '在线识别' } }] }]
  const output = disableLegacyMenus(input)
  assert.equal(output[0].children[0].component, 'ai/DisabledEntryPage')
  assert.equal(output[0].children[0].meta.url, '')
  assert.equal(output[0].children[0].redirect, undefined)
  assert.equal(input[0].children[0].component, 'easy')
  assert.equal(JSON.stringify(disableLegacyMenus(output)), JSON.stringify(output))
})

test('calling retired component methods directly cannot send execution or upload requests', () => {
  let notices = 0
  const context = { $message: { warning() { notices++ } } }
  const mocks = { 'ant-design-vue': { message: context.$message },
    './modules/TabAiModelBundModal': {},
    '@/api/manage': new Proxy({}, { get() { return () => assert.fail('unexpected request') } }) }
  for (const name of ['tab/TabAiModelBundList.vue', 'video/TabAiModelBundList.vue']) {
    const component = loadSource('views/' + name, mocks).default
    component.methods.handleIdentify.call(context)
    component.methods.handleIdentifyClose.call(context)
  }
  for (const name of ['audio/audio.vue', 'tab/live/audio.vue']) {
    const component = loadSource('views/' + name, mocks).default
    component.methods.uploadAudio.call(context)
    component.methods.AiAudio.call(context)
    if (component.methods.initWebSocket) component.methods.initWebSocket.call(context)
  }
  assert.equal(notices, 8)
})
