## Purpose

定义业务系统通过授权的服务端登记来源启动远程实时视频分析、查询事件与截图、停止和恢复会话的稳定行为，同时阻止浏览器接触 RTSP 地址、GPU 地址或凭据。

## ADDED Requirements

### Requirement: Stream sources are opaque authorized resources

系统 SHALL 通过 `GET /ai/v1/stream-sources` 只返回当前用户有权使用的来源及不透明 `streamSourceId`。业务响应和日志 SHALL 不包含 GPU URL、RTSP 地址、用户名、密码或凭据引用的值。

#### Scenario: User lists authorized sources
- **WHEN** 已登录用户请求流来源列表
- **THEN** 系统仅返回其有权限的来源 ID、显示信息和可用性，不返回连接秘密

#### Scenario: User references another user's source
- **WHEN** 用户用无权访问的 `streamSourceId` 创建或查询会话
- **THEN** 系统拒绝请求，且不调用外部服务或泄露来源是否存在

### Requirement: Stream sessions use the versioned business API

系统 SHALL 为 `video-stream-analysis.v1` 提供 `POST /ai/v1/stream-sessions`、`GET /ai/v1/stream-sessions/{id}`、`GET /ai/v1/stream-sessions/{id}/events` 和 `POST /ai/v1/stream-sessions/{id}/stop`。创建请求 SHALL 只接受授权的 `streamSourceId`、能力代码、幂等 key 和有界参数。

#### Scenario: Authorized session starts
- **WHEN** 有权限用户为可用来源创建会话
- **THEN** 系统在调用 provider 前持久化本地 sessionId、归属、来源和配置快照，并返回可查询的会话

#### Scenario: Duplicate start is submitted
- **WHEN** 同一用户以相同幂等 key 和规范化参数重复启动同一来源
- **THEN** 系统返回原本地会话且最多自动派发一次；同 key 不同输入返回冲突

### Requirement: Provider source mapping is mandatory

流能力 SHALL 仅在真实 provider 能把本地 `streamSourceId` 映射为已登记来源且完整契约已确认时启用。系统 SHALL 不用后端视频中继、浏览器提交 RTSP 或任意 URL 作为自动替代方案。

#### Scenario: Provider source ID is unavailable
- **WHEN** 来源缺少已确认的 provider source 映射
- **THEN** 该来源保持停用并显示可解释原因，不发起流会话

#### Scenario: Client submits a raw RTSP value
- **WHEN** 创建请求包含 RTSP 地址、GPU URL 或连接凭据
- **THEN** 系统拒绝请求且不保存或转发这些字段

### Requirement: Stub stream sources remain simulated and isolated

开发环境 SHALL 可通过独立 HTTP stub 提供预先登记的合成 `streamSourceId`、确定性事件和截图，以验证会话、游标、去重、停止与恢复。stub 来源和成果 SHALL 明确标识为模拟，且 SHALL 不能使真实 provider 来源映射或 RTX 5070/4090 验收变为完成。正式配置 SHALL 不启动或回退到 stub。

#### Scenario: Synthetic stub source starts a session
- **WHEN** 开发环境显式选择一个已登记的 stub `streamSourceId`
- **THEN** 系统通过正常 provider HTTP 边界创建和查询会话，并将来源、事件、截图与验收证据标记为模拟

#### Scenario: Real provider mapping is unavailable
- **WHEN** 正式环境没有同事确认的 provider source 映射
- **THEN** 真实流能力保持 disabled，系统不使用同名 stub 来源、不接收原始 RTSP，也不声称真实会话可用

### Requirement: Session state preserves execution certainty

系统 SHALL 使用有界会话状态和未知操作原因区分待启动、启动中、运行中、停止请求中、已停止、失败与结果未知。启动请求发送后响应丢失且 provider 无查询能力时 SHALL 保持 UNKNOWN，不自动重复启动。

#### Scenario: Start response is lost
- **WHEN** provider 已可能接收启动请求但响应丢失，且没有已确认的会话查询或去重能力
- **THEN** 本地会话进入带原因的 UNKNOWN，不标为 RUNNING 或 FAILED，也不透明重发启动请求

#### Scenario: Provider confirms running session
- **WHEN** provider 返回可关联的外部会话身份和运行确认
- **THEN** 系统保存该身份与确认时间后进入 RUNNING

