# 04b 前端模拟验收

本包从公共起点 `f242a02` 交付 OpenSpec 平台 4.9 与实时流 4.1—4.3 的 04b 实现，等待 00 独立验收后再勾选。业务契约版本为 1.1.0，冻结提交 `1177de8`。运行页面只调用现有请求封装下的 `/ai/v1`，本目录的模拟工具不被正式页面引用。

## 启动与复现

在 `04b-frontend/code` 工作树执行。需要 Node.js（本轮使用 v24.14.0）以及本工作树自己的 `frontend-vue/node_modules`；不复用原 WGAI 的依赖目录。依赖版本保持仓库锁定值。

```sh
cd frontend-vue
npm ci --legacy-peer-deps --no-audit --no-fund
NODE_OPTIONS='--openssl-legacy-provider --max-old-space-size=4096' npm run build
cd ..
node --test backend-github/integrations/ai-contracts/acceptance/04b-frontend/tests/*.test.cjs
lsof -nP -iTCP:18105 -iTCP:19105 -sTCP:LISTEN
node backend-github/integrations/ai-contracts/acceptance/04b-frontend/mock/server.cjs
```

检查端口输出：已有监听者时先确认其归属，不终止其他包进程。服务绑定 `127.0.0.1`，冲突会退出。先完成构建再启动或刷新页面，重新构建会暂时移除 `dist`。安装后核对依赖/锁文件没有改动；本轮 npm 引起的 yarn.lock 重写已恢复到公共基线，未提交依赖变更。

打开 <http://127.0.0.1:18105/_demo>，从控制页下载固定上传样例并选择场景。点击“打开 AI 推理”，使用本地夹具登录：账号 `demo`（AI 菜单）、`viewer`（无 AI 菜单）或 `other`（另一资产归属），密码 `demo`，测试验证码 `1234`。这些身份只由本目录模拟服务接受，不访问原数据库。

1. 图片检测继续上传 PNG/JPEG，确认默认参数 `0.5 / 10 / true`，提交任务。
2. `success` 返回 202，等待约 4.5 秒后完成。查看检测列表、图片尺寸，点击预览和下载。
3. 刷新任务详情只查询原任务；从历史查看已提交任务，筛选状态。数据保存在服务内存，浏览器刷新不丢失，服务重启重置。
4. 控制页切换 `immediate`（200）、`empty`、`failed`（供应商鉴权失败）、`unknown`、`slow`（60 秒）。每次“开始新任务”才产生新提交身份。
5. 能力设为 false 后刷新能力，应显示停用原因。上传超过能力声明的 10 MiB 或其他类型文件应在提交前拦截。
6. 下载模式 `expired / denied / interrupted` 分别验证 410、404 和中断；恢复 `normal` 可再次下载。JSON 错误不会保存成图片。
7. `queryDelay=12000` 后查询慢任务 A，马上从历史打开任务 B；A 返回后 B 不变。离开后请求记录中不应再出现 A 的轮询。
8. 上传视频分析只接受 MP4，默认参数为 `0.5 / 1000ms / 100 / true / false`；成果显示带时间偏移的事件、授权截图和可选标注视频。
9. 实时事件页面只选择本地不透明来源编号；disabled 来源仍显示原因但无法启动。运行页按游标查询并去重，离开不调用停止，只有按钮明确发出停止请求。
10. 控制页的 `normal / empty / failed` 覆盖实时运行、空事件和失败；停止 `confirmed / unknown / unsupported` 分别覆盖确认、结果未知和不支持。
11. `viewer` 只显示夹具首页，直接访问 `/ai/inference`、`/ai/video` 或 `/ai/streams` 显示不存在或无权限。`demo` 的旧训练、缺失 easy 入口显示停用页；刷新旧路径仍停用。

控制页的 `/_demo/config`、`/_demo/requests`、`/_demo/input.png` 是测试工具端点，**不是业务 API 契约扩展**。请求记录不保存登录口令、令牌、上传内容或幂等 key。模拟任务、能力与结果保持 `simulated=true`；资产沿用冻结结构，不增加 simulated 字段。

## 第五轮实际验收记录

2026-09-04 在本地生产构建和内置浏览器完成 1.1.0 增量检查。自动检查使用实际页面脚本、API 模块、轮询服务和冻结 OpenAPI 样例；生命周期测试调用 Vue 2 页面钩子，浏览器验证实际路由行为。原图片回归继续通过。

