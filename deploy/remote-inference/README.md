# 03 客户端配置增量

本目录属于 03 初始配置交付，后续由 05/08 按所有权顺序接管。使用 Java 8 / 原后端镜像，不创建 GPU 服务、固定端口或数据卷。所有相对路径由 Compose 首个配置文件决定，挂载源统一传绝对路径。

## 使用

先按 01 登记的本包隔离配置启动基线环境；每次启动前检查端口、卷及 Compose project name。以本包新代码构建后端镜像，不复用基线镜像作为实现验收。

选择 `dev.env.example`（显式 mock）或 `prod.env.example`（disabled）为外部配置的参考，设置 `WGAI_REMOTE_AI_CONFIG_FILE` 为本目录 `application-remote-ai.yml` 的绝对路径。在原隔离 Compose 配置之后追加 `core.override.yml`，保留原数据库、上传目录和端口隔离。该覆盖文件启用 `remote-ai` 配置 profile；实际生效值必须通过渲染配置核对。

需要覆盖真实 HTTP 适配路径时，使用 `stub.env.example`，并在基线 Compose 与 `core.override.yml` 后显式追加
`stub.override.yml`、启用 `remote-ai-stub` profile。该服务只有合成输入输出，使用公开开发 token，所有响应均
标识 simulated。正式启动不得包含该覆盖文件或 `WGAI_INFERENCE_DEVELOPMENT_STUB=true`。开发数据库只可在
隔离副本中应用 `stub-bindings.example.sql`，替换其中的 `__OWNER_ID__`；该 seed 不属于 V001/V002 迁移。

服务凭据是独立文件，与业务 X-Access-Token 无关。需要凭据时追加 `secrets.override.yml` 并设置 `WGAI_INFERENCE_TOKEN_SOURCE`；私有 CA 另追加 `ca.override.yml` 和 `WGAI_INFERENCE_CA_SOURCE`。文件只读挂载，真实值不入库。正常公网 CA 可保留 CA_FILE 为空并沿用 JVM 信任库。禁止跳过证书或主机名校验。

## 当前可用范围

- 默认 disabled；mock 只用于明确标识的本地模拟。正式模板默认关闭。
- 唯一模拟绑定：capabilityCode/providerCapabilityCode=`image-detection.v1`，capabilityVersion=`mock-v1`，providerKey=`mock`，adapterId=`mock-v1`，query/cancel/deduplication 全 false。由 04a 的 CapabilityRepository 提供本地绑定，不在 03 注册运行时假仓储。
- 模拟器读取图像尺寸，生成合成检测与固定 16×16 合成预览；预览不是原图标注。threshold 大于 0.95 时返回有效空成果；annotate=false 无文件成果。引用有效期为生成后 1 小时。
- remote 在真实接口经 02/00 确认前始终不可用于真实能力。唯一例外是同时满足
  `mode=remote`、`development-stub=true`、`provider-key=stub` 的显式开发组合；它注册草案适配器但只接受
  simulated stub 绑定。缺少任一条件或正式模板均不会注册，也不会自动回退 stub。
- 图片 draft 夹具绑定 adapterId=`sync-draft-v0.1`；视频和实时流夹具分别绑定 `video-draft-v0.2`、`stream-draft-v0.2`。providerKey 必须匹配后端配置；HTTP 只允许测试夹具中的字面量回环地址或显式开发 profile 的精确服务名 `remote-ai-stub`，其余部署配置仍只接受与批准 origin 完全一致的 HTTPS 地址。
- 视频/流草案客户端已经实现真实的一次性 HTTP 请求、严格响应转换和故障确定性，供 05 提供正式样例后逐字段核对。它们没有生产 Bean；`VideoAnalysisProvider` 与 `StreamSessionProvider` 当前只注册硬关闭模式。流请求只接收仓储映射后的 provider source ref，浏览器仍只能提交本地 `streamSourceId`，不得传 RTSP、GPU URL 或凭据。
- 视频与流的 POST 禁止自动重试和重定向；请求发出后断线保持 UNKNOWN。流停止只有收到严格的 `CONFIRMED_STOPPED` 响应才算停止，失败、超时或未知响应均不得写入停止终态。
- 新上传和推理必须有 `ai:infer`。00/04a 负责按角色登记权限，03 不修改迁移或默认给全部用户授权。重复提交须先按冻结的用户/key 返回已有记录，再检查新提交能力；不得在 HTTP 过滤器里按全局模式提前拦截这一去重流程。04a 在去重后接入能力可用性检查，03 派发端还会在外呼前再次守卫。
- 04a 仓储未合入时，能力查询明确报依赖未就绪；核心后端仍可启动。真实上传/任务/历史归属和成果落库由 04a 完成。

## 消费者和预算

