# 04b-frontend 第五轮交接

状态：`READY_FOR_00_ACCEPTANCE`。04b 的 1.1.0 前端实现、模拟协议检查和浏览器回归已完成；尚未由 00 接受，不释放 05。

- 共同起点：`f242a027a2e2827f5445bea80e517c472ff1e3c9`
- 冻结契约：`1177de8be45123d043d7cb26b845ee9d94c26784`（1.1.0）
- 实现提交：`5595bb1abe340529e40209f6f3a2483e8023f7d3`
- 分支：`work/remote-inference/04b-frontend`
- 工作树：`/Users/twowt88/Documents/ChatGPT/WGAI-parallel/04b-frontend/code`

## 合入内容

1. 保持图片接口兼容，新增上传视频页、MP4 类型/大小限制、五项有界参数、固定幂等提交、任务类型化历史和 PENDING 取消按钮。
2. 视频成功页展示带毫秒偏移的事件时间线、授权截图和可选标注视频；空成果、UNKNOWN、失败和下载错误均有独立展示。
3. 新增来源选择、会话启动、会话状态、事件游标、截图与明确停止页面。浏览器只发送本地 `streamSourceId` 和有界参数。
4. 流轮询为单串行链，具备代次/会话身份隔离、游标推进、事件 ID 去重和 STOPPED/FAILED/UNKNOWN 终态保护。
5. 离开、失活或销毁页面只停止本地轮询；不会调用远程停止。STOP_REQUESTED 保持待确认，不显示为 STOPPED。
6. 导航仅由既有 AI 管理权限派生；无权限不生成工作台，隐藏详情不能直接访问。旧图片与第四轮停用入口继续保留。

## 验收摘要

- Node 自动检查：27/27 PASS；覆盖 1.1.0 OpenAPI 响应、图片兼容、视频/流幂等、取消/停止边界、权限、游标去重和生命周期。
- 针对性 ESLint：PASS；新增 Vue 均不超过 250 行，JS 均不超过 200 行。
- 生产构建：PASS；仅有 5 组既存 CSS 顺序、Browserslist 和包体积提示，未升级依赖。
- 浏览器：视频成果/历史、实时运行/空页/失败、授权截图、确认停止、停止未知、disabled 来源和无权限直达均 PASS。
- OpenSpec strict：`remote-inference-platform`、`remote-video-streaming` 均 PASS。
- Graphify：30014 nodes / 73491 edges / 1278 communities；仅保留 6 个既有 Vue 部分解析提示。
- 原 18100/19100 集成环境验收后仍返回 HTTP 200；独立 18105/19105 模拟进程已停止。

证据见 `README.md`、`evidence/round5-verification.json` 和 `evidence/round5-browser.md`。

## 00 验收要求

00 应从 `f242a02` 快进本交付，独立执行 27 项测试、静态检查、完整前端构建和浏览器回归，再决定是否勾选平台 4.9 与流 4.1—4.3。验收必须核对：

- 无权限与跨用户资产/来源/会话访问仍被后端拒绝；
- 同一提交/启动只派发一次，响应未知时复用原 key/body；
- 页面离开不停止远程会话，只有明确按钮发送停止；
- UNKNOWN 或停止未确认从未被显示为成功/已停止；
- 浏览器请求、页面和日志中不存在外部地址、媒体源秘密或凭据；
- 旧图片历史、成果预览/下载和第四轮停用入口未退化。

真实 provider 资料与联调仍为 `UNCONFIRMED`。本包模拟结果不能记为 5.1、真实视频/流成功或生产能力启用；05 未确认之前继续保持生产视频/流能力 disabled。不要提前清理旧代码或生成发布候选。

回退采用追加 revert，实现提交为单独边界；保留历史数据、资产、V001/V002 和旧入口停用记录。
