## Why

图片、上传视频和实时流的本地业务闭环已经形成，但同事尚未提供可调用的 GPU 服务；现有仓库又按来源和技术层散落，继续开发会让契约、前端、后端、数据库与验收材料越来越难定位。现在需要在保留既有行为和验收证据的前提下，引入明确标识的独立 HTTP 占位服务，并将最终仓库整理为按业务功能组织的模块化单体。

## What Changes

- 保持业务 API `1.1.0`、图片兼容、上传视频、实时流、私有资产、持久任务、UNKNOWN、取消/停止确认和历史成果行为不变。
- 增加开发期独立 HTTP stub，按当前 provider 草案返回确定性图片、视频和流样例以及受控故障；它通过真实网络适配器调用，但所有结果和证据明确标为模拟。
- 将真实 RTX 5070 局域网联调与 RTX 4090 48GB 正式验收改为外部服务到位后的独立门禁；stub 通过不能替代任何真实服务任务。
- 最终仓库收敛为 `apps/backend`、`apps/frontend`、`database`、`remote-inference`、`deploy`、`docs`、`openspec` 和 `tools`。独立仓库已建立在 `/Users/twowt88/Documents/ChatGPT/Fniao-AI-Platform`，不共享旧 `.git`、索引或缓存。
- 后端 AI 代码从仅按技术层横向堆放，调整为 capability、asset、job、result、image、video、stream、provider、operations、legacy 功能模块；每个功能内部再按 api/application/domain/port/persistence 等必要层次拆分。
- 前端改为与业务概念对应的功能模块，每个真实模块按需包含 API、服务、组件、页面与路由；不创建空目录，不在单个页面或状态文件中容纳完整流程。
- 数据库初始化和增量迁移集中到 `database`；现有跨资产/任务/结果的 V001 原样归入 `migrations/ai-core`，V002 原样归入 `migrations/stream`，stub 绑定归入 `seeds/stub`。代码生成模板内的 SQL 保留原位，原始数据库和真实数据继续本地忽略。
- 远程契约、fixtures、stub 与验收证据从业务后端源码中分离到 `remote-inference`；业务后端仅保留 provider 端口和适配器。
- 不按人脸、车牌、安全帽等具体算法名称建立代码模块；它们作为外部能力绑定，只有请求、结果或业务流程确实不同才增加子模块。
- **BREAKING**：全部已确定淘汰的 MaxKB、tchat、easyAi 聊天与训练执行入口继续退役；未确认保留或缺真实服务证据的执行入口保持 disabled，不回退旧本地算法。
- `/Users/twowt88/Documents/ChatGPT/Fniao-AI-Platform` 是剩余改造的唯一集成目录。它先串行建立 apps 壳，再从同一提交在同级 `Fniao-AI-Platform-worktrees` 下创建 database 与 remote-inference 两个零重叠工作树并行实施，最后回到集成目录串行合并并修复共享路径；只合并已提交分支，不复制目录。

## Capabilities

### New Capabilities

- `remote-inference-api`：稳定业务 API、严格 provider 适配、开发 stub 与真实 provider 的证据隔离、鉴权、可用性和错误确定性。
- `inference-assets-jobs`：图片、视频和流相关私有资产、持久任务、结构化成果、恢复、取消、幂等与访问权限。
- `model-capability-lifecycle`：业务能力绑定、模拟/真实可用性标识、旧入口退役和历史成果保留。

### Modified Capabilities

无；当前没有已归档的主规格需要修改。

## Impact

- `apps/backend`：由现有 `backend-github` 迁移；保留 JEECG 基础和 Maven 构建，新增 AI 代码按功能分包。
- `apps/frontend`：由现有 `frontend-vue` 迁移；AI 页面、API、轮询与渲染器按功能归档。
- `database`：接收脱敏 bootstrap、功能迁移、演示 seed 与被忽略的 private 数据入口。
- `remote-inference`：接收 provider 契约、fixtures、独立 stub 与验收证据；不含 GPU 模型、权重、CUDA、驱动或训练实现。
- `deploy`：同时编排业务前端、业务后端、MySQL、Redis及显式可选的 stub profile；正式配置不默认启动 stub。
- OpenSpec、Graphify、Serena、脚本、Docker 构建上下文和文档链接需要随最终路径统一更新。
- `backend-master` 继续只读参考且不进入最终仓库。真实 GPU 服务仍由同事独立维护，通过版本化接口对接。

## Non-goals

本变更不实现真实算法、GPU 服务、模型管理、显卡调优、训练平台或视频中继；不将模块化单体拆成一组业务微服务；不重写 JEECG 系统管理和权限基础；不把 stub 当作生产降级路径，也不根据 stub 结果决定真实算法已可用。