| 配置 | 消费者 |
|---|---|
| mode | ModeInferenceProvider、ProviderAvailability |
| provider-key/base-url/approved-origin/api-path | DraftTransportFactory、DraftEndpoint、ProviderAvailability |
| video-api-path、stream-sources-path、stream-sessions-path | DraftEndpoint 的视频提交、来源会话创建、会话/事件查询和停止地址；不允许跨 origin 或自由 URL |
| token-file/ca-file | ProviderCredentials、ProviderTrust；能力配置检查和协议传输共用 |
| connect/request/transfer-timeout-ms | HTTP 连接、总调用/读取、上传 deadline/写入和成果下载总预算 |
| max-inflight | mock 与草案 transport 的信号量；不另建任务队列 |
| upload/output-max-bytes | 请求/成果流限制、mock、能力描述的更严格上限 |
| video-upload/video-output-max-bytes | 视频输入、事件截图和可选标注视频的有界传输；首期默认各 512 MiB，仍受真实服务限额确认门禁 |

初值：3 秒连接，120 秒总调用，30 秒传输，并发 1，输入/单成果各 10 MiB。超过当前模拟范围的配置拒绝启用，实际 GPU 容量留第 5 批确认。前端短等待最多 1500ms，任务队列归 04a；不沿用旧设计中已被冻结契约替代的 30 秒前端等待示例。

外部观测只记录连接事实，60 秒过期后回到未确认；不注册影响核心健康聚合的外部 HealthIndicator，也不调用虚构的健康/就绪接口。外部版本、视频格式/限额、流来源映射、事件查询、停止、取消和远端去重仍未确认；这些缺项都保持能力 disabled。

故障时切换 disabled；旧执行守卫保持有效，已产生的模拟成果可按保存的绑定继续收集。生产历史读取由本地资产提供。回滚仅选择兼容且仍有守卫的版本，不重新开启旧本地模型。

## 05 真实契约资料校验

`contract-intake.example.json` 是故意无法通过的未确认模板，不能复制后直接启用。把真实资料写入
仓库外的私有 JSON，所有凭据只写绝对文件引用，然后执行：

```sh
node deploy/remote-inference/validate-contract-intake.cjs /absolute/private/path/contract-intake.json
```

校验器要求 HTTPS 与完全一致的批准 origin，拒绝占位/回环主机、URL 内嵌凭据、任何 RTSP 字符串
和内联密钥字段。它逐项要求确认版本、CA 信任方式、错误确定性、限额、样例文件、图片/视频可选
查询/取消/去重能力，以及流来源映射、事件查询和停止确认语义；引用文件必须实际存在。输出只包含
字段路径和通用错误，不回显地址、source ID 或凭据内容。

校验通过只表示 5.1 资料结构完整，不代表接口真实可用，也不自动切换 `remote`。仍须人工核对样例
语义、由适配器所有者实现并验收真实 wire，再从 05 后端容器执行 5.2—5.4。明确不支持的能力用
`status: UNSUPPORTED` 和原因登记；这不会伪造为已完成的真实图片、视频或流验收。

## 05 真实联调证据校验

只有私有 intake 校验通过、02/00 已冻结实际 wire、03 所有者已完成正式 remote 装配，才启动 05
隔离环境并从实际业务后端容器执行联调。不得把宿主机请求、端口探测、mock 或 draft fixture 写入
真实验收。完成图片、上传视频和流会话后，将脱敏证据按
`real-integration-evidence.example.json` 的字段整理，并执行：

```sh
node deploy/remote-inference/validate-real-integration-evidence.cjs \
  /absolute/private/path/contract-intake.json \
  /path/to/real-integration-evidence.json
```

校验器把证据绑定到私有 intake 的原始字节 SHA-256，要求记录实际后端容器与镜像摘要、remote
模式、加密通道、服务鉴权、批准 origin，以及图片/视频/流的业务 request/session ID、供应商关联
ID、一次派发、实际版本、耗时、输入大小与哈希。成果必须已经回存，并用资产 ID、大小、哈希、
授权下载、页面和历史读取串联；视频事件必须有时间偏移和截图，流事件必须有时间戳和截图。

证据文件本身也按大小和 SHA-256 核验，提交内容只能是脱敏副本，不得包含 provider URL、RTSP、
凭据或授权值。供应商声明支持停止时，只有实际确认才能记录 `STOPPED`；未支持时只能记录
`UNSUPPORTED`，不能借此通过完整流验收。完整证据还必须明确不存在 UNKNOWN 误标成功、未确认
停止误标终态、重复派发和宿主机代验。

`real-integration-evidence.example.json` 故意不完整，不能作为通过证据。校验器通过仍只是 05 提交
给 00 的候选证据；00 必须复核原始私有资料、真实运行记录和页面结果后才可勾选 5.1—5.4。资料、
正式适配器或任何一项真实流程缺失时，继续保持 remote disabled，且不得释放 06、07 或 RC。
