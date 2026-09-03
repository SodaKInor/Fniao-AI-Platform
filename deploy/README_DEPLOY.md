# WGAI 本地 Docker 部署

本部署只启动核心管理系统：Vue 前端、Java 单体后端、MySQL、Redis和本地上传存储。真实算法模型由另一台 Windows 机器后续通过契约提供，本次默认不加载模型、OpenCV native library、GPU、摄像头、ASR或 PLC。

## 依赖管理

- macOS 宿主机依赖集中在 `Brewfile`：Git 与 Docker Desktop。
- Java、Maven、Node/npm、Nginx、MySQL和Redis版本集中在 `.env.example`，均运行于本机 Docker 中。
- Maven 依赖由 POM 管理，私有 JAR由 `backend/install-private-jars.sh` 管理。
- 前端只使用 `package-lock.json` 与 `npm ci`；仓库中的 `yarn.lock` 不参与部署。
- 只从 Homebrew、Docker Official Images、Maven现有仓库和 npm锁文件声明的来源下载依赖。

首次运行可能要求 macOS 管理员密码来安装 Command Line Tools，Docker Desktop首次启动也可能显示许可确认。执行：

```sh
./deploy/scripts/start.sh
```

脚本会创建权限为 `600` 的 `deploy/.env` 并生成随机 MySQL/Redis密码。不要提交或分享该文件。

## 日常命令

```sh
# 启动/重新构建
./deploy/scripts/start.sh

# 停止但保留数据库、Redis和上传卷
./deploy/scripts/stop.sh

# 查看全部日志；末尾可附服务名
./deploy/scripts/logs.sh

# 完整验收（包含一次安全的 Compose 重启）
./deploy/scripts/verify.sh
```

默认访问地址：`http://localhost:8080`。

容器内部后端地址：`http://backend:8080/jeecg-boot`。浏览器始终通过同源 `/jeecg-boot` 访问 API；MySQL、Redis和后端没有发布宿主机端口。服务名分别为 `mysql`、`redis`、`backend`、`frontend`。

若在 Linux 服务器上对外提供服务，将 `.env` 中 `FRONTEND_BIND_ADDRESS` 改为受防火墙保护的监听地址，并在外层配置 TLS反向代理。Linux主机只需官方 Docker Engine、Compose plugin、Git、curl和OpenSSL，不需要安装 Java、Maven、Node、MySQL或Redis。

## 数据库初始化

首次创建空的 `mysql_data` 卷时，MySQL依次只读执行：

1. `backend-github/db/java_ai.sql`：真实 `java_ai` 业务转储，来源版本 MySQL 5.7.17；
2. `deploy/db/002-local-sanitize.sql`：清理历史日志、旧算法/视频绑定、API key、回调地址和外部报表数据源凭据。

业务 SQL包含 122 张表和 `admin` 用户，但密码是 PBE加密值，不是明文。只有通过真实登录验证后，才能确认模块 README所述的 `admin/123456`。首次登录后必须立即修改密码。

修改 SQL不会自动更新已有卷。不要为了重试删除卷；先查看 `./deploy/scripts/logs.sh mysql` 并修复具体 SQL错误。删除数据库卷不属于日常部署或回滚流程。

## Windows 算法服务契约（后续）

当前 `.env` 中以下开关均为 `false`：`WGAI_ALGORITHM_ENABLED`、`WGAI_ASR_ENABLED`、`WGAI_OPENCV_ENABLED`、`WGAI_CAMERA_ENABLED`、`WGAI_GPU_ENABLED`、`WGAI_PLC_ENABLED`。

后续契约至少需要定义：健康检查、鉴权、请求/响应 JSON、模型标识、图片/音频上传方式、流式 WebSocket格式、超时、错误码及重试幂等性。浏览器视频流地址通过 `VUE_APP_VIDEO_STREAM_URL` 注入，不能重新写入构建产物。若确需本容器加载 OpenCV，应将 Linux `.so` 放入 `deploy/assets/native/`，设置 `OPENCV_NATIVE_PATH=/opt/wgai/native/<library.so>` 后显式启用；Windows DLL不能在 Linux容器中使用。

本机 Docker Desktop不能为 Linux容器提供 NVIDIA GPU或直接透传常规 Windows摄像头。GPU推理应留在 Windows算法机或迁移到配有 NVIDIA Container Toolkit的 Linux主机。PLC需另行提供可路由的设备地址和协议参数。

## 回滚

1. 执行 `./deploy/scripts/stop.sh`，保留所有命名卷。
2. 原始 `wgai-github.zip`、`wgai-master.zip`、`wgai-vue.zip` 是未修改基线。
3. `deploy/diffs/` 保存本次源码完整差异；需要恢复时，把原 ZIP解压到新的恢复目录，对照差异只恢复列出的源文件。
4. 不运行 `git reset --hard`、`git clean -fd`、递归删除工作区或带 `-v` 的 Compose停止命令。
