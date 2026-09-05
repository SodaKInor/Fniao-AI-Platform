# 远程推理平台架构与代码约束

状态：2026-09-04 基于功能验收提交 `a14450ec0ed82cd329a666e52ac12c15cce3515d` 和规划起点 `c58df289674c2b246334a4d005ad5ba1c90fae80` 的最新设计。1—7 批、独立 HTTP stub、本地故障门禁及前后端功能模块迁移均已完成；独立最终仓库已建立在 `/Users/twowt88/Documents/ChatGPT/Fniao-AI-Platform`，当前只实施第 8 批最终目录整合。真实 RTX 5070/4090 服务验收继续等待同事交付。

## 1. 项目边界

最终仓库是一个模块化单体，不是多个小项目，也不把每个算法拆成微服务：

```text
Fniao-AI-Platform/
├── apps/backend/                 Java 8 / Spring Boot 2.6.6 / JEECG 业务后端
├── apps/frontend/                Vue 2 业务前端
├── database/                     bootstrap、ai-core/stream 迁移、stub seed 与 private
├── remote-inference/             contracts、fixtures、stub、acceptance、handoff
├── deploy/                       业务系统与 remote-inference 配置
├── docs/remote-inference/        架构、运行、交接和报告
├── openspec/                     持久需求与任务状态
└── tools/                        项目工具脚本
```

当前业务源码仍位于 `backend-github` 和 `frontend-vue`，但内部功能分包已经完成。第 8 批先串行迁移 `apps` 壳，再从同一验收 SHA 并行迁移 database 与 remote-inference，最后串行修复共享路径和生成本地 RC；具体边界见 `PARALLEL_PLAN.md`。

GPU 服务、算法、模型、权重、驱动和显卡运行时由同事负责，不进入本仓库。`remote-inference/stub` 只模拟版本化 HTTP 接口，不产生真实算法结果。`backend-master` 始终只读且不进入最终仓库。

## 2. 后端按功能分包

当前已完成迁移的 Java 根为：

`backend-github/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/ai/`

最终 Maven 工程整体进入 `apps/backend`，上述 Java 包名保持 `org.jeecg.modules.ai`，内部已经是：

| 功能模块 | 职责 | 禁止内容 |
|---|---|---|
| capability | 能力目录、启停、外部标识绑定、限制与配置快照 | HTTP wire JSON、资产文件流、任务调度 |
| asset | 私有输入/成果资产、归属、上传下载、存储键、完整性与保留期 | provider 调用、任务终态决策 |
| job | requestId、幂等、状态机、后台派发、取消、恢复和历史 | 供应商 JSON、磁盘绝对路径、页面 DTO |
| result | 结构化成果、成果资产关联、结果校验与业务转换 | 网络连接、用户鉴权入口、任务派发 |
| image | 图片分析特有请求、参数和结果解释 | 复制通用任务、资产或 provider 实现 |
| video | 上传视频特有参数、事件时间线与可选标注视频 | 实时流会话状态、具体模型权重逻辑 |
| stream | 授权来源、流会话、事件游标、截图、停止和恢复 | 浏览器 RTSP、视频中继、通用任务状态复制 |
| provider | 外部端口、HTTP 适配、wire DTO、鉴权、信任、超时和严格转换 | 业务表、用户归属、页面响应、完整业务编排 |
| operations | 核心/外部健康、队列与在途、requestId/sessionId 诊断和指标 | 模型就绪猜测、凭据与原始地址输出 |
| legacy | 旧入口到新用例的薄适配及明确停用守卫 | 新状态模型、旧算法回退、继续扩展旧流程 |

每个功能内部按实际需要使用 `api/`、`application/`、`domain/`、`port/`、`persistence/`、`storage/`、`client/` 或 `config/`。不机械创建空目录。

人脸、车牌、安全帽等名称是外部能力绑定，不是新的代码模块。只有请求结构、结果解释或业务流程真正不同，才在 image/video/stream 下增加小型子模块。audio、chat、training 在没有确认保留业务 API 时不创建；已决定退役的聊天和训练入口放入清理清单。

## 3. 后端依赖方向

```text
image/video/stream api -> 各自 application
媒体 application      -> capability + asset + job + result 的公开端口
job application        -> job domain + provider port + asset/result port
provider client        -> provider port + 严格 wire DTO
persistence/storage    -> 所属功能的 port + domain
bootstrap/config       -> 装配实现，不承载业务流程
legacy                 -> 已确认的新 application 入口
```

