# WGAI 开发工具部署报告

生成时间：2026-09-02（America/Los_Angeles）  
工作区：`/Users/twowt88/Documents/ChatGPT/WGAI`

## 1. 结论

Serena、Graphify 和 OpenSpec 已部署到 WGAI，并完成配置、索引、建图、工作流技能、管理脚本及主要验收。

- Serena 是唯一配置的 WGAI MCP 服务，地址为 `http://127.0.0.1:9121/mcp`。
- Serena Dashboard 为 `http://127.0.0.1:24282/dashboard/index.html`。
- Graphify 仅做本地 AST/SQL 代码图谱，不注册或运行 MCP。
- OpenSpec 仅提供项目目录和六个核心技能，不注册 MCP，也不创建 Git hooks。
- `backend-github` 和 `frontend-vue` 是唯一实施目标。
- `backend-master` 仅供 Graphify 只读分析；部署前后 SHA-256 清单完全一致。
- 根目录和三个代码目录均不是 Git 仓库；未执行 `git init`，未安装 Git hooks。

唯一未完全自动化的项目是 Serena 的登录自动启动：macOS 隐私控制阻止后台 LaunchAgent 读取位于 `Documents` 下的项目。LaunchAgent 和自动回退机制均已安装；当前 Serena 由用户会话中的独立常驻进程提供服务，MCP 与 Dashboard 均在线。第 9 节记录了一次性处理方式。

## 2. 安全、备份与只读证明

本次部署时间戳：`20260902-060844`

用户配置备份：

`/Users/twowt88/.codex/backups/wgai-setup-20260902-060844/`

其中包含：

- `config.toml.before`
- `zprofile.before`
- `serena_config.generated.yml`
- 三次 LaunchAgent 诊断版本快照

项目备份与审计：

`/Users/twowt88/Documents/ChatGPT/WGAI/.setup-backups/wgai-setup-20260902-060844/`

其中包含：

- `backend-master.before.sha256`
- `backend-master.after.sha256`
- Graphify 初始生成的 `AGENTS.md`/hooks 基线
- Serena 初始生成的项目配置基线

`backend-master` 两份清单各 1,890 行，逐字节比较结果为 `MATCH`。清单记录相对路径、内容 SHA-256 和符号链接目标；部署时间以后没有发现该目录中的新修改，也没有生成 `target` 目录。

部署前不存在项目级 `AGENTS.md`、`.codex/config.toml`、`.codex/hooks.json`、OpenSpec 数据或 WGAI LaunchAgent，因此这些属于新增文件；已有用户配置均先备份后合并，未知字段未被清空。

## 3. 工具版本与绝对路径

| 工具 | 版本 | 绝对路径或位置 |
| --- | --- | --- |
| uv | 0.12.9 | `/Users/twowt88/.local/bin/uv` |
| uv Python | 3.13.15 | `/Users/twowt88/.local/share/uv/python/cpython-3.13.15-macos-x86_64-none/bin/python3.13` |
| Serena | 1.7.0 | `/Users/twowt88/.local/bin/serena` |
| Serena hooks | 1.7.0 工具环境 | `/Users/twowt88/.local/bin/serena-hooks` |
| Graphify | 0.9.53 | `/Users/twowt88/.local/bin/graphify` |
| Node.js | v24.14.0 | `/Users/twowt88/.nvm/versions/node/v24.14.0/bin/node` |
| npm | 11.11.0 | `/Users/twowt88/.nvm/versions/node/v24.14.0/bin/npm` |
| OpenSpec | 1.11.0 | `/Users/twowt88/.nvm/versions/node/v24.14.0/bin/openspec` |
| OpenJDK | 21.0.12.1 | `/usr/local/opt/openjdk@21/bin/java` |
| Maven | 3.9.16 | `/usr/local/bin/mvn` |
| Codex CLI | 0.152.1 | `/Applications/ChatGPT.app/Contents/Resources/codex` |

