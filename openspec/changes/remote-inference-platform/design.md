## Context

`feature/remote-inference` 已完成版本基线、`1.1.0` 业务契约、provider 客户端、私有资产、持久任务、图片/上传视频/实时流页面以及本地模拟组合验收。当前代码主要位于 `backend-github`、`frontend-vue`，远程契约、部署增量、数据库迁移和验收材料又分散在后端及根部署目录中；Java AI 代码采用 api/application/domain/client/persistence 横向分层，随着图片、视频和流扩展，单一层目录内的类型已经过多。

同事尚未提供 RTX 5070 开发服务或 RTX 4090 48GB 正式服务，因此真实方法、路径、鉴权、TLS、限额、来源映射、查询与停止语义仍不能确认。现阶段能完成的是业务端、独立契约 stub、故障恢复和最终项目结构；真实 GPU 验收必须继续保持开放。

并行 worktree 是施工隔离机制，不是最终产品结构。独立最终仓库已经从已验收本地基线建立在 `/Users/twowt88/Documents/ChatGPT/Fniao-AI-Platform`；剩余结构重构直接在该仓库及其同级临时工作树完成，保留 Git 历史且不再二次复制项目。

## Goals / Non-Goals

**Goals:**

- 建立一个可独立克隆、构建、部署和理解的最终仓库。
- 顶层按运行边界区分业务后端、业务前端、数据库、远程契约/stub 和部署；代码内部按稳定业务功能组织。
- 用独立 HTTP stub 验证真实网络适配路径，并让模拟证据与真实 GPU 证据无法混淆。
- 保留已完成 API、状态、权限、幂等、恢复、历史数据和前端行为。
- 允许同事服务到位后仅增加或调整 provider 适配和配置，不改写业务页面与持久任务模型。

**Non-Goals:**

- 不拆分业务微服务，不升级 Java/Vue 大版本，不重写 JEECG 核心。
- 不开发 GPU、算法、权重、CUDA、训练或视频中继。
- 不为每个模型名称创建模块；不为将来可能的能力生成空壳代码。
- 不移动代码生成器专用 SQL，不提交原始数据库、真实用户数据或凭据。

## Decisions

### 1. 最终仓库采用顶层运行边界

```text
Fniao-AI-Platform/
├── apps/
│   ├── backend/
│   └── frontend/
├── database/
│   ├── bootstrap/
│   ├── migrations/
│   ├── seeds/
│   └── private/
├── remote-inference/
│   ├── contracts/
│   ├── fixtures/
│   ├── stub/
│   └── acceptance/
├── deploy/
├── docs/remote-inference/
├── openspec/
└── tools/
```

这一结构让运行单元和资料边界一眼可见。保留 `backend-github`、`frontend-vue` 原名虽然改动较少，但继续携带来源含义且无法解决契约、数据库和部署散落问题；把每个业务功能做成顶层仓库则会重新造成碎片化。

### 2. 后端采用按功能分包、功能内分层的模块化单体

现有 JEECG Maven 工程整体迁入 `apps/backend`，不因整理目录拆成独立部署服务。AI 代码目标结构：

```text
org.jeecg.modules.ai/
├── capability/{api,application,domain,persistence}
├── asset/{api,application,domain,port,persistence,storage}
├── job/{api,application,domain,port,persistence,worker}
├── result/{api,application,domain,port}
├── image/{api,application,domain}
├── video/{api,application,domain}
├── stream/{api,application,domain}
├── provider/{port,client,dto,config}
├── operations/{api,application}
└── legacy/
```

capability 管理能力和绑定；asset 管理私有文件；job 管理请求身份、状态、幂等、调度和取消；result 管理成果解释与回存；image/video/stream 只保存各自特有业务类型和用例；provider 封装外部 wire 协议与网络；operations 提供健康和管理观测；legacy 只容纳过渡适配和停用守卫。

备选的“先按 api/application/domain 横向分层”已在能力扩展后变得难以定位；“每个功能独立 Maven 模块”会为当前规模增加循环构建和公共类型管理成本。因此采用 Java 包级功能模块，并用依赖检查约束边界。

### 3. 功能模块依赖保持单向

```text
image/video/stream api -> 各自 application
各媒体 application    -> capability + asset + job + result 的公开应用端口
job application        -> job domain + provider port + asset/result port
provider client        -> provider port + 严格 wire DTO
persistence/storage    -> 所属模块 port + domain
config/bootstrap       -> 负责装配，不承载业务流程
```

