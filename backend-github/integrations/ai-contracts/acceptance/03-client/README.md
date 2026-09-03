# 03-client 验收说明

范围：3.1、3.2、3.3、3.5 和 3.4 后端。代码根为本包独立 code 工作树，分支 work/remote-inference/03-client。共同冻结起点 ab9809d23919ea5d61dfc7d8b34d7f30bb9d607c，公共契约 5a55ca5cc6ea8fde09898f44519d62c715af12db。公共类型、规范和样例未修改。

## 证据

| 文件 | 验证内容 |
|---|---|
| java8-tests.json | 29 项真实 JUnit 4 测试、当前源码哈希、Java 8 class major 52、日志哈希 |
| contract-checks.json | 沿用冻结校验器检查 2 份 OpenAPI、15 个正反 JSON 和 2 个 PNG；输出仅写本包 |
| scope-and-architecture.json | 精确允许路径、公共契约无差异、Java AST import/类型/方法与文件行数 |
| build.json | 既有 Dockerfile 完整后端构建和镜像标识；两次运行验收使用同一最终镜像 |
| runtime-remote-missing.json | remote 配置缺失时核心启动、原健康检查及匿名访问拒绝 |
| runtime-native-guard.json | disabled 且旧 OpenCV 开关 true、无效库路径时仍完整启动，native 加载被守卫 |
| final-checks.json | OpenSpec、Graphify、隔离资源停止和最终范围核对 |

## 测试覆盖

- multipart 元数据和独立服务凭据；不发送用户令牌、归属信息或磁盘路径；输入只打开/关闭一次。
- 正常、有效空成果、版本和关联 ID 不匹配、重复/未知 JSON 字段、坐标越界、非有限数字、尾随 JSON。
- 400/401/403/408/429/500/503/202/301/307；Retry-After:0、断线、慢响应均检查实际调用次数，不重发推理。
- 连接拒绝 NOT_STARTED；发送后断线、超时和协议问题 UNKNOWN；外部鉴权为 PROVIDER_AUTH。
- 并发限额、实际上传字节超限、独立上传传输预算、成果有效期/来源/媒体/长度/截断/下载总预算及双重关闭。
- 真实回环 HTTPS：默认不信任夹具 CA；加载专用 CA 后成功；主机名不符仍拒绝。
- mock/disabled/remote 路由、模拟标记与空成果、保存绑定对应成果在停用后仍可读取。
- 实际 Shiro/JwtFilter 与配置排除顺序；匿名、无 ai:infer、授权后旧入口停用；保留查询；服务凭据失败后业务身份仍有效。
- 10 个旧 Controller 方法、19 个旧服务方法直接调用全部在依赖、线程或素材访问前拒绝；启动 native 守卫另有容器证据。
- 能力仓储为测试替身；权限、停用、配置缺失、外部观测与模型就绪未知、Spring 属性绑定、真实 MockMvc 响应契约；无仓储时 Spring 上下文可启动并明确报告依赖未就绪。

## 重现

从当前 code 根运行 `python3 backend-github/integrations/ai-contracts/acceptance/03-client/scripts/test_package.py`。脚本使用已存在的 maven:3.8.8-eclipse-temurin-8 与第一轮后端镜像的既有依赖，从本机 Maven 缓存取 JUnit 4.13.2、Hamcrest 2.2、spring-test 5.3.18。当前源代码只读挂入容器、fresh 编译到本包 drafts/validation，不改变 POM。容器网络为 none，只能访问自身回环夹具。测试证书、凭据、日志和临时文件均在本包 drafts，不入库。

`validate.py` 需要已有验证环境中的 javalang、jsonschema、openapi-spec-validator。本次使用 02 已有只读虚拟环境执行本包脚本；复用冻结检查函数时把输出目录定向本包，未覆盖 02 回执。校验器依赖提示 LibreSSL/urllib3 兼容警告；校验没有远程 schema 或外部网络请求。

完整构建使用原 deploy/backend/Dockerfile，先运行 01 的哈希核对脚本准备本工作树忽略的两个私有 JAR 和无秘密代码生成配置。Maven 仍按既有配置跳过测试，因此构建记录与独立 JUnit 记录明确分开。

## 审阅与限制

client 不导入应用流程或仓储实现；应用能力服务只读取冻结仓储端口和装配层提供的可用性事实；Controller 不做 HTTP、SQL 或文件搬运。Wire JSON 仅在 draft 编解码器内部按固定字段构造/验证，没有替换业务 DTO 或新增公共端口。

新增 Java 文件/方法均低于 250/50 行。旧历史服务 907→924 行、订阅 Controller 317→318 行等原有超限文件只增加薄守卫，不在本包搬迁其算法代码；基线与当前规模都在 AST 报告中。后续清理/拆分由 07 按交接归属处理。AST 检查不是完整语义证明，结合调用入口审阅与直接调用测试。

真实 GPU、实际账户登录、任务/资产落库、跨用户历史与下载、页面完整链路均未在本包验收。Shiro 身份后端使用明确测试 Realm；仓储替身只在 src/test。运行健康沿用原 doc.html 检查。隔离环境的 Swagger 文档返回空路径表，因此没有把它作为能力路由注册的证明，能力路径和 JSON 已用 MockMvc 验证；00 可结合原运行配置复核文档清单。

3.4 前端仍待 04b，4.7 完整模拟闭环由 00。真实协议尚未确认，remote 永不因配置开关自动启用；不宣称模型就绪、远端查询/取消/去重或 GPU 验收通过。