`~/.zprofile` 已加入带 WGAI 标记且可幂等更新的 PATH/JAVA_HOME 区块；保留了原有 Docker 配置。`uv tool update-shell` 创建了 `~/.zshenv` 的 uv PATH 设置。项目源码兼容级别和 POM 均未修改。

Serena 在 Intel macOS 上构建依赖时需要 OpenSSL，因此同时安装了 Homebrew `openssl@3` 3.6.4 与 `pkgconf` 3.0.6。Graphify 为解析仓库中的 SQL 文件安装了其官方 `sql` extra（`tree-sitter-sql`）；未安装或配置 Graphify MCP 服务。

## 4. Serena

### 项目与索引

- 全局配置：`/Users/twowt88/.serena/serena_config.yml`
- 项目配置：`.serena/project.yml`
- 仅注册一个项目：WGAI 根目录
- 语言服务：`java`、`typescript`、`vue`；TypeScript 语言服务同时覆盖 JavaScript/TypeScript
- Serena 精确符号索引工作区：`backend-github`、`frontend-vue`
- `backend-master` 不进入可编辑的 Serena LSP 工作区，避免 Java 语言服务向只读参考树写入构建状态；它仍由 Graphify 纳入全工作区只读架构图
- 索引总量：2,621 个文件，其中 Java 1,744、TypeScript/JavaScript 231、Vue 646
- 最终 health check：通过；日志为 `.serena/logs/health-checks/health_check_20260902-063433.log`

### 服务与 Codex 配置

- MCP：`http://127.0.0.1:9121/mcp`
- Dashboard：`http://127.0.0.1:24282/dashboard/index.html`
- 监听范围：两个端口均仅为 `127.0.0.1`
- Dashboard 自动打开：禁用
- 项目 MCP 名称：`serena_wgai`
- 传输：`streamable_http`，没有 stdio command
- 启动超时：30 秒；工具超时：120 秒；默认写工具审批模式：`writes`
- Codex 0.152.1 已成功解析 `codex mcp get/list`
- 标准 MCP `initialize`：HTTP 200，返回 session ID，协商协议 `2025-06-18`
- Dashboard HTTP 检查：200

`serverInfo.version` 在 MCP initialize 响应中显示为 `1.28.1`，这是 Serena 所用 MCP 服务库报告的服务版本；实际安装的 Serena CLI/包版本经 `serena --version` 验证为 `1.7.0`。

### 服务管理

LaunchAgent：

`/Users/twowt88/Library/LaunchAgents/com.local.wgai-serena.plist`

标签：`com.local.wgai-serena`。配置包含 `RunAtLoad`、失败时保活、5 秒节流、显式 PATH/JAVA_HOME、固定 Serena 安装、WGAI 日志路径，以及固定的 streamable HTTP 启动参数。后台包装器为 `/Users/twowt88/.local/bin/wgai-serena-service`，不会调用 `uvx`。

管理入口：

- `tools/serena/start`
- `tools/serena/stop`
- `tools/serena/restart`
- `tools/serena/status`
- `tools/serena/logs`
- `tools/serena/open-dashboard`

脚本均无扩展名、可执行、通过 zsh 语法检查。`start`、`stop`、`restart` 和重复 `start` 均已实测；当 LaunchAgent 受隐私策略阻止时，`start` 会自动建立独立的用户会话常驻进程。`open-dashboard` 从日志解析实际地址，并会探测 Serena 在默认 Dashboard 端口被占用时实际选择的后续端口；验收期间未调用该脚本打开浏览器。

日志：

- `.serena/runtime/serena.stdout.log`
- `.serena/runtime/serena.stderr.log`

最终检查时状态：LaunchAgent 未载入；回退管理进程 PID 68996，Serena PID 68998；MCP 与 Dashboard 在线。PID 会在重启服务后变化，应以 `tools/serena/status` 为准。

## 5. Graphify

- 安装版本：`graphifyy==0.9.53`，含本地 SQL 解析 extra
- 安装方式：`graphify install --project --platform codex`
- 权威技能：`.codex/skills/graphify`
- Codex 规范入口：`.agents/skills/graphify`，是指向权威副本的符号链接
- 忽略规则：`.graphifyignore`；排除缓存、压缩包、工具生成目录和构建产物，但没有排除 `backend-master`
- 首次建图及后续强制更新均使用本地 `--code-only`/本地解析路径，没有 API Key，也没有向外部 LLM 上传源码

