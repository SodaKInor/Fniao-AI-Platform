# 02-contract 验收和职责审阅

范围仅 2.1—2.5。前置是 00 已验收 01 并提交 823eaa02050eda26c283a2e1588225d3ee022e8c；本包从干净工作树快进到该提交后才开始。最终交付提交由外层 HANDOFF.md 记录。

| 任务 | 证据 |
|---|---|
| 2.1 | v1/business.openapi.json 与 provider-draft/v0.1.openapi.json：方法、鉴权、身份、输入、成果、错误和限制；外部全标 UNCONFIRMED，示例为 .invalid 地址 |
| 2.2 | examples/manifest.json 和 15 个 JSON 正反样例、2 个 PNG 字节夹具；字段、媒体、文件哈希、有效空成果和错误均检查；全部模拟 |
| 2.3 | v1/SEMANTICS.md：Result/现有登录、同一任务的 200/202、UNKNOWN 禁止自动再次推理，provider 仅同步；可选查询/取消/去重关闭 |
| 2.4 | handoff/development.md 与 production.md 分别记录缺项、确认状态与最迟批次；不以开发记录代替正式确认 |
| 2.5 | AI_ROOT 的 43 个公开类型、type-checks.json、java8-checks.json；每公开类型单文件，实际 Java 8 编译和依赖检查，无客户端/仓储/Controller/前端实现 |

## 验证方法

从本包 code/ 运行。工具虚拟环境位于外层 drafts/validation-venv，依赖为 openapi-spec-validator 0.7.2、jsonschema 4.23.0、javalang 0.13.0；不修改业务依赖或锁文件。Python 自带 LibreSSL 导致 urllib3 兼容提示，本次校验无远程 schema 引用、不发网络请求，校验器退出 0。

- `python <本目录>/scripts/validate_contracts.py`：OpenAPI 3.0.3 全文及 JSON Pointer，15 个有效/无效样例；额外校验本地身份、状态/成果关系、规范化坐标和参数映射，以及文件 PNG/CRC/解压尺寸/长度/哈希。结果 contract-checks.json。
- `python <本目录>/scripts/check_types.py`：真实 Java AST，顶层公开声明、import、DTO 字段/类型和状态/错误枚举一致性、物理文件及方法跨度。结果 type-checks.json。
- `scripts/compile_java8.sh`：在本机已有 maven:3.8.8-eclipse-temurin-8 容器运行，网络关闭、当前 code 只读挂载 /workspace，独立 drafts/java-validation 挂载 /validation。领域和端口单独用 JDK 标准库编译，DTO 仅加从 01 已构建制品提取的既有 Jackson annotations。全部 class major 52；jdeps 核查实际字节码引用，记录 java8-checks.json。
- `graphify update .`：当前工作树更新；结果及已知原 Vue 解析限制见 tool-checks.json。共享 Serena 初始化失败，未切换原项目或跨工作树写入。
- `openspec validate remote-inference-platform --strict`：只校验总规格，02 不勾选代码库总任务表。

## 关键类型和边界的人工审阅

结合声明、字段引用、字节码依赖与以下业务流程审阅，而非仅搜索 Spring/HTTP 关键字：

| 区域 | 具体审阅与结论 |
|---|---|
| ProviderRequest / ContentSource / InferenceProvider | 只有已取得派发权的应用能够发起调用；标准类型传递输入与绑定快照；provider 最多打开一次输入、负责关闭，不能接触三仓储或执行透明重试 |
| ProviderResult / ProviderArtifact / ProviderArtifactReader | 外部相对引用留在内部检查点，不出现在 API；reader 绑定配置、限制来源/字节，返回流由应用关闭，失败清理连接；仅回存后变成本地资产 |
| ArtifactStore / StoredArtifact / AssetRepository | 存储借用流并校验字节，返回完整文件哈希；资产仓储只处理归属元数据；应用协调两者，存储不写任务终态 |
| JobRequest / JobRepository / JobUpdate | 唯一用户/key 原子去重；只读查 key 可返回停用前的既有任务；原子 claim 与 cancel 竞争，同 token/version 更新；完整检查点先于文件收集；迟到响应和 UNKNOWN 不回到 PENDING |
| Capability / CapabilitySnapshot / CapabilityRepository | 当前启停和可用性由本地绑定提供，任务保存不含 URL/凭据的快照；供应商停用不删除已有资产 |
| API DTO | 与 domain 记录分离，只复用规范化 JobState/ErrorCode；具体参数/检测结构，不含 Map；已有 Jackson 注解控制空值和日期，未新增 Result/登录/依赖 |

责任规则是后续实现的验收约束，声明不能证明数据库竞争、流清理或权限已实现。公开类型没有服务编排或运行时持久化，真实并发、取消竞争、网络故障和业务端到端验证分别留给对应包。最大文件/方法物理行数以 type-checks.json 为准，均未达到 400/80 专项审查阈值。

草案不计为真实接口确认；第 5 批开发接口、第 8 批正式接口及整体 4.7 演示均保持未完成。不得推送、部署、归档整体变更或修改原业务入口。
