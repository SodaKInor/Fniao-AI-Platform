# Fniao AI Platform 本地 Docker 部署

根 Compose 启动 Vue 前端、Java 8 单体后端、MySQL、Redis和私有上传存储。真实 GPU 服务不在本仓库内；默认 remote 模式为 `disabled`，开发 stub 只能通过显式 profile 加入。

## 准备私有输入

版本库不包含数据库转储和两个私有兼容 JAR。首次构建前在被 Git 与 Docker 构建上下文忽略的位置准备：

```text
database/private/java_ai.sql
apps/backend/jeecg-module-system/jeecg-system-start/src/main/resources/asrt_sdk_maven-1.0-alpha1.jar
apps/backend/jeecg-module-system/jeecg-system-start/src/main/resources/opencv-4.10.0.jar
```

数据库基线也可放在仓库外，并在未跟踪的 `deploy/.env` 中设置 `DATABASE_BASELINE_FILE`。Compose 仅在首次初始化时把该文件只读挂载为 `001-java_ai.sql`；它不进入 Git、构建上下文或镜像层，也不受 BuildKit secret 的 500 KiB 上限影响。空卷初始化顺序固定为：私有基线 → `database/bootstrap/002-local-sanitize.sql` → V001 → V002；正式默认不应用 stub seed。

## 启动与验证

从任意目录调用仓库内脚本均会通过 Git 解析最终根：

```sh
./deploy/scripts/start.sh
./deploy/scripts/verify.sh
./deploy/scripts/logs.sh
./deploy/scripts/stop.sh
./deploy/scripts/backup-database.sh /absolute/path/outside-repository/backup.sql
./deploy/scripts/verify-restore.sh /absolute/path/private-baseline.sql
```

`start.sh` 在缺少 `deploy/.env` 时生成权限为 600 的本地 MySQL/Redis 密码，并在构建前检查私有数据库基线。默认访问地址为 `http://localhost:8080`。MySQL、Redis和后端不发布宿主机端口；浏览器经同源 `/jeecg-boot` 访问业务后端。

修改初始化 SQL 不会改变已有卷。不要为重试删除卷；先检查具体日志。`backup-database.sh` 只允许写到仓库外，`verify-restore.sh` 只在无网络、tmpfs 的一次性 MySQL 容器中验证 bootstrap、V001→V002 与重复执行行为，不接触现用卷。

## 远程推理

正式模板没有真实 GPU URL、token 或 CA。具体的 disabled、显式 stub 和真实秘密配置方式见 [远程推理部署边界](remote-inference/README.md)。本地 stub 结果不能替代 RTX 5070 或 RTX 4090 真实验收。

## 回滚与保留

`stop.sh` 默认保留数据库、Redis和上传卷。应用回滚到兼容提交时保持新推理 disabled，保留新增表和历史成果，不执行破坏性数据库降级。01—07 的旧 ZIP、卷、路径和运行记录仅保存在历史文档中，不是当前恢复入口。
