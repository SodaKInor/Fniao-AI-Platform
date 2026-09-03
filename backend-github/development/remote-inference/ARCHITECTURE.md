# 代码架构与约束

状态：约束与目标目录设计，业务实现尚未开始。适用于本次新增/明显扩展的远程接入代码；旧系统按迁移清单逐项收敛。

## 1. 边界与技术选择

沿用 Java 8 / Spring Boot 2.6.6 与 Vue 2 的现有项目结构。新增模块属于现有业务后端，不单独建设微服务、GPU 工程或第二套登录。GPU 算法、驱动和模型运行由同事负责。

单一职责是主要标准：上传、权限、任务状态、HTTP 协议、数据库和展示不能集中在一个文件。也不把每一行代码机械拆成文件；只创建当前任务实际需要的模块，避免没有调用者的空壳类、泛化框架和全局万能工具类。

## 2. 后端目录与职责

以下目录位于 backend-github/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/ai/。类名是职责示例，具体公共签名在 02-contract 中冻结。

| 目录 | 职责及示例 | 不应包含 |
|---|---|---|
| api/controller/ | InferenceController、JobController、AssetController、CapabilityController；校验请求、取得已认证身份、调用应用服务、返回现有 Result 包装 | HTTP 客户端、SQL、文件流转实现、整段任务循环 |
| api/dto/ | 分文件声明提交、状态、成果和能力的请求/响应 DTO；由契约包维护 | ORM 实体、服务密码、供应商原始返回结构 |
| api/mapper/ | API DTO 与领域/应用输入输出之间的转换 | 远程调用、数据库查询 |
| application/ | SubmitInferenceService、DispatchJobService、CollectResultService、JobQueryService；按用例编排权限、端口调用与状态保存 | 直接使用供应商 SDK、MyBatis Mapper、磁盘绝对路径 |
| domain/ | JobState、Capability、InferenceResult、状态转换与约束；纯业务类型 | Spring、MyBatis、HTTP 客户端和存储 SDK 依赖 |
| port/ | InferenceProvider、ProviderArtifactReader、JobRepository、AssetRepository、CapabilityRepository、ArtifactStore 等必要接口 | 第三方协议 JSON、具体数据库/网络实现 |
| client/ | HTTP 请求构造、multipart/JSON 编码、供应商响应转换、可选查询/取消适配、受控成果读取 | 操作业务表、决定用户归属、编排完整业务流程 |
| storage/ | 私有文件流、存储键、安全路径、完整性检查、临时文件清理；实现 ArtifactStore | 调用业务 Controller、更新任务终态 |
| persistence/ | entity/、mapper/、repository/、converter/；实现持久化端口、原子状态更新和去重约束 | 供应商请求、页面 DTO、业务全流程 |
| config/ | ProviderProperties、客户端与执行器配置、必要 Bean 装配 | 隐式业务流程、硬编码服务密钥或 GPU 地址 |
| legacy/ | 保留旧入口到新应用服务的薄适配及执行守卫 | 复制新流程、创建另一套任务/状态模型 |

建议按业务功能继续拆分 application/jobs/、application/assets/、application/capabilities/；不要把所有用例放进 AiServiceImpl.java。每个顶层公开 Java 类型使用同名文件，相关私有小类型可以留在所属文件。

供应商 wire DTO 放在 client/<adapter>/dto/，业务 API DTO 放在 api/dto/，领域类型放在 domain/，数据库实体放在 persistence/entity/。通过小型转换器连接，不使用一个 DTO 同时承担这四种含义。业务请求不能以不受约束的 Map<String,Object> 代替完整字段设计。

## 3. 允许的依赖方向

```text
api/controller  -> application -> domain + port
api/mapper      -> api/dto + domain
client          -> port + domain
storage         -> port + domain
persistence     -> port + domain
config          -> 负责装配实现，集中处理框架配置
legacy          -> application + 必要的旧接口类型
```

- domain 不反向依赖其他层；port 只依赖领域类型和必要的 Java 标准类型。
- application 依赖接口，不直接导入 client/storage/persistence 的实现类。
- Controller 不直接注入 Mapper、HTTP 客户端或文件存储实现。
- client 不访问业务数据库；storage 不更新任务状态；持久化实现不调用远程 GPU。
- 领域规则可独立验证，替换供应商仅修改其适配器和配置，替换存储仅修改对应实现与装配。
- 不建立万能 common/Utils 来绕过依赖边界，不通过静态可变对象保存用户或任务执行状态。

公共端口须定义输入文件如何以流读取、谁关闭流、结果是结构化内容/文件引用/可选远端任务，以及哪些错误说明执行结果未知。供应商数据不能越过适配器直接成为业务状态。

## 4. 一次调用如何分工

1. Controller 解析请求并取得用户身份，应用服务检查能力、参数和输入资产归属。
2. SubmitInferenceService 通过仓储原子保存任务身份、摘要及非敏感快照，重复 key 返回原记录。
3. DispatchJobService 取得派发权后调用 InferenceProvider。等待 HTTP 响应时不保持数据库事务或数据库锁。
4. 供应商适配器构造请求、管理网络资源、校验原始响应并转换为领域结果；不会直接把数据库任务标记为成功。
5. CollectResultService 通过 ProviderArtifactReader 和 ArtifactStore 保存成果，确认完整性后再通过 JobRepository 更新终态。
6. 查询服务返回本地记录，API 转成稳定 DTO。刷新页面只查记录，不重复外呼。