### Requirement: Events and snapshots are ordered, deduplicated, and authorized

事件查询 SHALL 使用稳定游标返回有界批次；每个事件 SHALL 有会话内去重身份、发生时间或相对流会话起点的偏移、事件类型和可选授权截图资产。重复事件 SHALL 只保存一次，截图 SHALL 继承会话归属且不能跨会话引用。

#### Scenario: Provider repeats an event page
- **WHEN** 主动查询因重试再次收到相同 providerEventId
- **THEN** 系统不创建重复事件或截图关联，下一游标保持单调推进

#### Scenario: User downloads a snapshot from another session
- **WHEN** 用户请求不属于其可见会话的截图资产
- **THEN** 系统按私有资产规则拒绝且不返回文件内容

#### Scenario: No events have occurred
- **WHEN** 运行中会话的当前游标没有事件
- **THEN** 系统返回有效空事件页，不把空结果标为流故障

### Requirement: Stop requires provider confirmation

本地尚未派发的会话可以直接结束。已发送或运行中的会话仅在 provider 明确支持停止且确认同一外部会话已停止后 SHALL 进入 STOPPED。关闭页面、终止轮询、停止不支持或停止响应未知 SHALL 不被记录为远端已停止。

#### Scenario: Provider confirms stop
- **WHEN** 有权限用户请求停止运行中会话且 provider 确认停止
- **THEN** 系统保存确认并进入 STOPPED，后续迟到事件不能改变该终态

#### Scenario: Stop is unsupported
- **WHEN** provider 契约未声明停止能力
- **THEN** 系统显示无法停止外部处理并保留实际会话状态，不发送猜测的停止请求

#### Scenario: Stop response is lost
- **WHEN** 停止请求可能已发送但确认响应丢失
- **THEN** 系统保存未知停止原因并通过已确认查询能力对账；无法对账时不得标为 STOPPED

### Requirement: Recovery reconciles without duplicate starts

后端重启 SHALL 恢复未派发会话。已派发会话仅在 provider 具备已确认的会话查询能力且外部身份已保存时自动对账；否则 SHALL 保持 UNKNOWN。事件恢复 SHALL 从已提交游标继续，不重建已有截图或重新启动流。

#### Scenario: Restart before provider dispatch
- **WHEN** 后端在会话持久化后、外部启动前重启
- **THEN** 恢复器可派发同一本地会话且只产生一次外部启动

#### Scenario: Restart while running with lookup support
- **WHEN** RUNNING 会话具有外部身份且 provider 支持查询
- **THEN** 后端对账原会话并从持久游标继续取事件，不创建新外部会话

#### Scenario: Restart without lookup support
- **WHEN** 会话已派发但 provider 不支持查询
- **THEN** 系统保留 UNKNOWN 和已有事件/截图，不猜测运行或停止状态

### Requirement: Late events cannot overwrite terminal sessions

系统 SHALL 记录终态版本并拒绝迟到事件、重复轮询或旧页面代次覆盖 STOPPED、FAILED 或 UNKNOWN 的已确认状态。已持久事件和截图在保留期内 SHALL 可在 provider 离线时读取。

#### Scenario: Event arrives after confirmed stop
- **WHEN** STOPPED 会话收到发生时间更早或 provider 延迟返回的事件
- **THEN** 系统可按审计策略记录为迟到且不改变终态，不向用户重复发布事件

#### Scenario: Provider is offline after events are stored
- **WHEN** provider 在事件和截图回存后不可达
- **THEN** 有权限用户仍可查看会话历史、事件时间线和本地截图

### Requirement: Legacy RTSP entries migrate conservatively

旧 RTSP 来源和管理记录 SHALL 保留。只有具备权限映射、provider source ID 和真实能力证据的来源才能迁入新会话入口；未映射来源与旧执行动作 SHALL 保持停用。

#### Scenario: Legacy source maps successfully
- **WHEN** 管理员为旧来源建立授权和已确认 provider source 映射
- **THEN** 新页面使用本地 `streamSourceId` 启动统一会话，旧 RTSP 秘密不进入浏览器

#### Scenario: Legacy source has no mapping
- **WHEN** 旧来源无法映射到 provider 登记来源
- **THEN** 系统保留其管理/历史记录但不提供实时执行动作
