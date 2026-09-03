# 03-client 后端交接

状态：READY_FOR_INTEGRATION（仅本包后端；整体 3.4 等待 04b）。

## 基线和提交

- 代码根：`/Users/twowt88/Documents/ChatGPT/WGAI-parallel/03-client/code`
- 分支：`work/remote-inference/03-client`
- 00 共同冻结起点：`ab9809d23919ea5d61dfc7d8b34d7f30bb9d607c`
- 公共契约提交：`5a55ca5cc6ea8fde09898f44519d62c715af12db`
- 实现与验收证据提交：`c2bfd195050dd3aa4ed9c86e63df1defd5fd2120`
- 本文档随后独立提交；完整交付取本分支包含本文档的提交。最终 SHA 同步记录于外层 `03-client/HANDOFF.md`，避免在提交中自引用 SHA。

## 完成范围

| 任务 | 本包结果 |
|---|---|
| 3.1 | 冻结 InferenceProvider/ProviderArtifactReader 的实现；默认 disabled、显式 mock、未确认 remote 拒绝；旧执行与 native 启动守卫 |
| 3.2 | 独立凭据、批准地址、可信 CA、预算与并发实际消费者；开发/正式模板和独立只读挂载覆盖文件 |
| 3.3 | 草案流式 multipart、严格协议转换、错误分类与 ExecutionCertainty；无自动重试/鉴权重发/重定向/算法回退；成果读取边界 |
| 3.4 | 后端完成：AI Shiro 规则优先于匿名/静态规则，新上传/推理 ai:infer，旧执行入口永久拒绝。前端等待 04b，整体复选框保持未完成 |
| 3.5 | GET /jeecg-boot/ai/v1/capabilities，冻结 Result<List<CapabilityDto>>；仓储、权限、配置和被动观测组成可用性，外部状态不影响核心健康 |

草案协议只通过夹具构造，不自动装配成业务 remote。没有可将草案升级为正式协议的开关。没有新公共 DTO、领域类型、端口或构建依赖，也没有修改 04a/04b、迁移、总任务表。

## 00 / 04a 集成事项

1. 先核实公共契约提交，再按顺序合入实现提交和本文档提交。与 04a 的仓储、流程实现及 04b 界面组合后，由 00 更新总表。
2. `ProviderConfiguration` 已注册唯一 InferenceProvider、ProviderArtifactReader 和 CapabilityQueryService；04a 注入这些冻结端口。04a 提供真实 CapabilityRepository，03 没有运行时假仓储。仓储缺失时核心应用仍可启动，能力查询明确返回 500 / INTERNAL_ERROR / 能力仓储尚未就绪。
3. 00/04a 登记并按角色授予独立权限 `ai:infer`，涵盖 POST /ai/v1/assets、/infer、/jobs。历史、详情、下载和取消继续由登录及 04a 的资源归属校验保护；不得把 infer 权限作为历史归属的替代。
4. 冻结的去重规则要求先按用户和 key 查找已存在提交，再处理新提交的能力校验。04a 在该查找后接入 CapabilityQueryService 所计算的可用性，拒绝已停用、未就绪或错误绑定的新任务。HTTP 过滤器不能按全局模式提前阻止返回已有任务；ModeInferenceProvider 在实际派发前还有最终守卫。
5. 模拟能力绑定必须为 capabilityCode/providerCapabilityCode=`image-detection.v1`、capabilityVersion=`mock-v1`、providerKey=`mock`、adapterId=`mock-v1`，query/cancel/deduplication 均 false，simulated=true。初始化数据/迁移归 04a；03 不另建实体或种子仓储。
6. 模拟器只读取 PNG/JPEG 尺寸并生成合成检测，文件成果是固定 16×16 合成预览，不能当成原图标注。threshold > 0.95 为有效空成果，annotate=false 无文件成果；引用一小时有效。停用后仍能按已保存绑定收集模拟成果。落库与归属下载由 04a 验收。
7. 无需 POM、锁文件或公共配置补丁。若真实协议需改动，先由 02/00 确认公共契约，再明确分配 03 适配；当前没有远端查询、取消、去重或健康/模型就绪保证。

## 配置消费者与默认值

部署说明见 [remote-ai/README.md](../../../../deploy/remote-ai/README.md)。`application-remote-ai.yml` 只有环境变量映射，无秘密值；因根忽略规则覆盖 application*.yml，已精确加入版本管理，未修改共同忽略规则。core.override.yml 加载该 profile；凭据、私有 CA 覆盖文件分别只读挂载。没有新增 GPU 服务、共享端口或数据卷。

