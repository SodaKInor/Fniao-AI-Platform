# 03-client 第五轮验收说明

范围：在已验收的 3.1—3.5 后端基础上交付 `remote-inference-platform` 3.6、3.7，并覆盖伴随变更 `remote-video-streaming` 2.1—2.3。代码根为本包独立工作树，分支 `work/remote-inference/03-client`。

## 冻结基线

- 00 新共同起点：`e44041ec050974ee3f36655f6869fb96cf16faad`
- 1.1.0 公共契约提交：`1177de8be45123d043d7cb26b845ee9d94c26784`
- 本包没有修改公共 domain、port、DTO、OpenAPI、provider draft 或 JSON/PNG 样例。
- 05 尚未提供已确认的 RTX 5070 方法、TLS/CA、鉴权、视频/流样例、限额、事件查询和停止能力，因此这里只实现可由真实 HTTP 夹具调用的严格草案适配器；生产运行时门禁保持硬关闭，不存在配置升级开关。

## 本轮交付

- 图片适配器保留兼容；图片、视频与流 provider 均支持可选远程委托，但运行配置未注册未确认草案。
- 上传视频使用一次性 multipart POST；严格校验绑定、媒体/大小、request/job 关联、时间偏移事件、截图和可选标注视频。
- 实时流实现来源会话创建、会话查询、事件分页和停止草案调用；请求只接收仓储映射后的 provider source ref，浏览器仍只能提交本地 `streamSourceId`。
- JSON 解码拒绝未知/重复/缺失字段、尾随内容、非法状态、乱序或重复事件和不匹配的 request/session ID。
- OkHttp 禁止连接重试和 HTTP/HTTPS 重定向，所有 POST body 标记 one-shot。请求发出后断线/超时为 UNKNOWN，不透明重放。
- 停止只有收到严格 `CONFIRMED_STOPPED` 才确认；失败、丢响应、未知状态不得进入停止终态。
- 批准地址要求与配置 origin 完全相等；生产只允许 HTTPS，凭据来自独立只读文件，CA 使用 JVM 信任或专用文件。错误正文、凭据、RTSP 和任意 GPU URL 不进入安全错误消息。
- 新增视频输入/输出上限，默认各 512 MiB；真实格式和供应商限额未确认前能力仍 disabled。

## 可复现证据

| 文件 | 内容 |
|---|---|
| `java8-tests.json` | 42 项 JUnit 4 测试、源码哈希、Java class major 52、隔离日志哈希 |
| `contract-checks.json` | 4 份 OpenAPI、34 个正反 JSON、2 个 PNG 和跨字段检查 |
| `scope-and-architecture.json` | 相对新共同起点的精确允许路径、冻结面无差异、Java AST/规模检查 |
| `build.json` | 原 Dockerfile 完整构建、镜像摘要、日志哈希和 Spring Boot 包结构检查 |
| `final-checks.json` | 两个 OpenSpec strict、Graphify、秘密扫描与最终差异检查 |

从当前 code 根运行：

```text
python3 backend-github/integrations/ai-contracts/acceptance/03-client/scripts/test_package.py --dependency-image wgai-integration-backend:round5-contract-1177de8
/Users/twowt88/Documents/ChatGPT/WGAI-parallel/02-contract/drafts/validation-venv/bin/python backend-github/integrations/ai-contracts/acceptance/03-client/scripts/validate.py
docker build -f deploy/backend/Dockerfile -t wgai-03-client-backend:round5 .
openspec validate remote-inference-platform --strict
openspec validate remote-video-streaming --strict
graphify update .
```

## 测试覆盖

- 图片既有正常、有效空成果、HTTP/TLS/CA、鉴权、超时、并发、大小、成果截断和业务身份隔离回归。
- 视频 200、事件时间线、截图/标注视频、有效空事件、绑定错误、未知字段、关联 ID 错误、输入上限、401 脱敏、发出后断线 UNKNOWN 和请求次数为 1。
- 流创建/查询/空事件页/有序事件/停止确认、来源和会话 ID 校验、cursor 编码、未知状态、未知字段、启动响应丢失 UNKNOWN 和停止响应丢失 UNKNOWN。
- mode Bean 证明真实资料缺失时视频/流均硬关闭；图片旧构造兼容，草案类只有显式测试构造路径。

## 明确未完成

没有宣称真实 GPU、真实视频格式、source ID 映射、远端事件查询或停止通过；这些继续阻断 5.1 和后续发布。任务/流持久化、V002、恢复/取消、成果回存和 API 编排归 04a；上传视频和实时事件页面归 04b；真实联调归 05，韧性归 06。

本包未推送、未部署、未修改 `backend-master`、GPU 源码、权重、历史表/数据或前端，也未归档 OpenSpec。
