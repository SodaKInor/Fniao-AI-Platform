# 01-foundation 验收索引

范围：来源、备份、入口清单和基线环境隔离。没有算法接入、公共类型或业务行为修改。交付提交由工作区外层 HANDOFF.md 记录，集成前由 00 再核实实际提交。

| 任务 | 验证及证据 |
|---|---|
| 1.1 | SOURCES.md、source-inventory.json、source-differences.json、upstream-license-references.json：三个原 ZIP 的 CRC/历史 SHA-256 一致；两个私有 JAR 与原包成员一致；许可证原文保留。build-receipt.json：独立工作树后端 Java 8 和前端构建成功。runtime-api-inventory.json 记录原服务状态 |
| 1.2 | SOURCES.md、templates/、prepare_build_inputs.py：无密钥构建输入和私有制品来源。scope-check.json：候选文件归属、忽略规则及指定凭据检查；不把有限扫描当成通用安全证明 |
| 1.3 | git-inventory.json：既有仓库、origin、main 与十个工作树复核；起点 e1ccab10fe5314c67be811c47cdbc3663e8a4b53。原源码基线 8c7fa382a344e82eb13828d53b9fd9e018a5a461 的含义不变 |
| 1.4 | backup-receipt.json、RESTORE.md：停写并锁全部表导出，覆盖 121 个 InnoDB 和 1 个 MyISAM 表；上传/Redis 归档、运行镜像恢复快照、私有配置在库外保存。归档可读取、校验一致，原四个服务恢复健康 |
| 1.5 | ENTRYPOINTS.md、menu-inventory.json、runtime-api-inventory.json、source-entrypoints.json：158 条导航、234 个运行 API 路径与目标源码调用点；逐项记录处置，需求待定项没有删除授权 |
| 1.6 | runtime-allocations.json、isolation-summary.json、十份 isolation-*.json：核实根目录/分支/独立文件，检查渲染配置，实际启动各包四个服务，并核验数据库、Redis、上传标记以及卷、端口、网络和容器标签 |

## 可复核命令

从本包 code/ 根执行；以下路径前缀为 `backend-github/integrations/ai-contracts/acceptance/01-foundation/`。

- `python3 <前缀>/scripts/prepare_build_inputs.py`：验证并准备忽略的构建资源；不会替换不同内容的已有文件。
- `docker build -f deploy/backend/Dockerfile -t wgai-foundation-backend:e1ccab1 .` 和 `docker build -f deploy/frontend/Dockerfile -t wgai-foundation-frontend:e1ccab1 .`：本次构建所用命令，日志位置/哈希在构建回执中。
- `python3 <前缀>/scripts/inventory.py`：原件/菜单/路径清单，读取原服务，不调用业务执行接口。
- `python3 <前缀>/scripts/isolation.py`：使用本次已验证库外备份，仅在独立新数据库中初始化；已通过的环境默认不重复测试。测试完停止测试服务，保留卷；配置和密码只在库外 drafts。
- `graphify update .`：本工作树 AST 索引更新；日志和限制见 tool-checks.json。
- `openspec validate remote-inference-platform --strict`：规格严格检查；01 不修改总任务表。

`scripts/backup.py` 是已执行停机备份的复现记录，有真实停机动作；本轮已完成，不需要再次运行。唯一通过的备份目录是 `/Users/twowt88/Documents/ChatGPT/WGAI-backups/round1/20260903T090136Z`，准备阶段失败目录的 INCOMPLETE 标记不可用于恢复。

## 验证边界

十个环境逐个验证，均使用 01 从共同源码构建的同一基线镜像，各自使用独立网络、数据库和数据卷。验证结束后测试服务停止，保留数据供对应包继续使用；这不表示十套服务已获准长期同时占用本机资源。其他包修改代码后必须构建自己的镜像并更新自己的运行配置，不能继续把基线镜像当作新代码验证。

数据库在隔离卷内导入并由真实应用启动使用，上传文件的完整字节读取由备份归档校验覆盖。没有在现用卷恢复；没有完整业务恢复验收、登录授权验收、推理、真实 GPU 或正式发布验收。旧算法入口和匿名路径仍由后续包处理。

运行镜像与源码提交分别记录：旧镜像对象缺失时使用运行容器生成恢复快照，保存原配置和原镜像标识；不声称该快照是原构建镜像或能够证明旧部署的源码来源。