- domain 不依赖 Spring、MyBatis、HTTP 或供应商 DTO。
- provider 不访问业务数据库，不决定用户归属，不直接更新成功终态。
- asset 不派发任务；storage 不更新任务状态；persistence 不调用 GPU。
- Controller 只做请求校验、认证身份、调用应用用例和返回稳定 DTO。
- 跨模块只使用公开应用端口或稳定领域标识，不通过 Mapper、实现类、静态可变对象或 CommonService/Utils 穿透边界。
- 请求发出后响应丢失继续使用 UNKNOWN；没有确认查询/幂等时不得透明重发。

## 4. 前端按功能组织

当前已完成迁移的前端根为 `frontend-vue/src/modules/ai`；最终随 Vue 工程整体迁入：

```text
apps/frontend/src/modules/ai/
├── capability/
├── asset/
├── job/
├── result/
├── image/
├── video/
├── stream/
└── legacy/
```

每个实际功能按需包含 `api/`、`services/`、`components/`、`views/` 和 `routes/`。页面组合组件，API 模块复用现有请求封装，轮询服务管理定时器、代次和终态，结果模块提供通用容器，媒体模块只实现特有展示。

浏览器只访问业务后端，不接收 GPU URL、服务凭据、RTSP、权重路径或可任意填写的外部地址。关闭页面只清理轮询，不等于取消任务或停止远端会话。

## 5. 数据库按功能归属

```text
database/
├── bootstrap/                    脱敏结构和必要基础数据
├── migrations/
│   ├── ai-core/                  原 V001，覆盖资产、任务、结果与能力
│   └── stream/
├── seeds/stub/                   明确标识模拟的能力绑定样例
└── private/                      原始基线和真实数据，Git 忽略
```

迁移版本号全局唯一。已交付 V001/V002 只移动规范位置，不改写内容、校验值和执行顺序。每个迁移说明所有者、前置版本和重复执行行为。代码生成器或旧页面旁的 `*_menu_insert.sql` 只有在它成为部署迁移时才移动。

## 6. 独立 HTTP stub

stub 是开发期独立进程，经 `remote` 模式和真实 HTTP provider 适配器访问。它按冻结契约实现已需要的健康、能力、图片、上传视频、流来源/会话/事件/停止和成果下载，并提供成功、有效空结果、延迟、鉴权失败、响应丢失、协议错误、重复/乱序事件与成果中断场景。

stub 内部至少拆分启动、配置、鉴权、路由、校验、场景服务、响应编码、fixtures 和测试。它不读业务数据库、不导入业务后端实现、不运行旧算法或 GPU 依赖。所有能力、结果、日志和证据标识 `stub`。正式 Compose 不默认启动或引用 stub，正式配置缺失时保持 disabled。

## 7. 文件规模

| 文件 | 目标 | 强制审查阈值 |
|---|---:|---:|
| Java 手写业务文件 | ≤250 行 | >400 行 |
| Vue 单文件组件 | ≤250 行 | >350 行 |
| JS/TS/CJS 手写模块 | ≤200 行 | >300 行 |
| 普通业务方法 | ≤50 行 | >80 行 |
| Controller 方法 | 通常 ≤30 行 | 承载业务流程即不合格 |

超过目标需要按业务职责拆分；达到强制阈值必须在集成报告说明不能拆分的理由。生成文件和固定 fixtures 单独统计，不能把手写代码标成 generated 绕过检查。

## 8. 实施顺序

1. 已完成：05 stub、06 故障恢复、07 功能模块与旧入口清理，功能基线为 `a14450e`；最终目录的施工起点为 `c58df289`。
2. 在最终目录 `codex/final-layout` 串行使用 `git mv` 建立 `apps/backend` 和 `apps/frontend`，验证并冻结 `08A_SHA`。
3. 从同一 `08A_SHA` 在最终目录同级建立 `codex/database-layout` 与 `codex/remote-boundary` 两个独立 worktree，并行移动互不重叠的目录。
4. 两包完成后回到 `codex/final-layout`，核对零重叠并依次合并，串行修复共享路径、完整构建并生成本地 RC。
5. 只在最终 Git 根重建一次 Graphify 和 Serena 定位；OpenSpec 直接使用仓库内现有目录。通过后安全合入并推送 main，不重新克隆最终目录。
6. 同事服务可用后单独完成 RTX 5070 局域网和 RTX 4090 48GB 正式验收；真实证据未齐前不归档整体变更。
