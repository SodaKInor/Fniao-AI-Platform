# 01 来源、构建与配置清单

核验对象：01-foundation 的 `code/`，起点 `e1ccab10fe5314c67be811c47cdbc3663e8a4b53`。原始源码基线仍为 `8c7fa382a344e82eb13828d53b9fd9e018a5a461`，两者不混用。当前 Git 仓库和全部工作树复用既有元数据，未重新初始化或推送。

## 来源状态

| 对象 | 已核验证据 | 记录边界 |
|---|---|---|
| backend-github | 用户于本轮提供原工作区内 ZIP；CRC 和历史 SHA-256 均一致；当前 Git 提交及 README 上游声明保留 | 取得来源记录为本轮用户提供的原包，不推测最初下载日期和上游提交 |
| frontend-vue | 用户提供 ZIP；CRC 和历史 SHA-256 一致；JEECG README 和附加许可文本保留 | 原始字节与当前可追溯 Git 基线分别记录 |
| backend-master | 用户提供 ZIP；CRC 和历史 SHA-256 一致；原目录只读参考，Git 排除 | 不属于实施范围，未复制进工作树 |
| asrt_sdk_maven-1.0-alpha1.jar | 本机文件 17,999 字节；与两个原 ZIP 中相应成员哈希完全一致；嵌入坐标 net.ailemon.asrt:asrt_sdk_maven:1.0-alpha1 | SDK 上游声明 Apache-2.0；上游当前 artifactId 为 sdk，本地包为 asrt_sdk_maven，保留此差异；不把服务器 GPL 许可套给客户端 SDK |
| opencv-4.10.0.jar | 本机文件 803,300 字节；与两个原 ZIP 中相应成员哈希一致；Java 8 重新编译和 Maven 安装通过 | OpenCV 4.10.0 标签声明 Apache-2.0；本地字节来源是原 ZIP，不声称官方二进制签名验证 |

初次搜索未找到原 ZIP；用户随后将三个原件提供在原工作区。重新检查原件后，三个文件的历史哈希和 ZIP 完整性均通过。未绕过 macOS 保护目录的访问限制。

| 已验证原件（原 WGAI 根目录） | 本轮重算并匹配的 SHA-256 |
|---|---|
| wgai-github.zip | `350e6da553ded3966313424a9638537976b6a4a2911f5754432b34b814ecef0c` |
| wgai-master.zip | `44723e788f9420d1b816c31972fc3f0a59b4bc6221185a47c5db53d6b0fd3630` |
| wgai-vue.zip | `dba19ab7383d15f4aa97f2a0195244d73fe6d09278bce2127e7b862991ef3d27` |

source-inventory.json 记录原件位置、大小、CRC 检查、SHA-256、许可证路径及私有 JAR 成员对应关系。source-differences.json 记录原包与 Git 基线源码的文件差异，不输出旧配置或敏感内容。

## 许可证保留

`backend-github/LICENSE` 为 Apache 2.0 文本；`frontend-vue/LICENSE` 和 `backend-github/wg/LICENSE` 另含补充条款。三个文件均与原工作区字节一致，哈希记录在 source-inventory.json。这里记录实际文件，不把全部代码或私有 JAR 一概判定为同一许可，也不作额外授权推断。

额外核对 [ASRT Java SDK 的固定提交许可](https://github.com/nl8590687/ASRT_SDK_Java/blob/4dfaa57b3b2135f1f9bc4c0b5cdbc4e53f4f872f/LICENSE) 与 [OpenCV 4.10.0 许可](https://github.com/opencv/opencv/blob/4.10.0/LICENSE)，均声明 Apache-2.0。检索日期、原文哈希和 ASRT 上游 Maven 坐标记录在 upstream-license-references.json。其作用是保存上游声明，与原 ZIP 的本机字节来源核验分别记录。

## 私有制品取得与独立构建

当前可复现的本机来源是用户提供的原 WGAI 根目录 `wgai-github.zip`。运行 `scripts/prepare_build_inputs.py`，先核对完整归档及两个成员哈希，再复制到当前工作树的同名忽略路径；已有不同内容时拒绝覆盖，不建立共享可写链接，不提交二进制。

- ASRT：`10b56560251cec9bac5a92a6ab058b84ee3b43c438ac9cab5a257fbf96981330`。
- OpenCV：`794e79dc1b77bc849d60081f0fad069403a01abb1d1255e984bdd8b9e1bb2d81`。
- 现有 `deploy/backend/install-private-jars.sh` 从 ASRT 嵌入 POM 安装坐标；OpenCV 经现有重编译脚本生成 Java 8 major 52 绑定，再安装到 Maven。本轮未修改两份既有脚本。
- 在 01 独立工作树按既有 Dockerfile 分别构建 `wgai-foundation-backend:e1ccab1` 与 `wgai-foundation-frontend:e1ccab1`，未覆盖原 `wgai-*:local` 标签；结果、日志路径和哈希见 build-receipt.json。
- 构建前还需将 templates/jeecg_database.properties.example 放入忽略的 resources/jeecg/jeecg_database.properties。此无密钥占位资源用于代码生成器类初始化；在线模式随后由 CodeGenerateDbConfig 使用当前隔离数据源覆盖。没有这个资源时源码能编译但应用不能启动。
- 后端 Maven 构建使用既有 `prod,docker-core` 配置，测试按既有 Dockerfile 跳过；前端按既有锁文件安装和构建。构建成功不等于登录、算法或端到端验收。

新机器从制品持有人取得上述哈希的原包，并同时保留其许可和本清单的上游许可引用。没有匹配原件时不使用其他同名 JAR 替换。

## 无密钥运行配置

既有 `deploy/.env.example` 与 `deploy/backend/application-docker.yml` 仍是环境变量模板，不复制真实 `.env` 进入仓库。本包的 `scripts/runtime_profiles.py` 用于生成独立的基线检查配置：

1. 核实每包代码根与登记分支，选择本轮独立构建的不可变镜像标识。
2. 为每包生成不同的 MySQL root、业务数据库和 Redis 密码，仅写在 01 包外层 `drafts/runtimes/<包名>/.env`，权限 600；该目录不在 Git 工作树内。
3. 为每包分配独立 MySQL 数据库、网络、三个命名卷和登记端口，端口仅绑定 127.0.0.1。原 WGAI 的卷和目录不挂入测试容器。
4. `compose.json` 包含环境变量占位符；渲染后的配置含运行密码，只存同目录的 `rendered.private.json`。公开验收记录只含资源标识，不含密码。
5. Git 排除了原 application-prod.yml。基线检查显式补入不含密钥的 Druid 自动配置排除、MyBatis mapper 路径及未使用的 ES/OSS/MinIO/邮件空配置。旧源码要求这些属性存在，空值不启用外部服务。具体结果见隔离记录；本轮不宣称登录后全部业务选项已经验收。

所有算法、OpenCV、ASR、摄像头、GPU、PLC 开关在测试配置中均为 false。本包未调用执行入口，不能由这些配置值推断现有旧执行守卫已经实现。

## 交付范围

新增资料和验收辅助脚本仅在本包 acceptance 子目录；没有业务代码、公共 DTO、依赖或锁文件改动。原始数据、私有制品、配置与日志不纳入提交。候选提交检查结果见 scope-check.json；检查覆盖范围和限制必须与结论一同阅读。
