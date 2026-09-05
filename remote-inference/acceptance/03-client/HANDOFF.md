# 03-client 第五轮交接

状态：`READY_FOR_00_ACCEPTANCE`。03 实现和本地验收已完成；真实 RTX 5070 契约仍是外部阻断，生产视频/流能力保持 disabled。

## 基线与提交

- 分支：`work/remote-inference/03-client`
- 00 共同起点：`e44041ec050974ee3f36655f6869fb96cf16faad`
- 公共契约：`1177de8be45123d043d7cb26b845ee9d94c26784`
- 实现与验收证据：`f7e8bfba4cee2ed2bb340685272a626235f2f92b`
- 本文档在后续独立提交；最终交付 SHA 写入工作树外层 `03-client/HANDOFF.md`，避免提交自引用。

## 完成范围

- `remote-inference-platform` 3.6、3.7 的代码包：图片兼容，新增上传视频与实时流的真实 HTTP 草案客户端、严格转换、批准地址/TLS/CA/独立鉴权、大小/并发/超时边界及分项硬关闭门禁。
- `remote-video-streaming` 2.1—2.3 的代码包：会话启动、状态查询、事件查询、停止转换；未知/重复/缺失字段和非法状态拒绝；启动/停止响应丢失为 UNKNOWN，POST 不重放，有效空事件页合法。
- 浏览器/业务 DTO 没有新增 RTSP、GPU URL 或凭据字段。provider source ref 只能由后续 04a 的本地授权来源映射产生。
- 05 真实资料缺失时，草案适配器不注册为生产 Bean；只注册返回明确不可用原因的模式端口。不存在通过环境变量绕过确认门禁的路径。

## 00 验收命令与结果

- 42 项 Java 8 JUnit：PASS，class major 52。
- 契约：4 OpenAPI、34 JSON 正反例、2 PNG：PASS。
- 包归属、公共契约冻结、Java AST/规模：PASS；相对共同起点只涉及 03 允许路径。
- 完整后端镜像：`wgai-03-client-backend:round5`，摘要 `sha256:7f3a693dc7a90e2cd3c801b9fa98a6e58a74f38fc88eca47bf6deac6e02fec54`，Spring Boot layertools smoke：PASS。
- `remote-inference-platform` 与 `remote-video-streaming` strict：PASS。
- `graphify update .`：PASS；6 个既有 Vue 语法告警，未改前端。

复现方式与逐项证据见同目录 `README.md`、`java8-tests.json`、`scope-and-architecture.json`、`contract-checks.json`、`build.json` 和 `final-checks.json`。

## 交给 00 / 04a

1. 00 先核对本分支祖先和允许路径，再独立复跑 42 项测试、冻结检查、两个 OpenSpec strict 和完整构建；验收后才勾选总表 3.6、3.7 与伴随变更 2.1—2.3。
2. 00 记录新的唯一共同起点，只释放 04a；不得同时释放 04b。
3. 04a 使用冻结的 `VideoAnalysisProvider`、`StreamSessionProvider`、流仓储端口和有界 domain，实现 V002、来源授权/映射、会话、事件、截图、恢复和停止生命周期。
4. 已派发任务/会话仅在供应商确认支持且返回确认后进入取消/停止终态；否则保持 UNKNOWN。事件/截图保存成功后才推进游标。
5. 真实协议一旦由 05 补齐，必须先由 02/00 对照 draft；不允许直接把当前未确认草案装配进生产。

## 未完成与回退

未完成：真实 GPU、视频格式/限额、provider source 映射、查询/停止能力、成果回存、数据库迁移、前端和端到端联调。它们继续阻断 05、06、07 与 RC。

回退优先保持 `WGAI_INFERENCE_MODE=disabled` 和旧执行守卫；不恢复旧本地算法。没有推送、部署、修改 `backend-master`/GPU 代码/历史数据，也没有归档 OpenSpec。