provider 不访问业务表、不决定用户归属；asset 不更新任务状态；Controller 不直接调用 HTTP 客户端或 Mapper；domain 不依赖 Spring、MyBatis 或供应商 JSON。跨模块只调用公开应用端口或稳定领域标识，禁止通过新的 CommonService、Utils 或共享可变对象绕过边界。

### 4. 前端按功能组织并与业务概念对应

```text
apps/frontend/src/modules/ai/
├── capability/
├── asset/
├── job/
├── result/
├── image/
├── video/
├── stream/
├── operations/
└── legacy/
```

每个已实现模块按需要包含 `api`、`services`、`components`、`views`、`routes`。页面负责组合，API 模块负责业务后端调用，轮询服务负责生命周期，结果模块提供通用容器，媒体模块提供特定渲染。audio、chat、training 在没有保留业务契约时不创建空模块。

按功能归档可以让一次业务修改集中在相邻文件；继续维护根级 `api/ai`、`services/ai`、`components/ai` 和 `views/ai` 会使同一功能横跨多个远目录。

### 5. 数据库按业务所有权集中管理

`database/bootstrap` 只保存可版本化的脱敏结构和必要基础数据；`database/migrations` 按 capability、asset、job、result、stream 分组并保持全局唯一版本；`database/seeds` 只保存明确的本地演示数据；`database/private` 保存本机原始基线且被 Git 忽略。

已经交付的 V001/V002 不改写内容，只通过移动和迁移清单保留校验值与执行顺序。Java/Vue 生成模板中的 `*_menu_insert.sql` 属于生成器或原功能源码，继续留在原模块。部署预检在私有数据库缺失时给出明确获取说明，不生成伪造生产数据。

### 6. 契约、stub 和验收证据独立于业务后端

`remote-inference/contracts` 保存业务与 provider 版本契约；`fixtures` 保存成功、空结果和错误样例；`stub` 是可独立启动的 HTTP 进程；`acceptance` 保存契约和组合证据。业务后端只保留 provider 端口与适配器，不拥有同事的服务代码。

stub 按当前 provider 草案实现健康、能力、图片、视频和流中已冻结的路径。代码分为启动、配置、路由、请求校验、场景服务、响应编码和测试，不能集中在单个 server 文件。它不读业务数据库、不访问旧算法、不包含 GPU 依赖，只返回确定性 fixtures。

采用独立 HTTP 进程而不是只使用进程内 mock，是为了覆盖 DNS/地址、鉴权头、multipart、超时、响应转换和成果下载等真实适配路径。采用固定 fixtures 而不是伪造算法，是为了让输出可复现且不制造算法已实现的误解。

### 7. disabled、mock、remote 三种模式语义不变

- `disabled`：拒绝新推理，历史和管理仍可用。
- `mock`：只用于后端进程内的针对性测试。
- `remote`：走真实 HTTP provider；开发地址可以指向 stub，未来指向同事服务。

stub 通过显式 Compose profile 启动，结果和能力元数据必须标识模拟来源。正式配置不启动也不允许解析到 stub；正式地址缺失时保持 disabled，绝不回退旧算法或 stub。

### 8. 现有图片、上传视频和实时流契约保持稳定

`image-detection.v1` 保持 `/ai/v1/infer` 和任务查询兼容；`video-file-analysis.v1` 使用上传视频任务；`video-stream-analysis.v1` 只接受授权 `streamSourceId`。供应商 DTO 继续严格类型化，未知字段按冻结策略忽略或拒绝，不以无约束 Map 进入业务响应、持久结果或日志。

上传视频最低成果仍为事件时间线和授权截图，标注视频可选。流事件继续使用游标、去重身份和授权截图。stub 只能验证这些契约和业务流程，不能确定真实编码支持、吞吐、时延、来源映射或停止能力。

### 9. 执行确定性、恢复与终态规则不因 stub 放宽

推理或启动请求可能已发出而响应丢失时保持 UNKNOWN，除非 provider 有已确认查询或幂等语义，否则不透明重发。FETCHING_RESULT 恢复只重取成果；远程取消和流停止只有收到确认才进入终态；迟到事件不能覆盖终态。

stub 提供可控延迟、响应丢失、协议错误、重复事件和成果中断场景，用于完成本地故障验证。真实 provider 的确定性和恢复能力仍需用真实资料及容器内请求单独验收。

### 10. 旧入口按业务决定和引用证据清理

聊天和训练执行入口按已确认决定退役；图片、上传视频和流入口映射到统一 API，真实 provider 资料未到时可在开发 stub 环境演示，在正式配置保持 disabled。每组清理保存前后引用清单，验证构建、历史数据和保留页面，不以名称含 AI 作为删除依据。

### 11. 先合并功能提交，再单独迁移目录

