# 第 8 批可分次复制的新对话提示词

固定目录与分支：

- 最终集成目录：`/Users/twowt88/Documents/ChatGPT/Fniao-AI-Platform`
- 集成分支：`codex/final-layout`
- 当前规划起点：`c58df289674c2b246334a4d005ad5ba1c90fae80`
- 临时工作树父目录：`/Users/twowt88/Documents/ChatGPT/Fniao-AI-Platform-worktrees`
- 数据库分支：`codex/database-layout`
- 远程边界分支：`codex/remote-boundary`

按顺序执行：先运行提示词 1；它创建工作树后，同时运行提示词 2A 和 2B；两者都完成后运行提示词 3。不要提前运行提示词 3。

## 提示词 1：串行建立 apps，并准备两个并行目录

```text
请在 /Users/twowt88/Documents/ChatGPT/Fniao-AI-Platform 完成第 8 批阶段 A，并为下一轮并行任务建立两个同级 Git worktree。直接实施、验证并提交，不要只给计划。

开始前核对：Git 根必须正好是上述目录；分支必须是 codex/final-layout；工作区必须干净；HEAD 必须是 c58df289674c2b246334a4d005ad5ba1c90fae80 或仅包含本提示词计划修订的后继提交。读取根 AGENTS.md、backend-github/development/remote-inference/{ARCHITECTURE.md,FILE_OWNERSHIP.md,PARALLEL_PLAN.md,PROMPTS.md}、openspec/changes/remote-inference-platform 和 remote-video-streaming。使用 Graphify 做跨目录定位；不要切换旧 WGAI 的共享 Serena 项目。

本阶段只完成：
1. 用 git mv 将 backend-github 整体移动为 apps/backend，将 frontend-vue 整体移动为 apps/frontend。
2. 修复 Maven、npm、许可证、直接构建命令和本阶段最小验证所必需的路径。不要移动或新建最终 database、remote-inference、docs/remote-inference、deploy/remote-inference 内容；这些属于下一轮。
3. 生成旧路径到新路径的迁移清单，确认 Java 包名、前端功能模块和业务行为没有改变。
4. 从 apps/backend 执行必要的 Java 8 构建/测试，从 apps/frontend 执行必要的 Vue 构建/测试，并检查 Git 能把主要变更识别为重命名。
5. 更新本分支内已经失效的活动说明，但不要改写 01—07 的历史验收证据中的旧绝对路径。
6. 提交阶段 A，记录提交为 08A_SHA。

阶段 A 提交且工作区恢复干净后，从同一个 08A_SHA 创建：
- /Users/twowt88/Documents/ChatGPT/Fniao-AI-Platform-worktrees/database-layout，分支 codex/database-layout；
- /Users/twowt88/Documents/ChatGPT/Fniao-AI-Platform-worktrees/remote-boundary，分支 codex/remote-boundary。

如果目录或分支已经存在，先核实它确实指向本仓库、正确分支和同一 08A_SHA；不要覆盖不明目录。确认两个工作树都干净、HEAD 相同。不要在本对话实施 2A 或 2B，也不要推送。最后给出 08A_SHA、阶段 A 提交、两条工作树路径、分支、HEAD、验证结果以及可开始并行的明确结论。
```

## 提示词 2A：数据库目录包（与 2B 并行）

```text
请在 /Users/twowt88/Documents/ChatGPT/Fniao-AI-Platform-worktrees/database-layout 完成数据库目录归整。直接实施、验证并提交，不要只给计划，也不要合并或推送。

开始前核对 Git 根就是本目录，分支是 codex/database-layout，工作区干净；把初始 HEAD 记为 08A_SHA。确认同级 remote-boundary 工作树在开始工作前也由同一 SHA 创建。读取 AGENTS.md、apps/backend/development/remote-inference/{ARCHITECTURE.md,FILE_OWNERSHIP.md,PARALLEL_PLAN.md} 和两个 OpenSpec 变更。只使用本工作树；不要切换共享 Serena 项目。

唯一写入范围是 database/** 以及本包内部、用于证明数据库迁移正确的清单和验证器。完成：
1. 建立 database/bootstrap、database/migrations/ai-core、database/migrations/stream、database/seeds/stub、database/private。
2. 用 git mv 迁移现有脱敏初始化/清理入口、V001、V002 和 stub 能力绑定样例。V001/V002 的字节、校验值、版本号和执行顺序必须保持不变。
3. 代码生成器专用 SQL 留在 apps/backend 所属源码。database/private 只提交 README 和忽略规则，不提交真实转储、真实数据、凭据或本机路径。
4. 建立单一迁移清单，说明文件来源、目标、所有者、前置版本、校验值和重复执行行为。
5. 在隔离数据库副本验证 bootstrap、V001→V002 顺序和可接受的重复执行行为；不得接触现用数据库或数据卷。
6. 检查提交不包含 remote-inference/**、docs/**、根 Compose、apps 业务源码、OpenSpec 总表或共享工具脚本。跨包待修复路径只写入本包交接记录。

提交一个单一职责提交。最后报告 08A_SHA、提交 SHA、完整变更文件列表、V001/V002 前后校验值、验证结果和待集成阶段修复项，然后停止等待提示词 3 合并。
```

## 提示词 2B：远程推理边界包（与 2A 并行）

