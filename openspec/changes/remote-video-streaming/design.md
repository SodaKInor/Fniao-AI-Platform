## Context

参见 proposal.md。实时流需要跨越来源授权、provider 会话、持久事件、私有截图、前端轮询和停止/恢复边界；真实 RTX 5070 接口仍未确认，因此设计必须允许分项 disabled，并在 provider source ID 无法映射时停止实施。

本变更与 `remote-inference-platform` 的 `1.1.0` 修订共享用户身份、能力可用性、私有资产、错误确定性和串行工作包，但流会话不复用文件任务状态机。技术栈保持 Java 8 / Spring Boot 2.6.6 与 Vue 2。

## Goals / Non-Goals

**Goals:**

- 用不透明本地来源 ID 和后端 provider 映射隔离 RTSP/GPU 秘密。
- 提供可持久、可恢复、可停止并能主动查询事件/截图的流会话。
- 对重复启动、启动响应丢失、事件重复、停止竞争和迟到事件给出确定状态。
- 让旧 RTSP 来源按证据逐项迁移，未映射项保持停用。

**Non-Goals:**

- 不传输或代理直播视频，不实现标注直播、WebRTC/HLS 转码或后端 RTSP 中继。
- 不新增 provider 回调入口；首期仅主动查询事件。
- 不允许浏览器或普通业务 API 提交 RTSP 地址、GPU 地址或凭据。
- 不修改 GPU 服务源码、`backend-master`、历史数据或框架大版本。

## Decisions

### 1. 本地来源与 provider 来源分离

`ai_stream_source` 保存本地 `streamSourceId`、显示信息、授权范围、启停状态、provider source 引用和非敏感配置快照。RTSP 地址及凭据继续由服务端受控配置或凭据引用管理，不进入业务 DTO。选择“provider 已登记来源”而不是传 URL，可避免浏览器泄密、SSRF 和业务后端承担视频带宽。

替代方案是后端转发 RTSP 或让浏览器提交地址；两者改变安全和容量架构，明确排除。真实服务若只接受明文 RTSP，先修订规格和重新评审，不在实现中静默变通。

### 2. 流会话有独立状态机

状态集合为 `PENDING`、`STARTING`、`RUNNING`、`STOP_REQUESTED`、`STOPPED`、`FAILED`、`UNKNOWN`。本地 sessionId 在外呼前持久化；providerSessionId 只在 provider 返回后保存。STARTING 响应丢失时，有查询能力则对账，无查询能力进入 UNKNOWN，不重发启动。

文件任务状态机适合一次性输入/结果，无法准确表达持续事件游标和停止竞争，因此不复用 `ai_job`。两者共享 `ExecutionCertainty`/未知原因、用户归属和审计格式。

### 3. 启动使用本地幂等，外部去重按确认能力

创建接口要求 Idempotency-Key，并以用户、来源、能力和规范化参数形成摘要。同 key 同请求返回原 sessionId，同 key 不同请求冲突。本地去重只保证业务后端最多自动派发一次；除非 provider 明确承诺外部幂等，否则不声称跨响应丢失可安全重试。

### 4. 主动轮询事件并持久化游标

provider 端口提供 start/getEvents/getSession/stop 的独立方法，但只有真实契约确认的方法才启用。事件查询使用 providerSessionId 与外部游标，返回有界事件页和 nextCursor。事务内先按 `(session_id, provider_event_id)` 去重并保存截图资产关联，再提交新游标；保存失败不推进游标。

回调会增加公网/局域网反向可达、鉴权、重放与部署负担，不作为首期依赖。有效空事件页是成功查询，不触发失败或重启会话。

### 5. 截图复用私有资产存储

provider 事件可内嵌小型截图或返回批准主机上的相对成果引用；client 层按媒体类型、长度、哈希、主机和重定向规则取回。完整写入私有存储并保存资产记录后，事件才引用该 assetId。跨用户/跨会话引用拒绝，部分文件不发布。

### 6. 停止是确认协议而不是 UI 动作

STOP 请求先以版本条件把会话移到 `STOP_REQUESTED`，再调用已确认的 provider stop。确认同一 providerSessionId 已停止后进入 STOPPED；不支持时不发送；超时/断线时记录 `STOP_CONFIRMATION_UNKNOWN` 并按查询能力对账。关闭页面只停止浏览器轮询，不改变会话状态。

