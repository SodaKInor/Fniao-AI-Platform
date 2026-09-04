# 06 本地恢复、竞态与可观测性交付证据

本目录只证明从第五轮共同起点 `b23f2fc8c5d1911af61dd0f55ad6a89d73c0d09d`
可独立验证的 06 内容。05 尚未提供已确认的 RTX 5070 契约、真实请求和远程操作证据，
所以 6.1—6.5 及伴随流 6.1—6.4 均保持未完成，本交付不得提前合入 00 或释放 07。

## 已实现

- `PENDING` 在重启后仍可原子认领；`DISPATCHING` / `WAITING` 超过有界租约且无查询契约时
  进入 `UNKNOWN`，不会重新发送推理请求。终态/并发完成通过版本和令牌检查优先保留。
- `FETCHING_RESULT` 只在租约过期后重取同一检查点成果；活跃下载不会被 100ms 扫描器抢占，
  恢复路径不会再次调用图片或视频推理。
- 流工作器持久保存“工作器 + 动作”令牌。同一实例用内存占用集避免并发轮询；另一实例须等待
  租约。丢失启动响应不会重发启动 POST；停止调用可能已发送时只查询确认或进入 `UNKNOWN`，
  不重复发送停止 POST。只有明确 `CONFIRMED_STOPPED` 或查询到 `STOPPED` 才写入终态。
- 流事件批次在写入前完成截图归属与会话绑定校验；任一截图不合法时不提交部分事件或游标，
  同一用户的两个会话之间也不能串用截图资产。
- Micrometer 提供任务/流队列和在途 gauge、阶段耗时、结果、错误分类及事件插入/去重计数；
  标签不含 requestId、sessionId、ownerId 或供应商身份。上述身份只用于结构化阶段日志定位。
- 原有文本、HTML 和 error HTML 文件输出均按日期与 10MB 轮转并保留 30 天；未增加依赖。

## 验证

- `java8-tests.json`：一次性 MySQL schema、只读源码挂载、Java 8，59/59 通过，测试库随后删除。
- `fault-matrix.json`：逐项对应恢复、取消竞争、UNKNOWN、断流、事件去重/迟到、停止和权限证据。
- `static-checks.json`：文件归属、冻结契约/迁移、生产 fail-closed、低基数指标、滚动日志和
  远程 POST 不重放边界。
- `backend-build.json`：Java 8 `prod,docker-core` Maven reactor 完整编译成功；宿主机仅余 146MiB，
  镜像导出为避免写满磁盘而终止，因此不记录或沿用旧镜像摘要，待释放空间后复验。
- `graphify.json`：30,112 节点、73,867 边、1,289 社区；仅保留前几轮相同的 6 个 Vue 警告。
- 两个 OpenSpec 变更严格校验，以及 4 份 OpenAPI、34 个 JSON 样例、83 个公共 Java 类型检查。

外层 `06-resilience/HANDOFF.md` 与 `WORKSPACES.json` 记录了 00 对最小持久层查询和日志配置的
明确重分配。没有修改 `backend-master`、前端、迁移、公共 DTO/端口、GPU 源码、权重、历史数据
或现用数据卷。
