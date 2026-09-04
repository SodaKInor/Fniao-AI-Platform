# 最新第 8 批执行提示词

当前有效起点：`a14450ec0ed82cd329a666e52ac12c15cce3515d`。01—07 已完成并由 00 验收；不要重复执行 05、06、07。

## 1. 08-release 阶段 A：串行 apps 建壳

```text
请在 /Users/twowt88/Documents/ChatGPT/WGAI-parallel/08-release 接手第 8 批阶段 A。先核实 code 工作树、work/remote-inference/08-release 分支和干净状态；必须从 00 的验收 SHA a14450ec0ed82cd329a666e52ac12c15cce3515d 或包含它的最新规划提交开始。读取 START_HERE.md、AGENTS.md、TASKS.md、最新 ARCHITECTURE.md、FILE_OWNERSHIP.md、PARALLEL_PLAN.md 和两个 OpenSpec 变更。

本阶段只用 git mv 将 backend-github 移到 apps/backend、frontend-vue 移到 apps/frontend，并完成 Maven、npm、许可证、最小测试和直接构建入口对新路径的必要修复。不要移动 database、remote-inference、docs/remote-inference 或 deploy/remote-inference，不要执行 8.2—8.5，不要修改真实 GPU 状态。生成旧→新路径清单，验证 Git 历史可追踪、后端和前端可从新路径构建，提交单一 08A 提交并更新 HANDOFF，等待 00 验收。
```

## 2. 00 验收阶段 A，并创建两个并行工作树

```text
请在 /Users/twowt88/Documents/ChatGPT/WGAI-parallel/00-integration 验收 08-release 阶段 A。核对提交只包含 apps/backend 与 apps/frontend 建壳及必要构建路径修复，独立复跑 Maven、npm、契约和旧路径检查。通过后合入 feature/remote-inference，记录唯一 08A_SHA。

然后从同一个 08A_SHA 创建两个独立 Git worktree：
1. /Users/twowt88/Documents/ChatGPT/WGAI-parallel/08b-database-layout/code，分支 work/remote-inference/08b-database-layout；
2. /Users/twowt88/Documents/ChatGPT/WGAI-parallel/08c-remote-boundary/code，分支 work/remote-inference/08c-remote-boundary。

核实两者 Git 根、分支、HEAD 和干净状态，更新 WORKSPACES.json。不要在本对话实施 08b/08c 的代码移动。完成后这两个目录可以分别开启新对话并行执行。
```

## 3. 并行包 08b：database

```text
请在 /Users/twowt88/Documents/ChatGPT/WGAI-parallel/08b-database-layout 接手数据库目录包。必须确认 code 工作树处于 work/remote-inference/08b-database-layout，HEAD 等于 00 登记的 08A_SHA。只建立 database/bootstrap、database/migrations/ai-core、database/migrations/stream、database/seeds/stub、database/private：移动现有脱敏初始化/清理入口、V001、V002 和 stub 绑定样例；V001/V002 文件字节、校验值、版本和顺序不得改变。private 只提交 README/.gitignore，不放真实数据。

不要修改根 Compose、remote-inference、docs、apps 业务源码、OpenSpec 总表或共享工具脚本。为新数据库目录增加独立清单和验证器，在数据库副本验证初始化、V001→V002 顺序和重复执行行为。把仍需统一修复的旧路径写入 HANDOFF，提交单一职责提交并等待 00。
```

## 4. 并行包 08c：remote-inference

```text
请在 /Users/twowt88/Documents/ChatGPT/WGAI-parallel/08c-remote-boundary 接手远程推理边界目录包。必须确认 code 工作树处于 work/remote-inference/08c-remote-boundary，HEAD 等于 00 登记的 08A_SHA。将业务/provider 契约移到 remote-inference/contracts，样例移到 fixtures，独立 HTTP stub 移到 stub，验收证据移到 acceptance，接口交接模板移到 handoff；将架构文档移到 docs/remote-inference；将 apps/backend/deploy/remote-ai 中除 migrations 和 stub-bindings.example.sql 之外的文件逐个移到 deploy/remote-inference。

不要移动整个 remote-ai 父目录，不要修改 database、根 Compose、apps 业务源码、OpenSpec 总表或共享工具脚本。修复本包内部链接，运行 stub/契约/证据校验，证明所有结果仍标识 simulated、正式 profile 不默认使用 stub。把跨包旧路径写入 HANDOFF，提交单一职责提交并等待 00。
```

## 5. 00 合并两个并行包

```text
请在 00-integration 核实 08b 与 08c 都从同一个 08A_SHA 开始，检查两包实际文件集合没有重叠。分别审查 HANDOFF、迁移表和验证结果，然后按提交合入 feature/remote-inference；冲突逐文件解决，不复制目录。复跑数据库清单、契约、stub 测试和链接检查。通过后记录唯一 08BC_SHA，并让 08-release 快进到该 SHA，释放阶段 D。
```

## 6. 08-release 阶段 D：串行收口和本地 RC

```text
请回到 08-release，从 00 登记的 08BC_SHA 继续。统一修复根 Compose、Dockerfile 构建上下文、环境模板、Maven/npm 脚本、备份恢复脚本、AGENTS、README、OpenSpec 链接及 tools/graphify、tools/serena 的活动路径；路径必须从 Git 根动态解析。正式 Compose 默认不启动、不引用、不回退 stub。

执行 Java、Vue、数据库、Compose、登录权限、remote→stub 图片/视频/流、disabled、历史成果、秘密扫描、旧活动路径、文件规模、模块依赖、两个 OpenSpec strict 和 graphify update .。生成 docs/FINAL_INTEGRATION_REPORT.md，明确本地 RC 仅覆盖 simulated/disabled，真实 5070/4090 未验收。提交阶段 D 和 HANDOFF，等待 00 最终验收。
```

## 7. 00 最终交付

```text
请在 00-integration 验收 08-release 阶段 D，独立复跑最终报告中的关键门禁。通过后合入 feature/remote-inference，再按用户既定授权合入并推送 main。从 origin/main 克隆 /Users/twowt88/Documents/ChatGPT/Fniao-AI-Platform，确认它是独立 clone，不含 WGAI-parallel、backend-master、旧 graphify-out、Serena cache、真实数据库、凭据、素材或模型。不要删除旧仓库和 worktree。

完成后让用户在新目录打开 Codex，并执行 /Users/twowt88/Documents/ChatGPT/WGAI-parallel/prompts/07-final-workspace-tool-isolation.md。Graphify、Serena、OpenSpec 只对最终 Git 根配置一次。真实 GPU 任务继续保持开放。
```
