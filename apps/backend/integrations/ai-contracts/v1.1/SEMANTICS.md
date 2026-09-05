# Business API 1.1.0 与公共 Java 边界

v1.1/business.openapi.json 是本轮冻结契约。v1/business.openapi.json 保持不可变，原
image-detection.v1 请求和响应 JSON 在 1.1 中继续有效；读取缺少 jobType 或 resultType
的历史记录时按 IMAGE_DETECTION 解释。

真实供应商协议尚未确认。图片草案仍为 provider-draft/v0.1.openapi.json，上传视频和实时流
扩展草案为 provider-draft/v0.2.openapi.json；二者的 .invalid 地址、字段、方法、TLS、鉴权、
限额与可选能力均不能用于启用 remote。

## 图片与上传视频任务

- 图片继续使用 POST /ai/v1/infer、POST/GET /ai/v1/jobs 和 GET /ai/v1/jobs/{id}；
  上传视频使用 POST /ai/v1/video-jobs，复用相同本地身份、幂等空间、历史、权限、
  成果下载及 POST /ai/v1/jobs/{id}/cancel。
- 新记录必须保存 JobType。图片只使用 parameters/result；视频只使用
  videoParameters/videoResult。旧图片 JSON 不强制出现新判别字段。
- 视频首期仅在真实服务确认后开放 MP4/H.264。稳定参数为 threshold、采样间隔、最大事件数、
  是否取截图和是否请求标注视频；所有范围以 OpenAPI 为上界，实际配置只能更严格。
- 视频成功的最低成果为按 offsetMillis 单调排序的事件时间线和归属本任务的本地截图。
  annotatedVideo 可缺省；不得伪造。空事件和空截图是有效空成果。
- 输入、任务和截图/视频成果均使用本地不透明 ID。供应商引用只留在内部检查点，完整回存并
  核验后才映射为业务资产。

视频请求摘要按下列 UTF-8/LF 顺序规范化；threshold 使用
BigDecimal.stripTrailingZeros().toPlainString()，零固定为 0：

    wgai-video-file-v1
    video-file-analysis.v1
    <inputAssetId>
    <threshold>
    <sampleIntervalMillis>
    <maxEvents>
    <includeSnapshots 小写 true/false>
    <annotate 小写 true/false>
    <retryOfRequestId 或空串>

## 实时流来源与会话

业务端点固定为：

    GET  /ai/v1/stream-sources
    POST /ai/v1/stream-sessions
    GET  /ai/v1/stream-sessions/{id}
    GET  /ai/v1/stream-sessions/{id}/events
    POST /ai/v1/stream-sessions/{id}/stop

- 浏览器请求只接受本地 streamSourceId、能力代码、Idempotency-Key 和有界参数。
  additionalProperties=false 拒绝 RTSP、GPU URL、provider source ID 和凭据。
- StreamSource 的 providerSourceRef 仅供后端配置映射；StreamSourceDto 不含该字段。
  未确认映射、TLS/CA、鉴权或停止能力时来源必须 disabled。
- 会话在 provider 调用前持久化。唯一幂等范围是 (ownerId,idempotencyKey)；同 key 同摘要
  返回原会话，同 key 不同摘要冲突。本地去重不代表供应商支持重试。
- 状态限定为 PENDING、STARTING、RUNNING、STOP_REQUESTED、STOPPED、FAILED、UNKNOWN。
  providerSessionId 和事件 provider 游标只存后端，不进入浏览器 DTO。
- 事件以 (sessionId,providerEventId) 去重；截图完整回存、归属提交和事件写入成功后才推进
  游标。有效空事件页不算失败。迟到事件不得覆盖 STOPPED、FAILED 或 UNKNOWN。

流创建摘要按下列 UTF-8/LF 顺序规范化：

    wgai-video-stream-v1
    video-stream-analysis.v1
    <streamSourceId>
    <maxEventsPerPoll>
    <pollIntervalMillis>
    <includeSnapshots 小写 true/false>

## 取消、停止、恢复与 UNKNOWN

- PENDING 文件任务或尚未派发的流会话可在本地竞争式取消/结束，成功者阻止派发。
- 已派发文件任务只有在供应商明确支持且确认同一远程请求已取消后才进入 CANCELLED。
- 已启动流只有在供应商确认同一 providerSessionId 已停止后才进入 STOPPED。
- 推理/启动响应丢失、查询不可用、未知状态、取消或停止确认丢失使用
  UnknownOperationReason；不透明重发 POST、关闭页面或停止轮询都不能伪造终态。
- PENDING 可恢复；已派发且不可查询保持 UNKNOWN；FETCHING_RESULT 只重取同一成果，
  不重新推理。流事件恢复从已提交游标继续，不重复启动会话。

## 公共类型与归属

02 维护 domain、port 与 api/dto。图片旧构造器保持源码兼容；视频和流使用独立有界类型及
VideoAnalysisProvider、StreamSessionProvider、StreamSourceRepository、
StreamSessionRepository、StreamEventRepository。这些类型不依赖 HTTP、数据库或 Spring，
也不使用 Map<String,Object> 承载供应商数据。

03 只实现 provider/client，04a 只实现任务、来源、会话、事件、迁移和文件持久化，04b 只实现
页面/API 调用/轮询。公开签名变化必须回到 02/00；声明和模拟样例不能证明真实 RTX 5070 能力。
