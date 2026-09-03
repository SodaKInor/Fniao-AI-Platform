const { test } = require('node:test')
const assert = require('node:assert/strict')
const { loadSource } = require('./load-source.cjs')

test('independent navigation inherits exact AI management menus and is idempotent', () => {
  const { prepareAiMenus } = loadSource('services/ai/navigation.js')
  const input = [{ component: 'layouts/RouteView', path: '/tab', children: [
    { component: 'tab/TabAiModelList', path: '/tab/TabAiModelList' }] }]
  const output = prepareAiMenus(input)
  assert.equal(output[1].path, '/ai'); assert.equal(output[1].children.length, 3)
  assert.equal(output[1].children[2].hidden, true)
  assert.equal(JSON.stringify(prepareAiMenus(output)), JSON.stringify(output))
  assert.equal(input.length, 1)
  assert.equal(prepareAiMenus([{ component: 'tab/TabChatQaList' }]).some(m => m.path === '/ai'), false)
  assert.equal(prepareAiMenus([]).length, 0)
})

test('downloads preserve authentication wrapper, validate bytes, and decode JSON failures', async () => {
  let response = new Blob(['abc'], { type: 'image/png' })
  const { downloadAsset } = loadSource('api/ai/assets.js', {
    '@/utils/request': { axios(config) {
      assert.equal(config.url, '/ai/v1/assets/asset_A/content')
      assert.equal(config.responseType, 'blob')
      return response instanceof Error ? Promise.reject(response) : Promise.resolve(response)
    } }
  })
  const asset = { assetId: 'asset_A', mediaType: 'image/png', sizeBytes: 3 }
  assert.equal(await downloadAsset(asset), response)
  response = new Blob(['ab'], { type: 'image/png' }); await assert.rejects(downloadAsset(asset), /不完整/)
  response = new Blob(['abc'], { type: 'text/html' }); await assert.rejects(downloadAsset(asset), /格式/)
  response = new Error('410')
  response.response = { data: new Blob([JSON.stringify({ result: { errorCode: 'ASSET_EXPIRED' } })], { type: 'application/json' }) }
  await assert.rejects(downloadAsset(asset), error => error.detail.errorCode === 'ASSET_EXPIRED')
  assert.throws(() => downloadAsset({ assetId: '../../outside' }), /编号无效/)
})