worktree 之间只合并提交，不复制目录。功能、恢复和清理在原路径完成验收后，从集成 SHA 创建独立结构重构分支，使用 `git mv` 迁移路径并集中更新 Maven/npm、Docker、脚本、文档、Graphify 与项目工具配置。这样可把行为改动与路径改动分开审阅和回退。

结构分支为 `/Users/twowt88/Documents/ChatGPT/Fniao-AI-Platform` 中的 `codex/final-layout`。该目录已经是独立 clone，正式远程为 GitHub `origin`，旧本地集成仓库仅登记为 `source-wgai` 以追溯未推送基线。结构通过后在本目录合入 `main` 并推送；不再重新克隆或覆盖该目录。

截至集成提交 `a14450ec0ed82cd329a666e52ac12c15cce3515d`，stub、恢复故障、功能分包和旧入口清理已经验收；包含最新规划的施工起点为 `c58df289674c2b246334a4d005ad5ba1c90fae80`。剩余目录迁移采用三段门禁：最终目录先串行建立 `apps` 壳并冻结 `08A_SHA`；从该 SHA 创建 `codex/database-layout` 与 `codex/remote-boundary` 两个同级工作树并行移动互不重叠文件；最后回到 `codex/final-layout` 串行合并、修复共享路径并生成 RC。

## Risks / Trade-offs

- [大范围路径迁移造成构建或脚本引用遗漏] → 单独结构提交，维护旧到新路径清单，执行 Maven、npm、Compose、SQL、OpenSpec 和旧路径扫描。
- [功能分包引入循环依赖] → 先建立公开端口与依赖矩阵，逐模块移动并运行 import/Graphify 检查，禁止跨模块直接访问实现。
- [stub 被误认为真实算法] → 响应、能力、日志和验收材料均标识 stub；真实任务保持未完成，正式配置禁止 stub。
- [没有真实服务导致错误的清理判断] → 只清理已明确退役且无保留调用者的组；真实能力不确定的入口 disabled 并记录，不推测支持。
- [数据库归整破坏初始化顺序] → 保留已交付迁移内容与校验值，建立单一清单，在数据库副本重复执行并核对历史摘要。
- [一次性移动过多文件使审阅困难] → 行为开发结束后再迁移，按后端、前端、数据库、remote-inference、部署/文档分提交并逐步验证。

## Migration Plan

1. 已完成并验收 01—07；独立最终仓库从包含功能基线的规划提交 `c58df289674c2b246334a4d005ad5ba1c90fae80` 建立，当前工作分支为 `codex/final-layout`。
2. 在 `/Users/twowt88/Documents/ChatGPT/Fniao-AI-Platform` 串行迁移 `backend-github → apps/backend` 与 `frontend-vue → apps/frontend`，完成最小 Maven/npm 路径修复和验证后提交并冻结 `08A_SHA`。
3. 从 `08A_SHA` 创建 `/Users/twowt88/Documents/ChatGPT/Fniao-AI-Platform-worktrees/database-layout`（`codex/database-layout`）和 `.../remote-boundary`（`codex/remote-boundary`）；前者只迁移数据库文件，后者只迁移契约、fixtures、stub、证据、文档和非数据库 remote 部署文件，两者并行。
4. 两包提交后回到最终目录，先核对文件集合零重叠，再依次合并两个分支；统一修复 Compose、Docker、脚本、OpenSpec、AGENTS 和活动链接。
5. 在独立数据库和文件目录执行完整本地验收，生成只声明 stub/disabled 范围的 RC；完成后只在最终 Git 根重建一次 Graphify 索引并将 Serena 指向该根，OpenSpec 直接使用仓库内现有目录。
6. 验收通过后将 `codex/final-layout` 合入并推送 `main`。目标目录本身就是最终独立仓库，不再重新克隆；同级临时工作树只在合并和状态核对完成后清理。
7. 同事服务可用后，在独立后续门禁中完成 RTX 5070 契约与局域网验收，再完成 RTX 4090 48GB 正式验收；真实证据齐全前不归档整个变更。

## Open Questions

- RTX 5070 服务对图片、上传视频和流会话分别使用什么方法、路径、鉴权、TLS/CA 与接口版本？
- 视频最大大小、时长与并发限制，以及事件、截图和可选标注视频格式是什么？
- 图片/视频查询、幂等、取消，以及流事件查询、停止是否具备可确认语义？
- GPU 端登记来源如何与业务端 `streamSourceId` 映射？

这些问题只影响未来真实 provider 适配和启用门禁，不影响当前模块结构、stub 范围或业务 API；答案缺失时相应真实能力保持 disabled。
