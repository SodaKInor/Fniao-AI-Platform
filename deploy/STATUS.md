# WGAI 部署状态

## 审计摘要

- 源码来自三个无 Git 元数据的 ZIP；原始 ZIP保留不变，SHA-256：
  - `wgai-github.zip`: `350e6da553ded3966313424a9638537976b6a4a2911f5754432b34b814ecef0c`
  - `wgai-master.zip`: `44723e788f9420d1b816c31972fc3f0a59b4bc6221185a47c5db53d6b0fd3630`
  - `wgai-vue.zip`: `dba19ab7383d15f4aa97f2a0195244d73fe6d09278bce2127e7b862991ef3d27`
- 目标后端：Spring Boot 2.6.6、JEECG 3.4.3、Java 8，单体启动类 `org.jeecg.JeecgSystemApplication`。
- 原默认 dev配置监听 `9998` 和 `/jeecg-boot`，旧 Compose却按 `8080` 构建，且数据库 Dockerfile引用不存在的 `jeecgboot-mysql-5.7.sql`。
- Docker profile现统一为内部 `8080` 与 `/jeecg-boot`；Nginx使用相同地址。
- 目标后端资源包含 ASRT与OpenCV两枚私有 JAR。ASRT坐标来自嵌入 POM；OpenCV坐标来自包名、Manifest版本与目标 POM精确匹配。
- 原启动类在 Spring启动后无条件加载作者 Windows OpenCV DLL，是 Linux容器必然退出的直接阻塞；现已默认关闭。
- 前端为 Vue 2、Vue CLI 3.12.1、history router、npm lockfile v2；生产 API与视频流原先包含多个绝对/局域网地址。
- `db/java_ai.sql` 来源 MySQL 5.7.17、schema为 `java_ai`、共 122 张表；原 SQL不含 `CREATE DATABASE/USE`，由 `MYSQL_DATABASE`选择。
- SQL包含 `admin` 加密密码、历史内网地址及明文摄像头口令；本地清理 SQL会删除历史日志和旧集成凭据。
- PLC仅存在于手工 `main` 测试代码，没有发现随 Spring自动连接的 Bean。算法、摄像头、ASR和模型代码按需调用，本次不启动。

## 实施记录

- [x] 完整展开三份源码并将 `backend-master` 设为只读。
- [x] 创建可维护的 Compose、Dockerfile、环境、脚本和部署文档。
- [x] 条件化 OpenCV native加载。
- [x] 统一私有 JAR坐标与安装流程。
- [x] 将前端生产 API改为同源相对路径并移除构建时局域网视频地址。
- [x] 创建首次初始化后的敏感数据清理 SQL。
- [x] 安装并记录宿主机工具版本。
- [x] 后端镜像构建通过。
- [x] 前端镜像构建通过。
- [x] 四个服务启动且健康。
- [x] `verify.sh` 完整通过。
- [ ] 浏览器登录与账号验证完成。

后续构建、日志错误和修复会继续追加到本文件。

## 宿主机版本

- macOS 15.7.9（Intel）。
- Xcode Command Line Tools 16.4；Apple Git 2.39.5；Clang 17.0.0。
- Homebrew 6.0.21；Homebrew Git 2.55.0。
- Docker Desktop 4.89.0（238018）；Docker Engine 29.7.2；Docker Compose 5.5.0。

## 构建与修复日志

