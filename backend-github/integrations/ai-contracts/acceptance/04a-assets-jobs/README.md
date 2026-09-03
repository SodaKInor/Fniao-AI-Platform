# 04a 文件、持久任务与成果验收

范围仅 4.1—4.5。内部业务 API 1.0.0，冻结 Java 端口；不实现 HTTP 客户端、前端、取消 API、重启对账或 4.7。

## 验证内容

- `migration-checks.json`：在 04a 已有数据库副本执行增量迁移两次，检查预存结构与目标定义一致；123 张原表的结构和数据摘要不变。
- `verification.json` / `junit.txt`：Java 8 下实际执行 JUnit；数据库采用真实 MySQL，供应商及成果 reader 为测试替身。
- `layer-checks.json`：检查冻结类型未修改、AST import 依赖方向、文件/方法规模和直接网络 API。结合职责审阅与运行测试；不声称静态文本检查证明全部架构性质。
- `scope-check.json`：提交范围、冻结契约内容及共同起点。

测试覆盖：数据库并发创建和独占派发、取消与派发竞争、状态事件事务回滚、token/version 与终态保护、区分大小写的 key、容量满载、历史游标及用户隔离；规范摘要、能力停用后的重复查询、短等待、真实有界执行器、UNKNOWN 禁止重发、显式尝试关联；完整成果、有效空成果、下载中断、拒绝来源/重定向、期限、长度/哈希、元数据提交前/后异常、服务离线后的历史；严格 JSON、授权上传下载、旧静态读取拒绝和响应开始后的断流。Spring 测试实际加载本包装配。

来源/重定向测试验证应用对 reader 拒绝的处理；真实 URL、DNS、TLS 和重定向策略属于 03。登录测试注入现有 Shiro `LoginUser` 身份，不声称验证真实令牌签发/验证链。模拟数据、包内后端验证不等于 4.7 页面演示、真实 GPU 或正式环境验收。

## 隔离与复现

只在 `04a-assets-jobs/code` 执行，先确认分支和数据库容器/卷归属。测试会清理本包副本中的新增 `ai_*` 表内容；不用于现用数据库。使用登记的 `wgai-ri-04a-assets-jobs_network`、MySQL 数据库 `wgai_ri_04a_assets_jobs` 和本包私有环境文件。测试服务无需公开宿主机端口。

1. 按 01 登记的独立 runtime profile 启动本包 MySQL，保留其已有历史副本；不要运行原项目启动或清理脚本。
2. `python3 backend-github/integrations/ai-contracts/acceptance/04a-assets-jobs/scripts/verify_migration.py`
3. `python3 backend-github/integrations/ai-contracts/acceptance/04a-assets-jobs/scripts/prepare_validation.py`。默认从已验收集成运行镜像提取现有依赖；可用 `--runtime-image` 指定包含 `/app/app.jar` 的兼容镜像。测试依赖版本与 SHA-256 固定。
4. Java 8 容器挂载本代码根只读为 `/workspace`、本包 `jeecg-system-biz/target/04a-validation` 为 `/validation`；加入本包网络并通过本包私有 `--env-file` 提供 `MYSQL_PASSWORD`。运行 `sh /workspace/backend-github/integrations/ai-contracts/acceptance/04a-assets-jobs/scripts/run_tests.sh`。脚本显式执行 JUnitCore，绕开父 POM 的固定跳过测试配置；未修改公共 POM。
5. 在本包忽略目录安装 `javalang==0.13.0` 后运行 `scripts/check_layers.py`；它只输出本包验收材料。完整构建沿用既有 `deploy/backend/Dockerfile` 的 `build` 阶段和 01 私有输入准备流程。
6. 完成后停止本包 MySQL 容器，保留独立卷和验证日志；在本 code 根运行 `graphify update .`。

## 装配与配置

`JobsConfiguration` 提供三个仓储、私有文件存储、提交/查询用例和后台执行器。03 提供唯一 `InferenceProvider` 与 `ProviderArtifactReader` Bean；缺少任一端口实现时执行器不派发，没有生产测试替身。能力描述由 `ai_capability_binding.descriptor_json` 保存，对应冻结 `Capability` 字段，端口只读。

迁移不自动启用能力或写入供应商地址。00/03 集成时写入与其配置匹配的能力描述：`snapshot.providerKey`、`adapterId`、外部能力代码和版本由 03 的配置决定；只有真实确认的模式/限额才能标可用。单元测试以 `fixture` 绑定显式写入独立数据库，这个绑定不能用于正式运行。没有新增管理 API 或第二套能力实体。

配置前缀 `wgai.ai.jobs`：`private-root` 默认 `/data/ai-private`，必须挂载专用持久私有目录，并与旧 upload/webapp/静态根完全分离；`parallelism=1`、`max-queued=20`、`max-input-bytes=10485760`、`max-output-bytes=10485760`、`max-image-dimension=4096`、`input-retention-days=7`、`output-retention-days=30`。当前固定模拟契约只允许收紧媒体资源限额，JSON 输入另有 16 KiB、8 层与数值精度/scale 1000 的资源预算。实际输入还检查能力限制；收集按配置与当前保留能力描述中的更严格输出限制执行。停用绑定不会拒绝已存在任务的查询或重新提交。

外呼总预算和网络资源关闭由 03 实现；本包不重新包装 HTTP 超时、取消或重试。文件失败最多收集三次，间隔 1、2 秒，受引用期限限制。UNKNOWN、FAILED、SUCCEEDED、CANCELLED 保持终态；6.x 的重启恢复和取消应用用例待交接后实施。崩溃或数据库长期不可用造成的在途记录/孤儿文件需要后续恢复处理，本包不声称完成 6.1。

数据库事件与状态事务提交；网络/文件 I/O 不持有事务。元数据提交失败后先检查是否已经提交，避免误删已被引用的文件；可以确认未引用时清理。无法访问数据库确认归属时保留文件和错误，不冒险删除。
