# 03 客户端配置增量

本目录属于 03 初始配置交付，后续由 05/08 按所有权顺序接管。使用 Java 8 / 原后端镜像，不创建 GPU 服务、固定端口或数据卷。所有相对路径由 Compose 首个配置文件决定，挂载源统一传绝对路径。

## 使用

先按 01 登记的本包隔离配置启动基线环境；每次启动前检查端口、卷及 Compose project name。以本包新代码构建后端镜像，不复用基线镜像作为实现验收。

选择 `dev.env.example`（显式 mock）或 `prod.env.example`（disabled）为外部配置的参考，设置 `WGAI_REMOTE_AI_CONFIG_FILE` 为本目录 `application-remote-ai.yml` 的绝对路径。在原隔离 Compose 配置之后追加 `core.override.yml`，保留原数据库、上传目录和端口隔离。该覆盖文件启用 `remote-ai` 配置 profile；实际生效值必须通过渲染配置核对。

服务凭据是独立文件，与业务 X-Access-Token 无关。需要凭据时追加 `secrets.override.yml` 并设置 `WGAI_INFERENCE_TOKEN_SOURCE`；私有 CA 另追加 `ca.override.yml` 和 `WGAI_INFERENCE_CA_SOURCE`。文件只读挂载，真实值不入库。正常公网 CA 可保留 CA_FILE 为空并沿用 JVM 信任库。禁止跳过证书或主机名校验。

## 当前可用范围

- 默认 disabled；mock 只用于明确标识的本地模拟。正式模板默认关闭。
- 唯一模拟绑定：capabilityCode/providerCapabilityCode=`image-detection.v1`，capabilityVersion=`mock-v1`，providerKey=`mock`，adapterId=`mock-v1`，query/cancel/deduplication 全 false。由 04a 的 CapabilityRepository 提供本地绑定，不在 03 注册运行时假仓储。
- 模拟器读取图像尺寸，生成合成检测与固定 16×16 合成预览；预览不是原图标注。threshold 大于 0.95 时返回有效空成果；annotate=false 无文件成果。引用有效期为生成后 1 小时。
- remote 在真实接口经 02/00 确认前始终不可用；不存在把草案变成正式协议的配置开关。草案适配器仅供独立协议夹具构造，不会自动注册到业务派发器。
- draft 夹具绑定 adapterId=`sync-draft-v0.1`，providerKey 匹配其后端配置；HTTP 只允许显式夹具中的字面量回环地址。部署配置只接受 HTTPS 批准地址。
- 新上传和推理必须有 `ai:infer`。00/04a 负责按角色登记权限，03 不修改迁移或默认给全部用户授权。重复提交须先按冻结的用户/key 返回已有记录，再检查新提交能力；不得在 HTTP 过滤器里按全局模式提前拦截这一去重流程。04a 在去重后接入能力可用性检查，03 派发端还会在外呼前再次守卫。
- 04a 仓储未合入时，能力查询明确报依赖未就绪；核心后端仍可启动。真实上传/任务/历史归属和成果落库由 04a 完成。

## 消费者和预算

| 配置 | 消费者 |
|---|---|
| mode | ModeInferenceProvider、ProviderAvailability |
| provider-key/base-url/approved-origin/api-path | DraftTransportFactory、DraftEndpoint、ProviderAvailability |
| token-file/ca-file | ProviderCredentials、ProviderTrust；能力配置检查和协议传输共用 |
| connect/request/transfer-timeout-ms | HTTP 连接、总调用/读取、上传 deadline/写入和成果下载总预算 |
| max-inflight | mock 与草案 transport 的信号量；不另建任务队列 |
| upload/output-max-bytes | 请求/成果流限制、mock、能力描述的更严格上限 |

初值：3 秒连接，120 秒总调用，30 秒传输，并发 1，输入/单成果各 10 MiB。超过当前模拟范围的配置拒绝启用，实际 GPU 容量留第 5 批确认。前端短等待最多 1500ms，任务队列归 04a；不沿用旧设计中已被冻结契约替代的 30 秒前端等待示例。

外部观测只记录连接事实，60 秒过期后回到未确认；不注册影响核心健康聚合的外部 HealthIndicator，也不调用虚构的健康/就绪接口。外部版本、查询、取消和远端去重仍未确认。

故障时切换 disabled；旧执行守卫保持有效，已产生的模拟成果可按保存的绑定继续收集。生产历史读取由本地资产提供。回滚仅选择兼容且仍有守卫的版本，不重新开启旧本地模型。
