# 第五轮 03 provider 集成验收

00 从共同起点 `e44041ec050974ee3f36655f6869fb96cf16faad` 串行核实并快进合入 03 最终交付 `214678f77cbf2f62b802592b1e6c008e01a09f17`。

## 结论

- 03 的 42 个交付路径均属于 client、config/provider、测试、remote-ai 部署模板或本包验收目录；未修改公共 domain/port/DTO、1.1.0 OpenAPI/provider draft/样例、迁移、前端或其他包实现。
- 42 项 Java 8 JUnit 独立复跑通过，class major 52。覆盖图片回归、上传视频、流启动/查询/事件/停止、严格字段和状态、有效空结果、批准地址、鉴权脱敏、一次性 POST、UNKNOWN 与停止确认。
- 公共契约独立复跑通过：4 份 OpenAPI、34 个正反 JSON、2 个 PNG 和跨字段检查。
- 完整后端构建通过；镜像 `wgai-integration-backend:round5-client-214678f`，摘要 `sha256:aec55ff71050fc04665290bcb4b5879683c82d485251698309acb5256d9c565a`，大小 1111459214 字节，Spring Boot layertools smoke 通过。
- 两个 OpenSpec strict 通过；Graphify 更新为 29541 节点/71838 边，保留 6 个既有 Vue 解析警告。
- 00 接受平台 3.6、3.7 与流变更 2.1—2.3。平台进度 27/48，流变更 5/21。

## 保留的外部阻断

05 尚未确认 RTX 5070 方法、TLS/CA、鉴权、视频/流样例、格式/限额、事件查询与停止能力。03 的草案 HTTP 机械实现没有注册为生产 remote；视频/流端口继续硬关闭。该集成验收不是实际 GPU 联调，不能完成 5.1、释放 05/06/07 或生成 RC。

浏览器仍只能提交本地 `streamSourceId`；不得提交 RTSP、GPU URL、凭据或 provider source ref。启动/停止响应丢失继续为 UNKNOWN，停止只有供应商对同一会话返回明确确认后才可进入 STOPPED。

## 证据

- `java8-tests.json`：00 独立测试回执与完整源码/日志哈希。
- `contract-checks.json`：00 独立契约回执。
- `review.json`：祖先、范围、镜像、OpenSpec、Graphify、门禁和下一包释放记录。
- 构建日志：`/Users/twowt88/Documents/ChatGPT/WGAI-parallel/00-integration/drafts/round5-client-backend-build.log`，SHA-256 `8c5d29d2af9871d09c4e45328fb0aa6a0e0876de47fc8769800d3d005acde856`。

下一步只释放 04a 从本次验收提交快进，实现 V002、视频任务成果及流来源/会话/事件/截图持久化、恢复和停止边界。04a 经 00 验收前不释放 04b。