终态写入使用版本/状态条件。迟到事件可以保留审计计数，但不得改变终态、重复展示或创建跨终态的新截图。

### 7. V002 与平台迁移作为一个部署集合

04a 作为迁移所有者，与平台任务类型判别一起交付 V002：

```text
ai_stream_source(stream_source_id, owner_or_scope, display_name,
                 provider_source_ref, enabled, config_snapshot, version, ...)
ai_stream_session(session_id, owner_id, stream_source_id, state,
                  idempotency_key, request_digest, provider_session_id,
                  cursor, unknown_reason, config_snapshot, version, ...)
ai_stream_event(event_id, session_id, provider_event_id, occurred_at,
                offset_ms, event_type, payload_json, snapshot_asset_id, ...)
```

唯一约束覆盖本地幂等 key 和 provider 事件去重。`payload_json` 只保存由有界 DTO 序列化的已知字段，不存任意供应商 Map。迁移只新增表/索引/兼容列，可重复执行；回滚旧应用版本时保留新表和数据，不执行破坏性降级。

### 8. 前端按会话代次轮询

页面先读取授权来源，再创建会话并进入详情。事件查询携带持久游标；页面 generation 与 sessionId 共同隔离旧响应。离开页面时清理定时器和未完成 UI 引用，但不调用 stop；只有用户明确操作停止按钮才请求后端停止。终态后停止轮询，刷新继续同一会话和游标。

页面只展示来源名、会话状态、时间线、截图和可解释错误；不显示 providerSessionId、RTSP、GPU URL 或凭据。

### 9. 真实契约与发布门禁

02 冻结业务 OpenAPI/DTO 和 provider 契约样例；03 实现严格 provider；04a 实现表/恢复/停止；04b 实现页面。00 按每包 HANDOFF 串行验收。05 必须从业务后端容器完成真实会话、事件、截图和历史读取；06 验证重复启动、启动响应丢失、重启对账、事件去重、停止竞争/不支持/未知和迟到事件。

5.1—5.4、6.1—6.5 与本变更任务全部通过前，07 和 RC 阻断。source ID 无法映射属于契约阻断，不是可以用中继规避的实现缺陷。

## Risks / Trade-offs

- [provider 只接受 RTSP URL] → 保持 disabled 并修订契约，不泄露秘密或引入中继。
- [事件轮询导致负载] → 有界分页、单调游标、退避和每会话/全局并发限制，以真实服务容量校准。
- [停止确认丢失] → STOP_REQUESTED/UNKNOWN 原因和可选查询对账，不伪称停止。
- [事件与截图事务跨文件系统] → 文件先写临时区并校验，事务提交后发布；失败不推进游标并清理残片。
- [旧来源权限不完整] → 默认不迁移执行，只保留管理/历史并要求显式授权映射。

## Migration Plan

1. 02 从第四轮放行祖先快进，冻结 `video-stream-analysis.v1` 的五个业务接口、DTO、状态/未知原因、provider 契约与样例；00 记录新共同起点。
2. 03、04a、04b 按平台设计的串行顺序实现，每包经 00 验收后释放下一包。V002 在含旧图片/RTSP 数据的副本执行两次并核对表/行摘要。
3. 初始部署所有流来源为 disabled。只为已确认 provider source 映射和权限的来源启用，并在 RTX 5070 完成真实事件/截图/停止验收。
4. 06 完成恢复、去重、迟到事件和停止边界后，00 执行统一 6.5；通过后 07 才迁移旧 RTSP 页面。
5. 回滚应用时停用新会话创建并保留流表、事件和截图；已确认运行中的会话按真实 stop 能力处理，不删除历史或猜测远程终止。

## Open Questions

- provider 的登记来源字段、会话/事件分页格式和版本是什么？
- provider 是否支持按 request/session ID 查询、启动去重与停止确认，保留窗口多久？
- 事件截图是内嵌内容还是受控相对下载引用，最大大小和有效期是多少？

这些信息缺失时对应能力保持 disabled，不改变上述安全、状态和发布门禁。
