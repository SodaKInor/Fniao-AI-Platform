# 远程推理平台并行与串行计划

## 1. 当前状态

`WGAI-parallel/*/code` 是临时 Git worktree，每个目录包含完整仓库的一条分支。最终项目不会把这些目录复制到一起，而是由 00 验收并合并提交后形成。

截至 2026-09-04：01、02、03、04a、04b 已进入 `feature/remote-inference` 并通过相应门禁；05 已交付拒绝伪造真实证据的 fail-closed 校验；06 有尚未合入的本地恢复/竞态/观测候选；07 只有只读清理盘点；08 只有恢复与发布草案。同事尚未提供 GPU 服务。

## 2. 最新工作包职责

| 包 | 最新职责 | 是否依赖真实 GPU |
|---|---|---|
| 00-integration | OpenSpec、架构、共享冲突、分支合并、组合验收、总状态和最终结构门禁 | 真实门禁阶段才依赖 |
| 01-foundation | 基线、备份、来源和隔离工作区；已完成 | 否 |
| 02-contract | 业务/provider 契约和公共类型；已有版本冻结，后续公共变化经 00 协调 | 否 |
| 03-client | provider 端口、严格 HTTP 适配、凭据、信任和转换；已有候选已集成 | 否 |
| 04a-assets-jobs | 资产、任务、结果、流持久化和 V001/V002；已集成 | 否 |
| 04b-frontend | 图片、上传视频、实时流、历史和结果页面；已集成 | 否 |
| 05-lan | 保留 fail-closed 校验，新增独立 HTTP stub、fixtures、Compose profile 和 remote→stub 组合验收 | 否；真实 5070 转为后续门禁 |
| 06-resilience | 基于 stub 验证恢复、UNKNOWN、取消/停止、事件竞争、观测和日志 | 否 |
| 07-cleanup | 后端/前端按功能模块迁移，旧聊天/训练及无引用算法依赖分组清理 | 否；未知真实能力保持 disabled |
| 08-release | 顶层目录整合、数据库归整、部署路径、全量本地 RC 和结构交接；00 负责最终合并与独立克隆 | 否；不宣称正式 GPU 完成 |
| future-real-gpu | RTX 5070 局域网真实契约/成果验收，再做 RTX 4090 48GB 正式验收 | 是；服务到位后再创建工作包 |

## 3. 执行顺序

```text
已完成：01 → 02 → 03 → 04a → 04b → 00 验收

下一阶段：
05 HTTP stub
      ↓
06 本地恢复与故障
      ↓
00 统一 stub/disabled 门禁
      ↓
07 功能模块迁移与旧业务清理
      ↓
08 顶层目录整合与本地 RC
      ↓
00 合并 main、推送、创建独立最终克隆

外部服务到位后：
RTX 5070 局域网验收 → RTX 4090 48GB 正式验收 → 规格同步/归档
```

05 与 06 不再并行修改共享行为。05 先交付 stub 和冻结场景，00 验收后 06 从新共同起点快进并复用其已经完成的候选。08 的只读资料准备可以继续，但顶层移动必须等待 07 完成。

## 4. 合并规则

1. 包只提交自己的代码和证据，更新本包 HANDOFF。
2. 00 核对提交范围、契约、测试、秘密与 Graphify 后合并。
3. 已包含在集成分支中的提交不重复 cherry-pick/merge；用祖先关系判断。
4. 发生冲突逐文件解决，不用整目录覆盖，不删除另一包已通过行为。
5. 先合并行为，再在 08 独立结构分支执行 `git mv`；目录重命名不与功能开发混在同一提交。
6. 真实 GPU 任务未完成不阻止本地 stub/disabled 候选，但阻止真实能力启用、正式发布结论和 OpenSpec 归档。

## 5. 运行资源与证据

- 每个包继续使用独立数据库、上传目录、端口和 Compose 项目名。
- 05 stub 使用本包专属端口和容器名，不修改同事机器或假装其为 RTX 服务。
- 06 的故障注入只针对 stub/本包资源，不停止共享演示环境或未来同事服务。
- stub、真实 5070、正式 4090 的证据放在不同目录并含环境类型字段；模拟证据不能进入真实门禁。
- 私有账号、凭据、原始数据库、素材和证书只保存在对应 drafts/private 或外部备份中，不提交。

## 6. 最终目录阶段

08 以前所有包仍在当前 `backend-github`、`frontend-vue` 路径工作。08 从 00 放行 SHA 创建结构分支，统一迁移为：

```text
apps/backend
apps/frontend
database
remote-inference
deploy
docs/remote-inference
openspec
tools
```

08 提交结构迁移和本地 RC 后由 00 独立验收；00 合并 main 并推送，再从远程创建 `/Users/twowt88/Documents/ChatGPT/Fniao-AI-Platform` 独立克隆。它不能是旧仓库 worktree，也不复制 `WGAI-parallel`、`backend-master`、graphify-out 或 Serena cache。用户在新目录打开 Codex，再配置一次该仓库自己的 Serena、Graphify 和 OpenSpec。

## 7. 工具规则

- 在当前 worktree 中执行 `graphify update .`，不要调用硬编码旧 WGAI 的脚本。
- 共享 Serena 在并行阶段不切换、不新增第二个服务；精确编辑只能在确认项目根匹配时使用。
- 00 完成新克隆后，停止旧并行会话，再把唯一 Serena 服务切到最终目录并在那里重建索引。
- OpenSpec 总状态只由 00 按证据更新；stub 完成不允许勾选 RTX 5070/4090 任务。