- 2026-09-02：`brew bundle` 首次安装 Docker Desktop 时因 `/usr/local/cli-plugins` 不存在且终端不能直接读取管理员密码而停止；通过 macOS 管理员授权创建该目录后重试成功。Docker Desktop 许可由用户在界面确认，Engine 自检通过。
- 2026-09-02：第一次后端构建失败，Maven 无法解析 `com.wlld:easyAi:1.1.7`。初次按错误的 `com.wlld` 包名检索，误判为 Java 源码未引用；后续编译确认实际包名是 `org.wlld`。该依赖仍属于本次停用的算法引擎，Artemis 已成功解析并保留。
- 2026-09-02：后端 Docker 构建启用 BuildKit Maven 缓存目录，以便失败重试复用已下载依赖。
- 2026-09-02：第二次后端构建进入源码编译后失败。随项目提供的 OpenCV 4.10.0 JAR 由 JDK 11 生成（class major 55），不能被 Java 8 编译器读取。该 JAR 同时内嵌完整生成 Java 源码，因此部署构建会用 JDK 8 重新编译 Java bindings、校验 major 52 后再按 `org.opencv:opencv:4.10.0` 安装；原私有 JAR 保持不变，native library 仍默认不加载。
- 2026-09-02：第三次后端构建中 Java 8 重新编译 OpenCV 已完成，但校验步骤错误依赖了 Maven 镜像中未安装的 `unzip`；改为直接读取刚生成的 class 文件头，未增加宿主机或运行镜像依赖。
- 2026-09-02：第四次后端构建确认 OpenCV Java 8 bindings 编译、major 52 校验与私有坐标安装全部通过；随后发现 `easyAi` 实际仅被一个演示 controller、三个聊天训练类和一个图片训练类引用。新增 `docker-core` Maven profile，仅在本地核心镜像中排除这五个算法演示源文件，其余聊天/图片配置、数据实体、CRUD controller、service 和 mapper 均保留编译。
- 2026-09-02：第五次后端构建成功。Reactor 八个目标模块全部 `SUCCESS`，Spring Boot 可执行 JAR 已打包并导入 `wgai-backend:local`；编译期仅有原项目对内部/废弃 API 和未检查泛型的告警，无错误。
- 2026-09-02：前端首次构建成功。`npm ci --legacy-peer-deps` 从 `package-lock.json` 安装 2068 个包，Vue CLI 生产 bundle 完成并导入 `wgai-frontend:local`。保留的告警包括旧锁定依赖弃用/已知风险、5 个 CSS 顺序/资源体积告警；未改写锁文件，Yarn 未参与。
- 2026-09-02：首次并行启动 MySQL/Redis 时，Docker Desktop 将两个容器停留在 `Created`；中断后 Redis 可单独正常启动，MySQL 单独启动仍停在 `Starting`。根因范围缩小为 MySQL 对 macOS `Documents` 目录中两份初始化 SQL 的运行时 bind mount。改为构建本地 `wgai-mysql` 初始化镜像并在镜像内 COPY SQL，取消运行时源码目录挂载；既避免宿主目录授权依赖，也保留“仅空卷首次执行”语义。已有命名卷未删除。
- 2026-09-02：上述中断发生在 MySQL 创建系统数据文件期间，尚未导入 `java_ai` 业务表，遗留的 InnoDB 文件无法干净启动。为避免删除或覆盖数据卷，先将全部不完整文件完整迁移到独立命名备份卷 `wgai_mysql_failed_init_20260902`，再在原命名卷 `wgai_mysql_data` 上重新完成初始化。原卷与备份卷均保留；随后 `001-java_ai.sql`、`002-local-sanitize.sql` 全部成功，MySQL 8.0.36 健康，初始化 122 张业务表，`sys_log`/`sys_data_log` 已清空，`admin` 账号记录存在且启用。
- 2026-09-02：后端首次运行进入重启循环，启动日志显示 `ThirdLoginController` 强制注入不存在的 `AuthRequestFactory`。Docker 配置已将 `justauth.enabled=false`，starter 因而正确不创建该 Bean，但原 controller 未使用同一开关。现为该 controller 增加同条件装配，关闭第三方登录时不再注册端点，不影响用户名密码核心登录。
- 2026-09-02：修复并重建后，后端在 20.982 秒内完成启动，内部 `8080/jeecg-boot` 健康检查通过，重启次数为 0；日志确认 OpenCV关闭，且无数据库、Redis或硬件连接错误。
- 2026-09-02：前端首次运行失败，Nginx 将带 `{8,}` 量词的未加引号静态资源正则误解析为指令。完整正则加引号后重建运行层；Vue构建产物无需改变。
- 2026-09-02：前端修复后健康，绑定仅为 `127.0.0.1:8080`。Vue 首页、`/user/login` history fallback和同源验证码代理均返回 200，`index.html` 带禁缓存响应头；MySQL、Redis和后端均未发布宿主机端口。
- 2026-09-02：`verify.sh` 十项验收完整通过：四服务健康、MySQL/Redis认证、122 张业务表、后端匿名页、同源 API、清理 SQL、生产静态地址扫描、后端错误/硬件探测扫描，以及 Compose重启后的数据库探针持久性均成功。
- 2026-09-02：实际执行 `stop.sh` 后确认三个正式命名卷与不完整初始化备份卷仍在；随后执行 `start.sh`，三镜像构建缓存复用，MySQL/Redis、后端、前端按序恢复健康。停止/启动脚本验收通过，数据未重新初始化。
- 2026-09-02：应用内浏览器真实打开 `http://localhost:8080`，自动进入 `/user/login?redirect=%2F`；页面标题、背景、三项输入框、登录按钮和验证码图片均正常，控制台无 warning/error。因验证码提交需要动作前确认，默认账号有效性与登录后上传验证尚待完成。
- 2026-09-02：重新从未修改 ZIP 基线生成完整源码差异：`backend-github.diff` 323 行（SHA-256 `239325ef0350b0b8ddb47d83bfe56185c262db9863e91591e6f929f247230399`），`frontend-vue.diff` 143 行（SHA-256 `f9ef49edba66b6b8012041cd2f20f49b6491a4db54b274699020bdb558868bc3`）。`backend-master` 递归检查无可写文件或目录。