输出：

| 文件 | 大小 |
| --- | ---: |
| `graphify-out/graph.json` | 约 75 MiB |
| `graphify-out/graph.html` | 约 1.4 MiB |
| `graphify-out/GRAPH_REPORT.md` | 约 170 KiB |

`graph.json` 已通过 JSON 解析：40,749 个节点、105,193 条链接、1,745 个社区，节点和链接均大于零。Graphify 对六个存在语法异常的 Vue 文件进行了部分抽取，并正常完成全图生成。

工具入口：

- `tools/graphify/update`：从任意目录更新 WGAI 图；“没有变化”也作为幂等成功处理
- `tools/graphify/query-example`：执行指定中文问题，并追加等义英文查询

中文查询“帮我梳理后端启动入口、前端 API 调用入口、数据库初始化脚本与核心模块关系”已执行成功，返回 272 个相关节点；英文等义查询返回 46 个相关节点。增量更新脚本已实测成功。

安装包会附带名为 `graphify-mcp` 的可执行文件，但它没有被运行或注册。进程检查及 `codex mcp list` 均确认没有 Graphify MCP 服务。

## 6. OpenSpec

- 安装版本：`@fission-ai/openspec@1.11.0`
- 初始化命令语义：Codex 工具、core profile、不启用 Copilot cloud
- 项目数据：`openspec/config.yaml`、`openspec/specs/`、`openspec/changes/`、`openspec/changes/archive/`
- `openspec list --json` 正确定位到 WGAI，当前 changes 为空

六个核心项目技能均存在：

- `.agents/skills/openspec-explore`
- `.agents/skills/openspec-propose`
- `.agents/skills/openspec-apply-change`
- `.agents/skills/openspec-update-change`
- `.agents/skills/openspec-sync-specs`
- `.agents/skills/openspec-archive-change`

未发现旧 OpenSpec 数据需要迁移。OpenSpec 没有 MCP 配置或进程，也没有创建 Git hooks。

## 7. 项目路由、Hooks 与公共入口

根 `AGENTS.md` 保留 Graphify 托管区块，并增加 WGAI 路由：

1. Graphify 用于跨目录定位、依赖和调用链。
2. Serena 用于精确符号、引用和局部修改。
3. OpenSpec 用于需求探索、提案、实施跟踪、同步和归档。
4. 标准流程为 Graphify 定位 → Serena 精查/修改 → OpenSpec 记录长期变更。
5. `backend-master` 永远只读；实施只落到 `backend-github` 和 `frontend-vue`。

`.codex/hooks.json` 已通过 JSON 校验，并同时包含：

- Graphify Bash `PreToolUse`：`graphify hook-check`
- Serena Bash `PreToolUse`：`serena-hooks remind_session_started_if_needed`
- Serena `SessionEnd`：`serena-hooks cleanup_after_session_hook`
- 不含 `SessionStart`

用户 `~/.codex/config.toml` 已结构化保留原配置，并启用：

- `features.hooks = true`
- `features.codex_hooks = true`
- `features.multi_agent = true`

稳定公共入口为 `serena_wgai`、`tools/serena/*`、`tools/graphify/*`、`.agents/skills/graphify` 和 `.agents/skills/openspec-*`。

