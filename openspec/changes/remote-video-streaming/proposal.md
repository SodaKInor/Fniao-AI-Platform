## Why

实时流业务已经具备本地来源、会话、事件、截图和停止边界，但同事尚未提供可调用服务，不能验证真实来源映射、RTSP 接入或停止能力。需要让流模块先通过独立 HTTP stub 完成本地闭环和故障验证，同时保持模拟证据与未来真实 GPU 证据严格分离，并纳入最终按功能组织的仓库结构。

## What Changes

- 保持 `video-stream-analysis.v1` 的来源列表、会话创建/查询、事件查询和停止业务接口。
- 浏览器仍只提交不透明 `streamSourceId`；GPU URL、RTSP 地址和凭据不进入浏览器、普通响应或日志。
- stub 只提供登记好的合成来源、确定性事件与截图以及受控启动/查询/停止故障，用于验证真实 HTTP 适配、游标、去重、恢复和终态保护。
- stub 来源、事件、结果与验收记录必须标为模拟；它不能证明真实 provider 能映射来源、处理 RTSP、满足容量或确认停止。
- 真实 RTX 5070 联调移到外部服务到位后的门禁；资料不全时真实流能力保持 disabled，不改成后端视频中继或明文 RTSP。
- 流业务代码归入最终后端和前端的 `stream` 功能模块；契约、fixtures、stub 和验收证据归入 `remote-inference`；V002 归入 `database/migrations/stream`，但已交付内容和校验值不改写。
- 旧 RTSP 页面仅在来源具备授权和真实 provider source 映射后启用；其余执行入口保持停用，管理记录、事件、截图和历史数据保留。

## Capabilities

### New Capabilities

- `video-stream-analysis`：定义授权来源、实时会话、事件/截图查询、停止、恢复、开发 stub 证据边界及旧 RTSP 迁移行为。

### Modified Capabilities

无；共享任务、资产、模块结构和真实/模拟可用性规则由同时更新的 `remote-inference-platform` 变更约束。

## Impact

- `apps/backend`：流来源、会话、事件、provider 端口、持久化及恢复/停止逻辑归入 `stream` 功能模块。
- `apps/frontend`：来源选择、会话状态、事件时间线、截图和轮询归入 `stream` 功能模块。
- `database/migrations/stream`：接收 V002 流表迁移并保留历史兼容与重复执行验证。
- `remote-inference`：保存流契约、合成来源、事件/截图 fixtures、独立 stub 和模拟/真实验收证据。
- 外部 GPU 服务仍由同事维护；真实接口和正式服务不可用时，本变更保持开放。

## Non-goals

不传输或代理直播视频，不实现标注直播、WebRTC/HLS 转码、后端 RTSP 中继、provider 回调或真实流算法；不允许 stub 成为正式降级服务，也不把 stub 的停止或查询行为当作同事服务承诺。
