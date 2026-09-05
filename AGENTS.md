## graphify

This project uses a knowledge graph at `graphify-out/` after the final-root rebuild. A fresh independent clone may not have that local ignored directory yet; when it is absent during stage A or the two parallel moves, use the frozen architecture documents and focused `rg` searches, then build the graph once in stage D.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

# Fniao AI Platform Development Tool Routing

- Use Graphify first for cross-directory discovery, module relationships, dependencies, and call chains when the final-root graph exists; before the stage D rebuild, use the frozen architecture documents and focused searches.
- Use Serena first for exact symbols, references, and focused code changes.
- Use OpenSpec for requirements exploration, proposals, implementation tracking, spec synchronization, and archival.
- 第 8 批目录整合已完成；唯一应用目标是 `apps/backend` 和 `apps/frontend`。
- `backend-master` 只存在于旧工作区，始终只读且不得进入本仓库。
- Follow the standard workflow: Graphify locates the relevant area, Serena performs precise inspection or editing, and OpenSpec records durable changes.
- When the knowledge graph is stale, run `tools/graphify/update` from anywhere in the workspace.
- Check the Serena service with `tools/serena/status` and inspect logs with `tools/serena/logs`.
- Do not configure Graphify or OpenSpec as MCP servers.
- 并行目录迁移期间不要切换旧 `serena_wgai` 项目。阶段 D 结束后只把一个 Serena 项目指向 `/Users/twowt88/Documents/ChatGPT/Fniao-AI-Platform`，不要为各顶层目录或临时 worktree 分别配置。

# Remote AI architecture and parallel work

- 活动架构、文件归属和运行计划统一从 `docs/remote-inference` 读取；`remote-inference/acceptance` 内 01—07 路径只代表当时的历史执行环境。
- Follow the assigned work package; do not implement the entire OpenSpec task list from an individual package.
- Controllers, application workflows, provider clients, persistence, and file storage have separate responsibilities. Frontend pages, components, API calls, and polling logic are separate modules.
- 最终 Git 根通过 `git rev-parse --show-toplevel` 动态确定；不得把旧 WGAI 或已结束的临时工作树当作活动源码目录。
- Respect the package's file ownership. Changes to shared contracts and public types go through the contract owner; integration updates the master task checklist after verifying handoff evidence.
- 当前 `tools/graphify/update` 和 Serena 管理脚本仍可能含旧工作区路径，阶段 D 必须改为从 Git 根解析。在并行工作树不要运行旧路径脚本或切换共享 Serena。两个并行分支合并后，只在最终 Git 根重建一次 Graphify 并配置一次 Serena；OpenSpec 直接使用仓库内目录，不复制或配置为 MCP。