| 检查 | 结果和证据 |
|---|---|
| 上传 → 202 → 等待 → 成果预览/下载 | 通过；`evidence/success.txt`、`success.png`、`download.json` |
| 有效空成果 | 通过；`empty.txt`，显示“未检测到目标” |
| UNKNOWN / 供应商鉴权错误 | 通过；`unknown.txt`、`provider-auth.txt`，无自动重提，用户登录仍有效 |
| 410 / 404 / 下载中断 | 通过；`expired.txt`、`download-denied.txt`、`download-interrupted.txt` |
| 能力停用、输入超限 | 通过；`capability-disabled.txt`、`oversized.txt`；不支持类型另有自动检查 |
| 离开、A → B 迟到成功、刷新历史 | 通过；`lifecycle-requests.json`、`late-response.txt`、`history-refresh.txt`，该场景仅一次 POST /infer |
| 失活/销毁、迟到失败、残留定时器、重复激活、全部终态 | 自动检查通过；`tests/polling.test.cjs`、`tests/pages.test.cjs` |
| 预览地址释放、迟到 Blob、媒体类型/长度错误 | 自动检查通过；`tests/preview.test.cjs`、`tests/navigation-assets.test.cjs` |
| 同一提交复用 key/body、刷新不提交 | 自动检查与请求记录通过；无自动推理重试 |
| 导航权限、转换幂等、旧入口停用 | 通过；`viewer-navigation.txt`、`viewer-direct-route.txt`、`legacy-disabled.txt` 和自动检查 |
| 200/202、分页游标、模拟鉴权/归属、响应契约 | 自动检查通过；`tests/mock-contract.test.cjs` 对冻结 OpenAPI 校验 |
| 构建、针对性静态检查、文件归属/规模 | 通过；`evidence/verification.json`，测试结果见 `unit-tests.txt` |
| 上传视频、事件时间线、截图、PENDING 取消 | 通过；`tests/mock-contract.test.cjs`、`tests/video-stream-pages.test.cjs`、`evidence/round5-browser.md` |
| 来源权限、空/失败/运行/终态、确认/未知/不支持停止 | 通过；浏览器记录见 `evidence/round5-browser.md`，协议与生命周期见新增自动测试 |
| 游标推进、事件去重、会话代次隔离、离开不停止 | 通过；`tests/stream-polling.test.cjs`、`tests/video-stream-pages.test.cjs` |
| 1.1.0 OpenAPI、OpenSpec strict、Graphify | 通过；摘要见 `evidence/round5-verification.json` |

自动检查命令见上。`node backend-github/integrations/ai-contracts/acceptance/04b-frontend/check-scope.cjs` 可重新检查归属、规模、冻结文件和源码摘要。针对性静态检查必须显式覆盖仓库原有的 `/src` 忽略规则：

```sh
cd frontend-vue
./node_modules/.bin/eslint --no-ignore --no-fix 'src/api/ai/**/*.js' 'src/services/ai/**/*.js' 'src/components/ai/**/*.vue' 'src/views/ai/*.vue'
```

## 解释与限制

- 本轮 27 个自动检查通过。页面缓存失活/激活通过真实页面钩子的脚本检查；新路由设置 keepAlive=false，未声称浏览器启用了缓存并做了缓存端到端验收。
- 下载事件通知未被浏览器工具捕获，但浏览器已实际保存文件。验收核对落盘文件 79 字节及 SHA-256 与冻结样例相同，见 `download.json`。
- 旧全局管理页眉仍尝试原用户 WebSocket，模拟服务返回拒绝；新 AI 模块没有使用该通道。保留管理区的告警不代表新任务失败。
- 构建报告原有订单页面的 CSS 顺序告警，以及 Browserslist 数据陈旧和包体积告警。Graphify 对 6 个既有 Vue 文件报告部分解析告警；详见验证记录。本包新模块没有这些解析告警。
- 模拟服务只为可重复演示准备，不是业务后端实现；不证明真实 GPU、真实供应商或正式权限已验收。05 未确认真实视频/流接口前，生产能力必须继续 disabled。
- 00 应从 `f242a02` 串行合入本包并独立验收平台 4.9 与流 4.1—4.3。总 OpenSpec 勾选、真实联调、归档、远程推送及发布候选不在本次交付内。
