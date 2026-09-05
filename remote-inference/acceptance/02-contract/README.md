# 02-contract 1.1 验收和职责审阅

本轮范围是 2.6 以及 2.7 的 02 交付部分。工作树从第四轮统一放行并完成规划的
`d31489415b21032f3d08b9a0ef94a85813a8ec05` 快进后开始；最终交付提交由外层
`HANDOFF.md` 记录，并须由 00 独立复核后才成为新共同起点。

| 任务 | 证据 |
|---|---|
| 2.6 | `v1.1/business.openapi.json`、`provider-draft/v0.2.openapi.json` 与新增 Java 类型/端口：冻结图片兼容、上传视频、流来源/会话/事件/停止、结果类型及未知原因；供应商草案继续标为 UNCONFIRMED |
| 2.7（02 部分） | `SEMANTICS.md`、开发/生产交接模板、34 个 JSON 正反样例和 2 个 PNG；所有流来源默认 disabled，只有 00 验收后才记录新共同起点 |

## 验证结果

- `validate_contracts.py`：4 份 OpenAPI 3.0.3 文档、34 个 JSON 正反例和 2 个 PNG
  字节夹具通过；旧图片有效样例再次针对 1.1 schema 验证，拒绝浏览器提交 RTSP、GPU URL、
  provider URL 或凭据。
- `check_types.py`：83 个公开 Java 类型通过 AST、DTO 字段/类型、枚举、分层、单文件和
  `Map` 禁用检查；最大文件 164 行、最大方法 26 行，无 400/80 专项审查触发。
- `compile_java8.sh`：在禁网的 Temurin 8 容器中，以只读源码挂载编译 62 个 domain/port
  class 和 21 个 DTO class；均为 class major 52，`jdeps` 无未解析引用。
- `openspec validate remote-inference-platform --strict` 与
  `openspec validate remote-video-streaming --strict` 均通过。
- `graphify update .` 已在 02 工作树完成；保留六个既有 Vue 语法解析警告，不影响本包 Java
  契约节点。

详细机器输出分别保存在 `contract-checks.json`、`type-checks.json`、`java8-checks.json`、
`scope-check.json` 和 `tool-checks.json`。校验虚拟环境位于外层 `drafts/validation-venv`；
Python 的 LibreSSL/urllib3 提示不涉及网络，本契约也没有远程 schema 引用。

## 兼容与故障语义

- `v1/business.openapi.json` 保持不可变；图片接口与旧 JSON 继续兼容。旧图片记录缺少
  `jobType`/`resultType` 时按图片解释，新记录必须显式保存类型。
- 上传视频只声明 MP4/H.264 上界；最低成果为按偏移排序的事件及已回存本地截图，标注视频
  可选。真实服务未确认前能力关闭。
- 浏览器只提交本地 `streamSourceId`，不接受 RTSP、GPU 地址、provider source ID 或凭据；
  未确认来源映射、TLS/CA、鉴权、事件查询和停止能力时来源必须 disabled。
- 响应丢失、不可查询、取消或停止未获供应商确认均进入有原因的 UNKNOWN；不得透明重发
  POST，也不得把关闭页面或停止轮询当作成功终态。
- PENDING 可恢复；已派发且不可查询保持 UNKNOWN；FETCHING_RESULT 只重取成果。事件按
  `(sessionId, providerEventId)` 去重，完整回存截图和事件后才推进游标，迟到事件不得覆盖终态。

## 归属和未完成项

本包只改 `integrations/ai-contracts`、`AI_ROOT/domain`、`AI_ROOT/port` 和
`AI_ROOT/api/dto`。未改依赖、数据库、Controller、客户端、页面、总 OpenSpec 任务表、
`backend-master` 或原 WGAI 工作树；没有真实视频二进制或 GPU 响应被当作证据。

这些声明只冻结调用边界，不能证明供应商能力、数据库竞争、流清理、权限或端到端运行。
05 的方法、TLS/CA、鉴权、样例、限额、查询和停止确认仍是后续硬门禁；00 验收前 03/04a/04b
不得以本包为新共同起点。