配置依据：Codex 官方 [配置参考](https://developers.openai.com/codex/config-reference)、[Hooks 文档](https://developers.openai.com/codex/hooks)和 [Skills 文档](https://developers.openai.com/codex/skills)。

## 8. 验收记录

| 验收项 | 结果 |
| --- | --- |
| 工具版本和绝对路径 | 通过 |
| Serena 全量索引与 health check | 通过 |
| MCP initialize | 通过，HTTP 200 |
| Dashboard | 通过，HTTP 200 |
| 仅监听 loopback | 通过 |
| Codex 读取 HTTP MCP 配置 | 通过 |
| 唯一 WGAI Serena 实例 | 通过；当前只有一个 Serena 服务进程 |
| LaunchAgent 自动启动 | 待一次 macOS 隐私授权；当前使用自动回退进程 |
| Graphify JSON/HTML/报告 | 通过，均非空，JSON 可解析 |
| Graphify 中英文查询 | 通过 |
| Graphify 增量更新 | 通过 |
| 无 Graphify/OpenSpec MCP | 通过 |
| OpenSpec 六个核心技能 | 通过 |
| hooks 合并与 JSON 校验 | 通过，无 SessionStart |
| 八个管理脚本语法/权限/幂等性 | 通过 |
| `backend-master` 前后清单 | 通过，完全一致 |
| Git 状态 | 根目录和三个代码目录均报告“不是 Git 仓库” |

## 9. 唯一待用户完成的权限步骤

macOS 15.7.9 的隐私控制不允许由 `launchd` 启动的进程直接读取用户 `Documents`。诊断中后台服务对 WGAI 路径返回 `Operation not permitted`，因此不能在不绕过系统安全设置的情况下自动完成登录自启授权。

如需登录后自动启动和异常自动拉起，请只做一次：

1. 打开“系统设置 → 隐私与安全性 → 完全磁盘访问权限”。
2. 添加并启用 Serena 的 uv Python：`/Users/twowt88/.local/share/uv/python/cpython-3.13.15-macos-x86_64-none/bin/python3.13`。
3. 返回终端运行：`/Users/twowt88/Documents/ChatGPT/WGAI/tools/serena/restart`。
4. 运行 `tools/serena/status`；预期显示 `launchd: running`，且不再显示 fallback。

若未来 uv 升级 Python 后该绝对路径改变，需要为新解释器重新授权。另一个不需要完全磁盘访问权限的选择是把整个 WGAI 工作区移出 `Documents`，但本次部署按指定路径保留项目，没有擅自移动代码。

当前无需立即授权也可使用：`tools/serena/start` 会自动回退到当前用户的独立常驻进程；该模式在注销或重启 macOS 后不会自动恢复。

## 10. Git 状态

以下四个位置均执行了 `git status --short --branch`：

- WGAI 根目录：`fatal: not a git repository`
- `backend-github`：`fatal: not a git repository`
- `frontend-vue`：`fatal: not a git repository`
- `backend-master`：`fatal: not a git repository`

未初始化 Git，也未安装任何 Git hook。

## 11. 回滚步骤（未自动执行）

1. 运行 `tools/serena/stop`，停止 LaunchAgent 或回退服务。
2. 将当前 LaunchAgent 和新增项目工具产物移动到项目备份区留存，不直接删除。
3. 从 `/Users/twowt88/.codex/backups/wgai-setup-20260902-060844/config.toml.before` 恢复 `~/.codex/config.toml`。
4. 从 `/Users/twowt88/.codex/backups/wgai-setup-20260902-060844/zprofile.before` 恢复 `~/.zprofile`。
5. 根据项目备份中的生成基线移除或恢复 `AGENTS.md`、`.codex`、`.agents/skills`、`.serena`、`openspec`、`graphify-out`、`.graphifyignore` 和 `tools` 中本次新增内容。
6. `~/.zshenv` 在部署前不存在；回滚时仅在确认仍只包含 uv 安装器添加的 PATH 区块后将它移动到备份区。
7. 只有明确要求完全卸载时，才运行 `uv tool uninstall serena-agent`、`uv tool uninstall graphifyy` 和 `npm uninstall -g @fission-ai/openspec`。Homebrew 的 JDK、Maven、OpenSSL 和 pkgconf 可能被其他项目共享，不应在普通项目回滚中自动卸载。
8. 回滚后重新生成 `backend-master` 清单并与 `backend-master.before.sha256` 比较。

## 12. 后续建议

所有验证完成后重启一次 Codex 桌面应用，使当前桌面会话重新加载项目 hooks、skills 和 MCP 配置。本次部署没有自动重启 Codex。
