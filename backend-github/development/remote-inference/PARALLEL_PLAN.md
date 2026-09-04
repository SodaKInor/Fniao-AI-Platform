# 远程推理平台最新并行与串行计划

## 1. 当前真实状态

截至 2026-09-04，`feature/remote-inference` 的本地验收基线为
`a14450ec0ed82cd329a666e52ac12c15cce3515d`。

- 01—04b 的基线、契约、provider、资产/任务、视频/流和前端已经集成。
- 05 的独立 HTTP stub、显式开发 profile 和 remote→stub 图片/视频/流闭环已经集成。
- 06 的恢复、UNKNOWN、取消/停止、故障和观测矩阵已经集成。
- 07 已把后端迁入 10 个功能根、前端迁入 8 个功能根，并退役确认淘汰的旧执行入口。
- 平台 OpenSpec 为 44/56，流变更为 22/27。当前本地剩余仅是最终目录整合；真实 GPU 门禁等待同事服务。

`WGAI-parallel/*/code` 仍是临时 Git worktree。最终项目通过合并提交形成，禁止复制或拼接这些目录。

## 2. 当前功能模块

后端已经按以下功能组织：

`capability / asset / job / result / image / video / stream / provider / operations / legacy`

前端已经按以下功能组织：

`capability / asset / job / result / image / video / stream / legacy`

每个模块只保留实际需要的分层。算法名称是 capability 绑定，不建立“一个模型一套 Controller/Service/页面”的重复结构。

## 3. 第 8 批拆分

顶层路径移动会改变大量 Git 文件名，不能和功能开发同时进行。第 8 批采用“一次串行建壳、两个目录包并行、一次串行收口”：

```text
已完成基线 a14450e
        │
        ▼
08-release 阶段 A：串行建立 apps 壳
backend-github → apps/backend
frontend-vue   → apps/frontend
        │
        ▼
00 验收并冻结 08A_SHA，同时创建两个工作树
        │
        ├─────────────────────────────┐
        ▼                             ▼
08b-database-layout             08c-remote-boundary
database/**                     remote-inference/**
V001/V002/seed/private          docs/remote-inference/**
                                deploy/remote-inference/**
        └──────────────┬──────────────┘
                       ▼
00 串行合并 08b、08c，冻结 08BC_SHA
                       │
                       ▼
08-release 阶段 D：路径修复、构建、完整本地 RC
                       │
                       ▼
00 最终验收 → main → origin/main → 独立 clone
```

### 08-release：阶段 A 和阶段 D

阶段 A 只执行 `backend-github → apps/backend`、`frontend-vue → apps/frontend`，并做让 Maven、npm 和最小检查可从新路径运行所需的改动。通过后立即交给 00，不提前移动 database 或 remote-inference。

阶段 D 等 00 合入 08b/08c 后再继续，负责所有共享路径、根 Compose、Docker 构建上下文、工具脚本、AGENTS、OpenSpec 链接、README、完整测试和 `docs/FINAL_INTEGRATION_REPORT.md`。

### 08b-database-layout：可与 08c 并行

只负责：

- `database/bootstrap`：现有脱敏初始化/本地清理入口及说明。
- `database/migrations/ai-core`：原 V001，文件字节、校验值和顺序不变。
- `database/migrations/stream`：原 V002，文件字节、校验值和顺序不变。
- `database/seeds/stub`：开发 stub 能力绑定样例。
- `database/private`：只提交 README/.gitignore，不提交任何真实数据。

本包不得修改根 Compose、OpenSpec 总表、remote-inference、docs、apps 源码或共享工具脚本。需要更新的旧路径写入 HANDOFF，由阶段 D 统一修复。

### 08c-remote-boundary：可与 08b 并行

只负责：

- `remote-inference/contracts`：业务 v1/v1.1 与 provider draft 契约。
- `remote-inference/fixtures`：图片、视频、流、空结果和故障样例。
- `remote-inference/stub`：已有独立 HTTP stub。
- `remote-inference/acceptance`：各批验收证据。
- `remote-inference/handoff`：接口交接模板。
- `docs/remote-inference`：架构、文件归属、运行和历史说明。
- `deploy/remote-inference`：除 V001/V002 与 stub seed 外的远程 provider 配置、校验器和 profile。

本包逐文件移动 `apps/backend/deploy/remote-ai` 的非数据库内容，不移动整个父目录，避免与 08b 冲突。不得修改根 Compose、database、apps 业务源码、OpenSpec 总表或共享工具脚本。

## 4. 串行门禁

1. 08-release 必须从 00 验收基线开始；阶段 A 未验收时不能创建 08b/08c 代码工作树。
2. 08b 与 08c 必须从同一个 `08A_SHA` 建立独立 worktree，可以并行。
3. 00 先核对两包文件集合不重叠，再按提交合并；禁止复制目录。
4. 08-release 阶段 D 必须从 00 的 `08BC_SHA` 继续，不能从阶段 A 直接跳过并行包。
5. 只有完整本地 RC 通过后，00 才能合并 main、推送并创建最终独立 clone。
6. RTX 5070/4090 任务保持未完成，不阻止本地目录 RC，但阻止真实服务结论和 OpenSpec 归档。

## 5. 最终仓库

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
├── deploy/
│   └── remote-inference/
├── docs/
│   └── remote-inference/
├── openspec/
├── tools/
├── AGENTS.md
└── README.md
```

不创建空的算法类型目录。代码生成器自己的 SQL 留在 `apps/backend`；真实数据库、凭据、素材、模型、权重、缓存和工具索引不进入 Git。

## 6. 工具与最终克隆

- 当前各 worktree 只运行 `graphify update .`；不切换共享 Serena 项目。
- 00 推送 `origin/main` 后，从远程克隆到 `/Users/twowt88/Documents/ChatGPT/Fniao-AI-Platform`。
- 该目录不是旧仓库 worktree，不包含 `backend-master`、`WGAI-parallel`、旧 graphify-out 或 Serena cache。
- 关闭旧并行任务后，只在最终 Git 根配置一次 Graphify、Serena 和 OpenSpec。四个顶层功能区域不分别迁移工具。

## 7. 未来真实 GPU 门禁

同事服务到位后另建工作包，先从业务后端容器验收同一局域网 Ubuntu RTX 5070，再验收 Ubuntu 单张扩容 48GB RTX 4090 正式服务。stub、ping、端口连通或宿主机请求均不能替代真实成果证据。
