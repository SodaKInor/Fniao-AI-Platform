# 最新执行提示词

状态：01、02、03、04a、04b 已完成并由 00 集成。以下提示词取代旧的“必须先完成真实 RTX 5070 才能继续”调度。同事尚未提供服务，因此先完成独立 HTTP stub、本地故障、功能模块迁移和最终仓库整理；真实 GPU 单独等待。

每个实现对话必须在对应 `WGAI-parallel/<包>/code` 中运行，先读包级 `START_HERE.md`、`AGENTS.md`、`TASKS.md` 和本目录的架构/归属文件。工作树之间只合并 Git 提交，不复制目录。

## 第一轮：05 独立 HTTP stub

### 05-lan

```text
请接手 05-lan 的最新版任务。先确认 code 根、分支及共同起点，读取最新 ARCHITECTURE.md、FILE_OWNERSHIP.md、OpenSpec 的 remote-inference-platform 与 remote-video-streaming。保留已有 fail-closed 真实证据校验，不再等待同事服务；完成总表 5.1—5.4：冻结 stub 场景和模拟标识，按小文件和单一职责实现独立 HTTP stub，增加显式开发 Compose profile，并从业务后端容器以 remote 模式完成图片、上传视频和实时流的 HTTP 组合验收。stub 不读业务数据库、不调用旧算法、不含 GPU/模型依赖，正式配置不启动或回退 stub。所有证据明确写 stub，RTX 5070/4090 任务不得勾选。提交代码、证据和 HANDOFF，等待 00 验收。
```

### 00-integration

```text
请验收 05 最新交付。核实起点、文件归属、契约变更、stub 模块拆分、正式配置隔离和 HANDOFF；独立复跑契约、Compose、remote→stub 图片/视频/流闭环以及 fail-closed 真实证据校验。只勾选有证据的 5.1—5.4 和伴随流 stub 任务，不勾选任何真实 GPU 项。通过后合入 feature/remote-inference，记录新的共同起点并释放 06。
```

## 第二轮：06 本地恢复和故障

### 06-resilience

```text
请接手 06-resilience。先从 00 验收的 05 stub 共同起点整合你已有的本地候选，解决冲突时保留已集成契约和状态语义。完成总表 6.1—6.4及伴随流本地故障任务：用独立 stub 验证 PENDING/FETCHING_RESULT 恢复、UNKNOWN、不透明重发禁止、取消/停止确认、事件去重/乱序、迟到终态、成果断流、指标与滚动日志。不得用 stub 推断真实 provider 查询/取消/停止能力。复跑 Java、前端、Compose 和安全检查，提交 HANDOFF 等待 00。
```

### 00-integration

```text
请验收并合入 06。独立复跑登录/越权、图片/视频/流、重复提交、UNKNOWN、取消/停止、provider 下线历史访问、事件与文件故障、用户会话、指标和秘密扫描。通过后完成 6.5，只生成“本地 stub/disabled 候选”唯一 SHA；报告必须说明真实 RTX 5070/4090 未验收。随后释放 07，不归档整体变更。
```

## 第三轮：07 功能模块迁移和旧业务清理

### 07-cleanup

```text
请接手 07-cleanup。必须从 00 的 6.5 本地验收 SHA 开始，读取最新功能模块架构。先用 Graphify、源码引用、路由、菜单和数据库证据建立 capability、asset、job、result、image、video、stream、provider、operations、legacy 的文件映射与允许依赖矩阵。随后分组把后端改为按功能分包、前端改为 modules/ai 下按功能组织；每个功能内部再按需拆 api/application/domain/port/persistence 或 api/services/components/views/routes，不创建空目录，不按模型名称复制模块。按已确认范围退役 MaxKB、tchat、easyAi 聊天、训练和无调用者旧算法依赖，保留历史、管理 CRUD 和共用组件。每组独立提交并验证构建、remote→stub、disabled、权限、历史、数据库与 Graphify，交付 HANDOFF。
```

### 00-integration

```text
请验收 07 的每个模块迁移和清理提交。检查依赖方向、循环引用、公共契约、文件规模、直接请求拒绝、保留功能、历史数据与回滚点；复跑后端、前端、remote→stub、disabled 和数据库回归。通过后合入并记录结构迁移起点，释放 08。缺少真实服务证据的入口保持 disabled，不因此退回已通过的本地模块整理。
```

## 第四轮：08 顶层目录整合与最终本地项目

### 08-release

```text
请接手 08-release 的最新版任务。只从 00 放行的 07 SHA 创建独立结构分支，使用 git mv 将 backend-github→apps/backend、frontend-vue→apps/frontend；建立 database/{bootstrap,migrations,seeds,private}，把契约/fixtures/stub/验收证据归入 remote-inference，把架构归入 docs/remote-inference，并统一 deploy。保持 V001/V002 内容与校验值，代码生成器 SQL 留在所属源码，private 数据保持 Git 忽略。更新 Maven、npm、Docker、Compose、脚本、AGENTS、文档、OpenSpec 和所有活动路径；正式 Compose 默认无 stub。执行全量构建、数据库、权限、remote→stub、disabled、历史、秘密、模块依赖、文件规模、OpenSpec strict 和 Graphify 验证，生成 docs/FINAL_INTEGRATION_REPORT.md，提交 HANDOFF。不得勾选真实 GPU 或删除旧仓库。
```

### 00-integration

```text
请执行最终本地集成。核实 08 的目录迁移表、提交、构建、数据库、Compose、功能模块、stub 隔离、OpenSpec 和 Graphify 证据；确认活动配置没有旧 WGAI/worktree 绝对路径。通过后合入 feature/remote-inference，再按既定授权合入并推送 main。从远程 main 克隆 /Users/twowt88/Documents/ChatGPT/Fniao-AI-Platform，确认它是独立 clone，不包含 WGAI-parallel、backend-master、旧 graphify-out 或 Serena cache。不要删除旧仓库。告诉用户在新目录打开 Codex 并执行 07-final-workspace-tool-isolation.md。真实 GPU 任务保持开放。
```

## 第五轮：真实 GPU 服务到位后

```text
请在独立的新工作包执行真实 GPU 门禁。先对齐 RTX 5070 的方法、路径、TLS/CA、鉴权、图片/视频/流样例、限额、来源映射、查询、取消、停止和去重语义；从实际业务后端容器完成局域网请求与成果回存，不以 stub、ping、端口可达或宿主机请求替代。5070 全部通过后，再对接 Ubuntu 单张扩容 48GB RTX 4090 正式服务并执行正式权限、限额、成果、恢复和回滚。只有真实证据齐全时才同步主规格并归档；服务仍缺失则保持 disabled 和任务未完成。
```
