# 04a 视频任务、流会话与私有成果验收

本轮从共同起点 `0bafd30726e82de74cfeb58ebad12393b36841c7` 实施 4.8 及
`remote-video-streaming` 3.1—3.4。冻结业务契约为 1.1.0；没有修改 domain、port、API DTO、
OpenAPI 或 provider draft。

## 已完成

- 图片任务保持 1.0 JSON 和行为兼容；旧记录没有 `jobType` 时按 `IMAGE_DETECTION` 读取。
- `POST /ai/v1/video-jobs` 复用原任务身份、幂等、历史与授权资产；新增 PENDING 本地取消接口。
- MP4/H.264 私有上传、事件时间线、截图和可选标注视频均使用有界流式存储；成果完整落盘和建立归属后才能成功。
- `ai_stream_source`、`ai_stream_session`、`ai_stream_event` 由 V002 增量创建；浏览器只见本地 `streamSourceId`。
- 五个流业务端点、owner 隔离、持久幂等、启动独占、事件去重/游标、截图归属、停止请求与终态保护已实现。
- 流启动响应丢失、不可查询恢复、未确认停止结果进入有原因的 `UNKNOWN`；只把供应商明确确认的停止写为 `STOPPED`。
- `FETCHING_RESULT` 文件任务恢复只重取同一成果，不重新调用推理；更广的重启对账仍属于 06。

生产装配把流能力四个开关固定为 false。05 未给出真实 source ID 映射、查询和停止证据前，
即使数据库能力描述被误启用也无法创建流会话。没有将 RTSP、GPU URL、凭据或 provider session ID
放入浏览器请求/响应。

## 验证结果

- Java 8 下 180 个 AI 主源码文件编译，抽样 class major version 为 52。
- 真实隔离 MySQL 8.0.36 执行 49 个 JUnit 用例，0 失败。
- V001→V002 在临时库重建并各重复执行；8 张 AI 表结构一致，123 张历史业务表的结构和行摘要不变，V001 表结构/行摘要不变。
- AST 分层、文件/方法规模、冻结内容和文件归属检查通过。
- 完整 `prod,docker-core` 后端构建与 Spring Boot layertools 检查通过。
- 镜像 `wgai-04a-assets-jobs:round5-7c8a80f`：
  `sha256:74380adf50d0b9c9c1f510e94ae876eff589e16eb790c45315641cff0c9424c0`。
- `graphify update .` 完成：29830 nodes / 73166 edges；保留 6 个既有 Vue 解析警告。

验证覆盖视频类型/大小/幂等冲突、有效空分数、截图与标注视频、响应丢失、成果恢复、PENDING
取消；流来源权限、重复启动、事件去重、稳定游标、截图、迟到事件、停止竞争、停止不支持/未知及重启查询。
API 用例验证匿名与跨用户拒绝，并拒绝 RTSP、GPU URL、凭据、重复字段和标量强制转换。

## 复现边界

验证只使用 `04a-assets-jobs` 的数据库容器、网络和卷；源码只读挂载到 Java 8 容器，日志写到忽略的
`jeecg-system-biz/target/04a-validation`。复现顺序：启动本包 MySQL，运行 `verify_migration.py`，
准备依赖，运行 `run_tests.sh`，再运行 `check_layers.py`、`check_scope.py` 和完整后端镜像构建。
私有口令仅从本包运行时配置注入，不写入证据。

验证完成后测试生成的 AI 行已清空，数据库容器停止，卷
`wgai-ri-04a-assets-jobs_mysql_data` 保留。没有触碰 18100/19100 服务、backend-master、GPU 源码、
权重、现用数据卷或历史资产。

## 未宣称完成

- 真实 GPU 图片/视频/RTSP 请求与成果展示（05）。
- DISPATCHING/WAITING 文件任务的完整重启对账、远程取消、日志/指标和故障注入矩阵（06）。
- 04b 页面、真实登录链与 00 集成验收。
- 生产部署、推送、07 清理或发布候选。
