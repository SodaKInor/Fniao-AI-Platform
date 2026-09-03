## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

# WGAI Development Tool Routing

- Use Graphify first for cross-directory discovery, module relationships, dependencies, and call chains.
- Use Serena first for exact symbols, references, and focused code changes.
- Use OpenSpec for requirements exploration, proposals, implementation tracking, spec synchronization, and archival.
- `backend-github` and `frontend-vue` are the only implementation targets.
- `backend-master` is reference-only and must never be modified.
- Follow the standard workflow: Graphify locates the relevant area, Serena performs precise inspection or editing, and OpenSpec records durable changes.
- When the knowledge graph is stale, run `tools/graphify/update` from anywhere in the workspace.
- Check the Serena service with `tools/serena/status` and inspect logs with `tools/serena/logs`.
- Do not configure Graphify or OpenSpec as MCP servers.
- Do not configure an additional stdio Serena MCP server; `serena_wgai` is the single project MCP endpoint.

# Remote AI architecture and parallel work

- Before implementing remote AI, read `backend-github/development/remote-inference/ARCHITECTURE.md`, `FILE_OWNERSHIP.md`, and `PARALLEL_PLAN.md`.
- Follow the assigned work package; do not implement the entire OpenSpec task list from an individual package.
- Controllers, application workflows, provider clients, persistence, and file storage have separate responsibilities. Frontend pages, components, API calls, and polling logic are separate modules.
- Each parallel conversation uses its assigned code worktree and branch. Never substitute the original WGAI directory when a worktree is missing.
- Respect the package's file ownership. Changes to shared contracts and public types go through the contract owner; integration updates the master task checklist after verifying handoff evidence.
- Existing `tools/graphify/update` and Serena service-management scripts contain absolute paths to the original workspace. In a worktree run `graphify update .` from the verified worktree root; do not run the original-path update script there. Do not switch the shared Serena project during parallel edits or use it to write to an unverified workspace.