| 配置 | 实际消费者 |
|---|---|
| mode | ModeInferenceProvider、ProviderAvailability |
| provider-key、base-url、approved-origin、api-path | DraftTransportFactory、DraftEndpoint、ProviderAvailability |
| token-file、ca-file | ProviderCredentials、ProviderTrust；配置检查与草案传输共用 |
| connect/request/transfer-timeout-ms | HTTP 连接、总调用、流写入 deadline 和成果下载总预算 |
| max-inflight | mock 与草案 transport 的信号量；任务排队仍归 04a |
| upload/output-max-bytes | 流限制、mock 和能力描述上限 |

默认 disabled；连接 3 秒、总调用 120 秒、传输 30 秒，并发 1，输入/单成果各 10 MiB。正式模板默认停用。配置缺失只影响 AI 能力，外部观测 60 秒过期后报告未确认，没有主动健康探测或虚构模型就绪。

## 验收证据

- **真实 Java 8：29 项 JUnit 测试通过，class major 52。** 测试容器禁外网，源码只读挂载，包含真实 HTTP/HTTPS 夹具；通过记录实际请求次数和资源关闭证明预算、并发、错误转换与不重发行为。
- 401/403 外部鉴权转换为 PROVIDER_AUTH，发送前连接失败 NOT_STARTED，发送后丢失、超时或协议失败 UNKNOWN；业务身份保持有效。覆盖正常/空成果、版本/关联 ID、重复字段、非法坐标、408/503/断线/重定向、意外 202，以及成果来源、证书、有效期、超限、截断、关闭清理。
- 真实 Shiro/JwtFilter 和配置链配合测试 Realm 验证匿名、无权限、授权后旧入口停用及保留查询；10 个 Controller 入口、19 个服务入口直接调用均先拒绝。CapabilityRepository 和身份后端替身只用于测试，能力路径和冻结响应通过真实 MockMvc 校验。
- 完整后端 Docker 构建通过，最终镜像 `wgai-03-client-backend:delivery`；构建与运行镜像标识一致。Maven 既有打包跳过测试，因此单独保留非零 JUnit 证据，没有把打包成功当作测试执行。
- 隔离项目 `wgai-ri-03-client` 验证 remote 配置缺失时核心完整启动和匿名拒绝；再以 disabled、旧 OpenCV 开关 true、无效 native 路径验证加载守卫。03 后端/数据库/缓存已停止，独立卷保留；原服务在最终检查时均健康。
- 冻结契约检查通过（2 OpenAPI、15 JSON 正反例、2 PNG）；公共类型与契约提交无差异。59 个实现/证据路径属于 03，随后仅补本交接及范围回执。新增 Java 最长 141 行、方法最长 30 行；Controller 方法亦符合 30 行目标。
- OpenSpec strict 通过；在本包 code 根执行 `graphify update .` 成功。6 个既有 Vue 解析警告已记录，未修改前端。共享 Serena 仍指向原工作区，因此没有切换其项目或用于写入。

完整清单、源码与日志哈希、可复现脚本见同目录 [README.md](README.md)、[java8-tests.json](java8-tests.json)、[scope-and-architecture.json](scope-and-architecture.json)、[final-checks.json](final-checks.json)。原始日志和测试证书仅留本包 drafts，不提交运行秘密。

## 文件归属与限制

生产代码新增于 AI_ROOT/client、config/provider、application/capabilities、api/controller/CapabilityController、api/mapper/capabilities、legacy；既有文件仅改 Shiro AI 链、历史/订阅/视频/测试执行入口和 native 启动开关。完整路径和 AST 规模在范围回执中。

旧历史服务 907→924 行、订阅 Controller 317→318 行等已有规模问题只增加薄守卫；算法搬迁/删除交 07 接管。外层工作包说明只更新本包，不改他包 HANDOFF。

隔离运行的 Swagger 文档返回空路径表，未用它证明路由注册；能力路由和 JSON 的证据来自 MockMvc，00 可结合原运行配置复核文档清单。真实登录组合、资产与任务落库、跨用户资源归属、成果回存、完整页面闭环和 4.7 仍由 00 组合验收。

未验收真实 GPU 或正式环境；3.4 前端仍等待 04b。没有推送远程、合并他包或归档整体变更。

## 回退

优先把 WGAI_INFERENCE_MODE 切为 disabled 并保留全部旧执行守卫。已有历史由本地资产读取，已产生模拟成果可按绑定继续收集。代码回滚只选择契约兼容且仍保留守卫的版本，不恢复旧 Java 模型或自动回退。
