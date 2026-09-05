# 远程推理平台最新并行与串行计划

## 1. 完成状态

截至 2026-09-04，01—07 已完成并验收：独立 HTTP stub、恢复与 UNKNOWN、取消/停止、图片/视频/流闭环、前后端功能分包和确认淘汰的旧执行入口均已处理。功能验收基线为 `a14450ec0ed82cd329a666e52ac12c15cce3515d`，包含最新目录规划的施工起点为 `c58df289674c2b246334a4d005ad5ba1c90fae80`。

第 8 批两个并行目录包已经完成并串行合入；本文保留其拓扑、提交边界和清理门禁作为历史施工记录。真实 RTX 5070 局域网和 Ubuntu 单张扩容 48GB RTX 4090 门禁等待同事服务，不阻止本地 stub/disabled RC，但阻止真实服务结论和 OpenSpec 归档。

独立最终仓库已经建立在：

`/Users/twowt88/Documents/ChatGPT/Fniao-AI-Platform`

施工分支为 `codex/final-layout`。旧 WGAI、WGAI-parallel 及同级临时工作树只保留历史和追溯，不是活动路径。

## 2. 已执行的并行关系

第 8 批采用“一次串行建壳、两个零重叠目录包并行、一次串行收口”：

```text
最终目录 codex/final-layout @ c58df289
                  │
                  ▼
阶段 A（串行）
backend-github → apps/backend
frontend-vue   → apps/frontend
验证并冻结 08A_SHA
                  │
      ┌───────────┴───────────┐
      ▼                       ▼
2A database（并行）       2B remote boundary（并行）
codex/database-layout     codex/remote-boundary
database/**               remote-inference/**
                          docs/remote-inference/**
                          deploy/remote-inference/**
      └───────────┬───────────┘
                  ▼
阶段 D（串行，回到最终目录）
核对零重叠 → 依次合并 → 修复共享路径
完整本地 RC → 工具重建 → main → origin/main
```

并行工作树位于最终仓库同级目录：

- `/Users/twowt88/Documents/ChatGPT/Fniao-AI-Platform-worktrees/database-layout`
- `/Users/twowt88/Documents/ChatGPT/Fniao-AI-Platform-worktrees/remote-boundary`

它们不是最终项目的子目录，也不进入 Git。

## 3. 阶段 A：串行建立 apps

只移动 `backend-github → apps/backend` 和 `frontend-vue → apps/frontend`，并修复 Maven、npm、许可证和最小构建入口。此阶段不建立最终 database、remote-inference、docs/remote-inference 或 deploy/remote-inference 内容。

通过必要构建和路径检查后提交并冻结 `08A_SHA`。只有工作区干净且提交确定后，才从该 SHA 创建 2A/2B 工作树。

## 4. 2A：database，与 2B 并行

只负责：

- `database/bootstrap`：脱敏初始化、清理入口及说明。
- `database/migrations/ai-core`：原 V001，文件字节、校验值和顺序不变。
- `database/migrations/stream`：原 V002，文件字节、校验值和顺序不变。
- `database/seeds/stub`：明确标识开发模拟的能力绑定样例。
- `database/private`：只提交 README 和忽略规则。
- 数据库迁移清单、本包交接记录和验证器。

不得修改 remote-inference、docs、根 Compose、apps 业务源码、OpenSpec 总表或共享工具脚本。需要统一修复的路径写入交接记录。

## 5. 2B：remote boundary，与 2A 并行

只负责：

- `remote-inference/contracts`
- `remote-inference/fixtures`
- `remote-inference/stub`
- `remote-inference/acceptance`
- `remote-inference/handoff`
- `docs/remote-inference`
- `deploy/remote-inference` 的非数据库内容

逐文件迁移 `apps/backend/deploy/remote-ai` 的非数据库内容，不移动整个父目录，不接管 migrations、V001/V002 或 stub seed。不得修改 database、根 Compose、apps 业务源码、OpenSpec 总表或共享工具脚本。

stub 继续拆分启动、配置、鉴权、请求体、路由、校验、场景、响应、fixtures、状态和测试，不合并为单文件。模拟结果和证据继续明确标识 stub/simulated。

## 6. 阶段 D：串行集成和交付

1. 确认两个并行分支从同一 `08A_SHA` 开始、工作树干净、文件集合零重叠。
2. 在 `codex/final-layout` 依次合并两个已提交分支，不复制目录或整目录覆盖。
3. 修复根 Compose、Docker、环境模板、构建脚本、备份恢复、README、AGENTS、OpenSpec 链接和活动路径。
4. 验证正式配置默认不启动、不引用、不回退 stub；真实服务缺失时保持 disabled。
5. 执行 Java 8、Vue、数据库、Compose、权限、remote→stub、disabled、历史、秘密、路径、文件规模、依赖和 OpenSpec 严格验证，生成本地 RC 报告。
6. 并行工作树结束后，只在最终 Git 根重建一次 Graphify，Serena 只指向最终 Git 根，OpenSpec 直接使用仓库内现有目录。
7. 验证通过后安全合入并推送 `main`，禁止强推；再核对并移除干净且已合并的临时 worktree。

## 7. 最终仓库

```text
Fniao-AI-Platform/
├── apps/
│   ├── backend/
│   └── frontend/
├── database/
│   ├── bootstrap/
│   ├── migrations/
│   │   ├── ai-core/
│   │   └── stream/
│   ├── seeds/stub/
│   └── private/
├── remote-inference/
│   ├── contracts/
│   ├── fixtures/
│   ├── stub/
│   ├── acceptance/
│   └── handoff/
├── deploy/remote-inference/
├── docs/remote-inference/
├── openspec/
├── tools/
├── AGENTS.md
└── README.md
```

业务代码内部继续按 capability、asset、job、result、image、video、stream、provider、operations、legacy 功能模块组织，再按实际职责使用 api、application、domain、port、persistence、storage、client、config。算法名称只是外部 capability 绑定，不建立重复的 Controller/Service/页面，也不创建无业务契约的空模块。

## 8. 工具隔离

- Graphify、Serena、OpenSpec 按最终 Git 根配置一次，不按 apps、database、remote-inference 分别迁移。
- OpenSpec 已随独立仓库存在，不需要重新复制或建立 MCP。
- 并行阶段不切换共享 Serena 项目；需要跨目录定位时查询最终根的 Graphify 或使用工作树本地只读工具。
- 阶段 D 更新工具脚本为从 Git 根解析路径，清除活动配置中的旧 WGAI 绝对路径，然后重建最终 Graphify 并切换唯一 Serena 项目。

## 9. 未来真实 GPU 门禁

同事服务到位后另建工作包。业务前端只访问业务后端；业务后端通过版本化 provider HTTP 接口请求同一局域网 Ubuntu RTX 5070，之后再验收 Ubuntu 单张扩容 48GB RTX 4090 正式服务。本项目不进入同事算法仓库，也不负责模型、权重、CUDA、驱动或推理实现。
