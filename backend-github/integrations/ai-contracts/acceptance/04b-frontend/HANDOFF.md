# 04b-frontend 交接记录

状态：**READY_FOR_INTEGRATION**（2026-09-03，本包模拟验收通过）

## 基线与交付

- 工作树：`/Users/twowt88/Documents/ChatGPT/WGAI-parallel/04b-frontend/code`
- 分支：`work/remote-inference/04b-frontend`
- 原源码起点：`8c7fa382a344e82eb13828d53b9fd9e018a5a461`
- 实施公共基线：`ab9809d23919ea5d61dfc7d8b34d7f30bb9d607c`
- 冻结业务契约：`5a55ca5cc6ea8fde09898f44519d62c715af12db`，已核实为本分支祖先；00 已接受 2.1—2.5。
- 旧入口停用提交：`7401ec68d4cee87d37d6be39ea4acd7f5683b2bf`
- 新页面、模拟工具、自动检查及证据提交：`2df978fcc21fc2ebc1eda93b7082f7c88e47512b`
- 本记录随独立交接文档提交版本化。未推送远程。

## 本包进度

- [x] 4.6：统一业务 API、能力/上传/参数、任务状态、成果预览下载、历史与错误说明。
- [x] 3.4 前端部分：按入口审计停用旧执行页及旧操作，保留管理查询。
- [x] 模拟服务、自动检查、浏览器证据、文件归属/规模检查、构建与知识图更新。
- [ ] 由 00 在合入 03、04a 后完成 4.7 和 3.4 联合验收；本包不改总 OpenSpec 复选框，不归档整体变更。

## 模块与行为

| 归属位置 | 交付内容 |
|---|---|
| `frontend-vue/src/api/ai/` | 复用 `utils/request` 的 axios、Result 与 X-Access-Token；能力、上传、推理、查询、历史、授权 Blob 下载；没有新增业务字段 |
| `frontend-vue/src/services/ai/` | 串行轮询、状态/格式说明、菜单权限转换、停用入口映射 |
| `frontend-vue/src/components/ai/` | 能力选择、上传、参数、状态、检测列表、图片预览与下载地址释放 |
| `frontend-vue/src/views/ai/` | 推理提交、任务详情、历史和停用说明页；新增 Vue 最大 126 行，新增 JS 最大 49 行 |
| `frontend-vue/src/store/modules/user.js` | 菜单加载时转换一次结果；同一结果提供侧栏和动态路由 |
| 两处 `TabAiModelBundList.vue` | 旧识别和关闭操作变为停用提示 |
| `views/audio/audio.vue`、`views/tab/live/audio.vue` | 旧上传/音频执行变为提示，旧音频执行通道停用 |
| `views/teasy/TabEasyConfigList.vue` | “开始训练”变为停用说明；编辑及图片绑定保留 |
| 本 acceptance 目录 | 独立本地模拟服务、16 项自动检查、复现说明、截图、请求和构建证据 |

新导航为 `/ai/inference`、`/ai/history`，隐藏详情为 `/ai/jobs/:requestId`。只对已有模型库、绑定库、AI 基础库或识别历史组件权限的菜单生成，不修改数据库菜单或权限字段。转换幂等，旧路径刷新仍显示停用页。

首版仅开放 `image-detection.v1` / `detection.v1` 的 PNG/JPEG。输入大小取能力限制，默认参数为 threshold=0.5、maxDetections=10、annotate=true。不支持的能力/成果有明确说明；有效空成果显示“未检测到目标”。

每个新提交固定 key 与请求内容，结果不确定时只允许用户明确确认原提交。200/202 均使用返回任务身份进入详情并按查询结果 state 展示，HTTP 成功不被当成推理完成。刷新只有查询，不生成推理请求。

轮询每次完成后等待 2 秒，任务 ID 与递增代次同时隔离旧响应、错误和定时回调。离开、切换任务、失活、销毁时停止；激活只启动当前任务的一条查询链。SUCCEEDED、FAILED、UNKNOWN、CANCELLED 停止观察；网络错误暂停并提供“重新查询”，不虚构失败状态。资产下载校验声明媒体类型和字节长度，错误 JSON 不落盘，旧 Blob 不恢复预览。

停用范围来自 `01-foundation/ENTRYPOINTS.md`：train、face、szr、audio、tab/live/audio、tab/testAI、缺实现的视频告警/配置/抽帧/新订阅页面、缺失 component=easy。持续视频、MaxKB、分类/配置/历史等待定业务保持审计处置范围。

## 验收与复现

详见 [README.md](README.md) 的命令和场景。入口工具为 `mock/server.cjs`，仅监听本机 18105 / 19105；任务及资产保留在服务会话内，刷新可查，重启重置。结束验收时已停止模拟进程并释放登记端口，可按 README 再启动。

- `evidence/unit-tests.txt`：16/16 通过，包含生命周期钩子、A→B 迟到成功/失败、终态、无重叠轮询、重新激活、同 key/body、JSON/长度/格式、Blob 释放、游标与契约响应检查。
- `evidence/lifecycle-requests.json`：浏览器慢任务 A 离开后无后续查询；B 不被迟到的 A 覆盖；刷新历史后该场景仍只有一次 POST /infer。
- `evidence/success.png`、`final-preview.png`：实际构建上传/成果流程及最终预览截图；文本证据覆盖能力停用、超限、空成果、UNKNOWN、供应商鉴权错误、404/410/传输中断和无 AI 菜单账号。
- `evidence/download.json`：真实浏览器落盘文件 79 字节，SHA-256 对应冻结标注样例。浏览器工具下载事件未触发，已明确以落盘校验为证据。
- `evidence/verification.json`、`scope.json`：生产构建退出 0、针对性 ESLint 退出 0、范围与规模通过，冻结文件无修改。依赖只安装在本工作树，未改变清单/锁定版本。
- `evidence/graphify-update.txt`：在正确 worktree 执行 `graphify update .` 成功；6 个既有 Vue 文件部分解析告警和社区名称变化如实保留。没有使用带原目录绝对路径的更新脚本，也没有切换或写入共享 Serena 工程。

构建仍报告旧订单页面 CSS 顺序、包体积、Browserslist 陈旧告警。原全局管理页眉的用户 WebSocket 在模拟环境被拒绝；新 AI 模块不用此通道。缓存失活验证采用真实页面脚本钩子，新导航不启用 keepAlive，未宣称完成浏览器缓存端到端检查。

## 集成与回退

公共接口/依赖变更请求：无。后端实现、公共契约与样例、全局权限配置、主 OpenSpec 总表均未修改。所有后端目录下的改动都在本包 acceptance 内。

由 00 先合入 03、04a，再按 `7401ec6` → `2df978f` → 本交接记录提交的顺序接收本分支。正式业务权限、后端幂等/资产隔离/持久化、真实 GPU 或供应商均没有由模拟验收证明；4.7 和 3.4 总体验收仍待集成执行。

需要回退新页面时，只回退 `2df978f`；这样菜单加载恢复使用 `disableLegacyMenus`，保留 `7401ec6` 的入口停用。交接文档按需追加回退记录，不撤销旧入口停用。冲突时保持已有管理/待定业务与 03 的后端守卫，不通过恢复旧执行逻辑解决冲突。
