const { json, body } = require('./http.cjs')
const scenarios = ['success', 'immediate', 'empty', 'failed', 'unknown', 'slow']
const downloads = ['normal', 'expired', 'denied', 'interrupted']
const stops = ['confirmed', 'unknown', 'unsupported']
const streamScenarios = ['normal', 'empty', 'failed']
const option = values => values.map(v => `<option>${v}</option>`).join('')
async function control(req, res, url, state) {
  if (url.pathname === '/_demo/requests') { json(res, state.requests); return true }
  if (url.pathname === '/_demo/config' && req.method === 'POST') {
    const bytes = await body(req, 8192)
    const data = (req.headers['content-type'] || '').includes('json')
      ? JSON.parse(bytes) : Object.fromEntries(new URLSearchParams(bytes.toString()))
    if (scenarios.includes(data.scenario)) state.config.scenario = data.scenario
    if (downloads.includes(data.download)) state.config.download = data.download
    if (data.available !== undefined) state.config.available = data.available === true || data.available === 'true'
    if (data.videoAvailable !== undefined) state.config.videoAvailable = data.videoAvailable === true || data.videoAvailable === 'true'
    if (data.streamAvailable !== undefined) state.config.streamAvailable = data.streamAvailable === true || data.streamAvailable === 'true'
    if (stops.includes(data.stop)) state.config.stop = data.stop
    if (streamScenarios.includes(data.streamScenario)) state.config.streamScenario = data.streamScenario
    if (data.forceViewer !== undefined) state.config.forceViewer = data.forceViewer === true || data.forceViewer === 'true'
    if (data.queryDelay !== undefined) state.config.queryDelay = Math.min(15000, Math.max(0, Number(data.queryDelay) || 0))
    if (data.clearRequests) state.requests.length = 0
    if ((req.headers['content-type'] || '').includes('json')) json(res, state.config)
    else { res.writeHead(303, { Location: '/_demo' }); res.end() }
    return true
  }
  if (url.pathname !== '/_demo') return false
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' })
  res.end(`<!doctype html><html lang="zh"><meta charset="utf-8"><title>04b 本地模拟控制</title>
    <body style="font:16px system-ui;max-width:760px;margin:50px auto;line-height:1.8">
    <h1>04b 本地模拟控制</h1><p>此服务只用于前端验收，没有调用 GPU 或原业务后端。</p>
    <p>账号 demo（有 AI 菜单）、viewer（无 AI 菜单）、other（另一资产归属）。密码 demo，验证码 1234。</p>
    <form method="post" action="/_demo/config">
    <p><label>提交场景 <select name="scenario">${option(scenarios)}</select></label></p>
    <p><label>能力可用 <select name="available"><option>true</option><option>false</option></select></label></p>
    <p><label>视频能力可用 <select name="videoAvailable"><option>true</option><option>false</option></select></label></p>
    <p><label>实时能力可用 <select name="streamAvailable"><option>true</option><option>false</option></select></label></p>
    <p><label>停止场景 <select name="stop">${option(stops)}</select></label></p>
    <p><label>实时事件场景 <select name="streamScenario">${option(streamScenarios)}</select></label></p>
    <p><label>强制只读菜单 <select name="forceViewer"><option>false</option><option>true</option></select></label></p>
    <p><label>下载场景 <select name="download">${option(downloads)}</select></label></p>
    <p><label>查询延迟（毫秒） <input name="queryDelay" type="number" min="0" max="15000" value="0"></label></p>
    <button>应用模拟设置</button></form><pre>${JSON.stringify(state.config, null, 2)}</pre>
    <p><a href="/ai/inference">图片检测</a> · <a href="/ai/video">上传视频</a> · <a href="/ai/streams">实时事件</a> · <a href="/ai/history">任务历史</a></p>
    <p><a href="/_demo/input.png" download="input.png">下载固定图片样例</a> · <a href="/_demo/input.mp4" download="input.mp4">下载模拟视频样例</a> · <a href="/_demo/requests">请求记录</a></p>
    <p>success 为等待后成功；immediate 为 200；empty 为有效空结果；failed 为任务失败；unknown 为结果未知；slow 等待 60 秒。</p>
    </body></html>`)
  return true
}
module.exports = { control }
