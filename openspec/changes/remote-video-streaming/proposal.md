## Why

现有 RTSP 相关页面和来源记录没有可验收的远程会话、事件、截图及停止语义，也不能把地址或凭据安全交给浏览器。需要以服务端登记来源和可恢复会话为边界，在不建设视频中继或标注直播的前提下提供实时事件分析。

## What Changes

- 新增 `video-stream-analysis.v1`，由业务后端提供来源列表、会话创建/查询、事件查询和停止接口。
- 浏览器只提交不透明的本地 `streamSourceId`；GPU URL、RTSP 地址和凭据仅在后端批准配置与 provider 映射中使用。
- 默认要求 GPU 服务支持已登记来源 ID 和主动事件查询；若真实接口不能映射，能力保持 disabled 并修订契约，不回退成后端中继或明文 RTSP。
- 实时成果限定为带时间的事件时间线与授权截图，不建设标注直播通道。
- 会话、事件和截图遵守用户/来源权限、事件去重、游标分页、重启对账、迟到事件和终态保护。
- 停止仅在 provider 明确支持并返回确认后进入 STOPPED；不支持或响应未知必须如实显示，不能把关闭页面当成远端已停止。
- 以 V002 增量迁移新增 `ai_stream_source`、`ai_stream_session`、`ai_stream_event`，不删除或重写旧表和历史资产。
- 旧 RTSP 页面仅在来源能映射到授权的 `streamSourceId` 时迁移；其余入口保持停用，管理 CRUD 和历史数据保留。

## Capabilities

### New Capabilities

- `video-stream-analysis`：定义授权来源、实时会话、事件/截图查询、停止、恢复及旧 RTSP 迁移的业务行为。

### Modified Capabilities

无；共享任务、资产和能力规则由同时修订的 `remote-inference-platform` 变更约束。

## Impact

- `backend-github`：新增流来源/会话/事件业务 API、provider 端口、持久化、V002 迁移及恢复/停止逻辑。
- `frontend-vue`：新增来源选择、会话状态、事件时间线和截图页面；轮询不传递或显示 RTSP/GPU 秘密。
- 数据库：新增三张流表并复用私有资产归属；历史图片、视频、RTSP 来源和成果不删除。
- 外部 RTX 5070 服务：必须提供可确认的来源映射、会话/事件查询及停止能力；这里只记录契约与证据，不修改同事源码。
- 清理与发布：本变更未通过真实联调和恢复门禁前，07 与发布候选保持阻断。