本地幂等必须有数据库唯一性/原子派发保证；仅禁用前端按钮或使用进程内锁不足以防并发重复。跨进程失败仍以现有规格的 UNKNOWN 策略为准，不承诺同步供应商具备远端恢复能力。

## 5. 前端结构

```text
frontend-vue/src/
  api/ai/
    capabilities.js       能力接口
    assets.js             上传与授权下载接口
    jobs.js               提交、状态及可用的取消接口
    index.js              仅导出，避免业务副作用
  services/ai/
    jobPolling.js         查询间隔、停止/恢复、过期响应处理
    resultPresentation.js 仅必要的结果展示转换
  components/ai/
    UploadPanel.vue
    JobStatusPanel.vue
    ResultPreview.vue
    renderers/            仅创建本批已支持的成果类型组件
  views/ai/
    InferencePage.vue
    JobDetailPage.vue
    HistoryPage.vue
```

- 页面负责组合组件及路由状态，不直接创建 Axios 客户端，不解析供应商 JSON。
- API 模块复用现有请求封装，浏览器只访问业务后端，不接收 GPU 密钥或可任意填写的服务 URL。
- 组件使用 props/events 表达输入与操作；成果展示组件不提交任务、不读取全局网络配置。
- 轮询逻辑可独立停止；页面离开/销毁时清理定时器，快速切换任务不让旧响应覆盖新任务。
- 当前 Vue 2 通过普通服务函数和生命周期完成管理；不为本次任务引入 Vue 3 API 或新的全局状态框架。
- 仅在跨页面确有共享状态需求时扩展现有状态管理，不新建一个容纳所有请求、成果和配置的 store 文件。
- 通用页面不堆叠每个算法的 if/else。按已确认成果类型选择小型渲染组件；未支持类型明确反馈。

## 6. 文件规模与可维护性

这些是本项目新增代码的约束，不是声称行业统一标准。

| 检查对象 | 目标 | 超出后的处理 |
|---|---|---|
| Java 手写业务文件 | 尽量不超过 250 行；400 行触发合并前专项拆分审查 | 按用例/适配/转换职责拆分；确有紧密单一职责时由集成记录理由，不能自行忽略 |
| Vue 单文件组件 | 尽量不超过 250 行；350 行触发拆分审查 | 拆展示子组件、API 或轮询服务 |
| JS 手写业务模块 | 尽量不超过 200 行；300 行触发拆分审查 | 拆独立能力，避免万能请求/状态模块 |
| 普通业务方法 | 尽量不超过 50 行；80 行触发审查 | 提取有业务含义的步骤，不制造无意义的一行转发 |
| Controller 方法 | 以解析、调用、返回为主，通常不超过 30 行 | 把业务过程移到应用服务 |
| 相同业务规则 | 单一实现 | 使用明确公共类型/服务，禁止复制出多套状态码或重试逻辑 |

行数按物理行统计，生成代码、静态数据和供应商代码单独说明，不允许将手写代码改名为“generated”绕过检查。超过提示阈值不等于算法错误，但没有拆分或可审阅理由不能作为该包的完成交付。即使行数较少，同时处理数据库、远程请求和页面输出的文件也违反职责约束。

命名表达用途：避免 AiManager、CommonService、Helper、Utils 中不断追加无关功能。异常保留明确类别与 requestId；不吞异常后返回空成功。注释说明约束和原因，不用大段注释代替模块拆分。

## 7. 配置、数据与测试

- 共用字段、状态码、错误码、公开方法由 02-contract 维护；新字段先改契约和样例再改消费者。
- 数据迁移归 04a-assets-jobs 所有，已交付迁移不改写；后续包新增迁移需由所有者/集成分配编号。
- 服务凭据只保存引用，不写入调用配置快照、日志、样例或页面。任务 ID 与资产 ID 查询都检查归属。
- 并发、排队、上传/下载和等待预算各自受限，后台等待不阻塞无限线程，不把全文件读入内存。
- 02-contract 提供公共类型/契约及分层与规模检查；03/04a/04b 各自验证核心行为；00 做真实组合的端到端验收。
- 不给简单 DTO 或纯转发写镜像测试。优先验证状态竞争、跨用户访问、去重、HTTP 结果未知、文件中断、UI 轮询清理。
- 分层静态检查必须明确覆盖范围；简单文本匹配不能冒充完整语义证明。集成时结合 import/引用检查及关键文件审阅。

## 8. 并行开发约束

先阅读 FILE_OWNERSHIP.md 与所在工作包的 AGENTS.md。03、04a、04b 必须从同一已集成的契约/共享类型提交开始。需要公共类型变化时，把变更请求交给 02-contract/00-integration；不得各自维护同名但不兼容的 DTO。

工作包只写自己的 code/，只提交自己拥有的文件。不运行指向原目录的工具，不共享有固定名称的数据卷或数据库，不同时启动占用同一宿主机端口的应用。包内测试通过不等于总任务通过；提交代码、测试证据与限制后由集成统一更新进度。

## 9. 完成交付检查

提交交接材料前检查：模块边界、公开契约兼容、文件规模、文件归属、秘密/日志、针对性测试和实际工作目录。完整业务还需验证上传到成果回存的链路。源码清理等待替代链路验收；不以目录已经建立、文件已生成或格式检查通过宣称功能已实现。

