# 第五轮 04a 视频任务与流持久化集成验收

00 从共同起点 `0bafd30726e82de74cfeb58ebad12393b36841c7` 以纯快进方式核实并合入
04a 最终交付 `45ba76ce3d55629041092ecb1230bdc1afb8b230`。本验收只放行 04a 的
fail-closed 实现基础，不把模拟用例或 `.invalid` 供应商草案记为真实 RTX 5070 联调。

## 结论

- 64 个交付路径均属于 04a；冻结的 1.1.0 公共 DTO、domain、port、OpenAPI、供应商草案
  均未漂移。未改前端、构建依赖或 `backend-master`。
- 00 在自有一次性 MySQL schema 和 Java 8 环境独立执行 9 个套件，49/49 通过；覆盖图片兼容、
  视频任务、PENDING 取消、`FETCHING_RESULT` 只取成果、流权限/幂等、事件去重/截图、恢复、
  UNKNOWN 和停止终态保护。
- V001→V002 连续执行两次后保持同一 8 表结构；V002 无 ALTER/DROP/DELETE/TRUNCATE，
  `ai_stream_event.score` 可空。00 主库 128 张表清单及既有 AI 表结构/行数摘要前后相同，
  一次性库已删除。
- 4 份 OpenAPI、34 个 JSON、2 个 PNG、83 个 Java 公共类型和两个 OpenSpec 变更严格校验通过。
- 完整 `prod,docker-core` 后端构建成功；独立镜像
  `wgai-integration-backend:round5-assets-45ba76c@sha256:3a5c91104fb4b68fce034fc046116045b2ca9e0bd42e7702063813a3c5be9d84`
  具备预期 Spring Boot 四层布局。

## 故障与恢复记录

验证期间 Docker 虚拟机于 `2026-09-04T07:26:32Z` 正常停止，导致原四个 00 容器退出；日志
证明并非业务迁移或数据库死锁。00 未重置或删除镜像、容器、网络、卷，重新启动 Docker 后
原容器和 `wgai-ri-00-integration_mysql_data` 原卷恢复，MySQL/Redis/backend/frontend 均重新
healthy，18100/19100 返回 200。迁移验收随后从头成功重跑。

## 放行与保留门禁

本次接受平台任务 4.8 及伴随流任务 3.1—3.4，并可在本验收提交产生后作为 04b 的新共同起点。
供应商 v0.2 继续是 `UNCONFIRMED`，全部可选能力为 false，生产流功能硬关闭；真实图片、视频、
source ID 映射、事件查询和停止均仍属于 05。文件任务完整重启对账、远程取消、遥测和故障矩阵
仍属于 06。04b、05、06、6.5、07 和 RC 均未完成。

机器证据见同目录 `java8-tests.json`、`migration.json`、`contract-openspec.json`、`review.json`、
`backend-build.json` 及脚本。构建日志保存在 00 外层私有 `drafts/round5-assets`，不提交生成物。