```text
请在 /Users/twowt88/Documents/ChatGPT/Fniao-AI-Platform-worktrees/remote-boundary 完成远程推理边界目录归整。直接实施、验证并提交，不要只给计划，也不要合并或推送。

开始前核对 Git 根就是本目录，分支是 codex/remote-boundary，工作区干净；把初始 HEAD 记为 08A_SHA。确认同级 database-layout 工作树在开始工作前也由同一 SHA 创建。读取 AGENTS.md、apps/backend/development/remote-inference/{ARCHITECTURE.md,FILE_OWNERSHIP.md,PARALLEL_PLAN.md} 和两个 OpenSpec 变更。只使用本工作树；不要切换共享 Serena 项目。

唯一写入范围是 remote-inference/**、docs/remote-inference/**、deploy/remote-inference/** 以及这些目录内部的清单、链接和验证器。完成：
1. 将版本化业务/provider 契约移动到 remote-inference/contracts，样例移动到 fixtures，独立 HTTP stub 移动到 stub，历史验收证据移动到 acceptance，接口交接材料移动到 handoff。
2. 将 apps/backend/development/remote-inference 的活动架构、运行、文件归属和提示词移动到 docs/remote-inference，并修复本包内部相对链接。
3. 将 apps/backend/deploy/remote-ai 中的非数据库远程 provider 配置、校验器和开发 profile 逐文件移动到 deploy/remote-inference。不要移动整个 remote-ai 父目录；不要接管 migrations、V001、V002 或 stub seed。
4. 保持 stub 继续按启动、配置、鉴权、请求体、路由、校验、场景、响应、fixtures、状态和测试拆分，不把代码合并成单文件。
5. 运行契约、fixtures、stub、链接和证据校验，确认模拟结果仍明确标识 simulated/stub，正式配置不会默认启动、引用或回退 stub。
6. 检查提交不包含 database/**、根 Compose、apps 业务源码、OpenSpec 总表或共享工具脚本。跨包待修复路径只写入本包交接记录。

提交一个单一职责提交。最后报告 08A_SHA、提交 SHA、完整变更文件列表、验证结果和待集成阶段修复项，然后停止等待提示词 3 合并。
```

## 提示词 3：串行合并、收口、工具重建与推送

```text
提示词 2A 和 2B 已经分别完成。请回到 /Users/twowt88/Documents/ChatGPT/Fniao-AI-Platform，在 codex/final-layout 分支完成合并、共享路径修复、本地 RC、工具定位和最终 Git 交付。直接实施到完成，不要只给计划。

开始前核对主工作区干净，并读取两个并行对话给出的 08A_SHA 与提交 SHA。确认 codex/database-layout 和 codex/remote-boundary 都以同一个 08A_SHA 为起点、各自工作树干净、提交均存在。比较 08A_SHA..两个分支的实际文件列表：归属必须零重叠；若出现越界或重叠，逐文件审查并修正后再合并，禁止复制目录或整目录覆盖。

依次把 codex/database-layout、codex/remote-boundary 的已验证提交合入 codex/final-layout。然后串行完成：
1. 修复根 Compose、Dockerfile 构建上下文、环境模板、Maven/npm 脚本、备份恢复脚本、README、AGENTS、OpenSpec 链接和所有活动文档路径。运行中的路径必须从 Git 根动态解析。01—07 的历史验收证据可保留旧绝对路径，但必须明确属于历史记录。
2. 最终结构必须是 apps/backend、apps/frontend、database、remote-inference、deploy/remote-inference、docs/remote-inference、openspec、tools。业务 AI 代码继续按 capability、asset、job、result、image、video、stream、provider、operations、legacy 功能模块组织；功能内再分 api/application/domain/port/persistence/storage/client/config。不要为具体算法名建立重复模块，不创建空 audio/chat/training 壳。
3. 正式 Compose 默认不得启动、引用或回退 stub；同事真实 GPU 服务未交付时，对应能力保持 disabled。浏览器只访问业务后端，业务后端经 provider HTTP 请求 GPU 端。真实 GPU URL、鉴权和 TLS 参数只来自无密钥模板对应的本地/部署秘密配置。
4. 更新 tools/graphify 和 tools/serena，使它们从当前 Git 根解析路径，不再硬编码 WGAI。并行工作树关闭后，只在最终根重建一次 Graphify 知识图并把唯一 Serena 项目指向 /Users/twowt88/Documents/ChatGPT/Fniao-AI-Platform。OpenSpec 已在仓库内，直接从最终根运行，不复制、不注册三套，也不把 Graphify/OpenSpec 配成 MCP。
5. 执行充分的 Java 8、Vue、数据库迁移、Compose 配置、权限、remote→stub 图片/视频/流、disabled、历史读取、秘密扫描、活动旧路径扫描、文件规模、模块依赖和两个 openspec validate --strict。生成 docs/FINAL_INTEGRATION_REPORT.md，明确本地 RC 只证明 simulated/disabled，RTX 5070 局域网与单张 4090 48GB 正式服务仍待同事交付后验收。
6. 提交串行收口变更。确认工作区干净后，从 origin 获取最新 main；禁止强推。如果 origin/main 不是当前结构分支的祖先，先安全合并远端变化并重跑受影响验证。随后将 codex/final-layout 合入 main 并推送 origin/main。
7. 推送成功后移除仅用于追溯旧本地仓库的 source-wgai remote。只有在两个同级工作树均干净、提交已合入 main 且没有未跟踪交付物时，才用 git worktree 正常移除其登记；不要删除旧 WGAI 仓库。

最后报告：main 与 origin/main 的提交 SHA、最终目录树、关键验证、Graphify/Serena/OpenSpec 状态、被保留为历史记录的旧路径范围，以及仍开放的 RTX 5070/4090 真实服务任务。
```

## 暂不执行：真实 GPU 服务提示词

同事尚未交付服务，本轮不创建或运行真实 GPU 工作包。服务到位后另起对话，只负责从业务后端容器通过局域网请求 RTX 5070，再在 Ubuntu 单张扩容 48GB RTX 4090 环境完成正式验收；不进入同事的算法仓库，也不把 stub 证据当作真实结果。
