# 并行文件归属

本表适用于 08 顶层目录迁移之前。每包只在自己的 `code/` worktree 写入并提交；最终路径由 08 统一移动，其他包不能提前各自重命名顶层目录。

当前 Java AI 根为 `backend-github/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/ai/`，前端 AI 文件位于 `frontend-vue/src`。目标功能模块见 `ARCHITECTURE.md`。

| 区域/文件 | 所有者 | 约束 |
|---|---|---|
| 集成分支、OpenSpec 总表、架构、文件归属、共享构建/配置冲突 | 00-integration | 核实交接后修改；记录 stub/真实证据边界 |
| 既有业务/provider 契约、公共 DTO、状态与端口 | 02-contract，经 00 协调 | 已冻结类型不由消费包私自更改；stub 所需新增字段先走契约审查 |
| `AI_ROOT/client`、provider 端口与配置 | 03-client；后续修复由 00 明确分派 | provider 严格转换、网络、凭据和信任；不访问业务表 |
| 资产、任务、结果、流会话的应用/持久化/存储 | 04a；恢复补丁由 06 | 06 从已集成 04a 起点工作，不同时修改同一文件 |
| 当前 `frontend-vue/src/{api,services,components,views}/ai` 及 AI 路由 | 04b；故障补丁由 06 | 07 才统一移动到功能模块；此前保持契约兼容 |
| 旧后端/前端执行守卫和能力门禁 | 03/04b；清理由 07 | 直接请求与菜单一致，remote/disabled 不回退旧算法 |
| `backend-github/deploy/remote-ai/migrations` 的既有 V001/V002 | 04a 内容所有；08 路径迁移 | 已交付内容、版本、校验值和顺序不得改写 |
| 05 fail-closed 工具、provider stub 契约增量、独立 HTTP stub、fixtures、stub Compose 和模拟证据 | 05-lan | 可在当前仓库根新增 `remote-inference/stub` 等过渡目录；不修改真实证据目录，不冒充 GPU |
| job/stream 恢复、取消/停止、竞态、指标和日志 | 06-resilience | 先合入 05 stub；用 stub 注入故障，不擅自修改公共契约 |
| AI_ROOT 与前端 AI 代码的功能模块迁移、旧入口和无引用依赖清理 | 07-cleanup | 按 capability/asset/job/result/image/video/stream/provider/operations/legacy 分组；每组独立提交和回归 |
| `backend-github`→`apps/backend`、`frontend-vue`→`apps/frontend`、database/remote-inference/deploy/docs 顶层归整 | 08-release | 只在 07 通过后的结构分支执行 `git mv`；统一更新所有构建和工具路径 |
| 最终 main 合并、推送、独立克隆和工具迁移门禁 | 00-integration | 验证 08 报告后执行；不删除旧工作区 |
| RTX 5070/4090 真实契约、网络与成果证据 | future-real-gpu | 服务到位后单独分配；stub 证据不得写入 |
| `integrations/ai-contracts/acceptance/<包名>` 或迁移后的 `remote-inference/acceptance/<包名>` | 对应包 | 包之间使用不同子目录，不改他包历史证据 |
| 各工作包 `HANDOFF.md`、`TASKS.md` 和 `drafts/` | 对应包 | 00 只读取交付，不覆盖他包记录；总状态写集成 OpenSpec |

## 功能模块迁移归属

07 对 AI 业务代码执行逻辑移动和依赖修正：

- capability：现有能力查询、绑定与可用性。
- asset：资产 API、服务、存储和持久化。
- job：任务 API、用例、状态、仓储与 worker。
- result：成果校验、解释、下载关联与回存端口。
- image/video/stream：媒体特有 API、参数、事件和页面。
- provider：端口、HTTP client、wire DTO 和配置。
- operations：健康、指标、诊断和日志边界。
- legacy：过渡适配、停用提示和直接请求拒绝。

公共类型需要变化时先交给 00 协调 02 复核。07 不为人脸、车牌、安全帽等模型名称复制整套模块，也不创建无调用者的 audio/chat/training 空目录。

## 公共文件

根 `pom.xml` 相关聚合、`package.json`/锁文件、Compose、AGENTS、OpenSpec、Graphify/Serena 管理脚本和最终路径引用由 00/08 串行处理。包需要新增依赖时在 HANDOFF 记录必要性、版本和最小补丁，由集成统一应用。

所有者表约束提交范围。越界变更必须在写入前由 00 重新分配；不能完成后以“顺手修复”为由合并。
