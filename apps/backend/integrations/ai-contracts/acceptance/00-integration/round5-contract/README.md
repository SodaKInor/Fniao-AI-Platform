# 第五轮 1.1 契约集成验收

00 已从规划提交 `d31489415b21032f3d08b9a0ef94a85813a8ec05` 验证并快进合入
02 交付 `1177de8be45123d043d7cb26b845ee9d94c26784`。本记录只放行新的公共契约起点，
不把真实 GPU 或任何运行时视频/流能力标为完成。

## 结论

- 文件归属通过：82 个交付文件全部位于 02 的四个允许根，未改构建依赖、数据库、Controller、
  前端、其他包验收记录或 `backend-master`。
- 业务契约升至 `1.1.0`；原图片端点和有效 JSON 对 1.1 再验证通过。新增上传视频任务、文件
  取消及五个实时流端点，所有输入/状态/事件/结果/未知原因均为有界类型。
- 浏览器契约拒绝 RTSP、GPU URL、provider source ID 和凭据；流来源只暴露本地
  `streamSourceId`。供应商 v0.2 仍为 `.invalid`/`UNCONFIRMED`，全部特性为 false，代码中也
  没有新增运行时视频/流 Controller，因此所有来源继续 disabled。
- 独立复跑 4 份 OpenAPI、34 个 JSON、2 个 PNG、83 个 Java 8 类型均通过；完整后端 Maven
  reactor 构建成功，生成本地镜像
  `wgai-integration-backend:round5-contract-1177de8@sha256:abdcf2ee71cea368883f7dfbdb2be6f433ac7be4c2d25c3336452d84d337cfa6`。
- 已验收平台任务 2.6、2.7 和伴随流任务 1.1、1.2。新的唯一共同起点 SHA 在本提交完成后写入
  外层 `00-integration/HANDOFF.md` 与 `WORKSPACES.json`；03 只能从该 SHA 快进。

## 保留门禁

真实方法、路径、TLS/CA、鉴权、图片/视频/流样例、错误确定性、限额、查询、取消、事件和停止
能力均未确认。05 的 5.1 保持未完成；未确认项不得通过草案、端口可达或模拟样例替代。

因此本次验收不释放 04a、04b、05、06 或 07，也不生成 RC。下一步仅串行重开 03；若 provider
无法满足本地 source 映射或故障语义，应回到 02/00 修订契约，而不是暴露明文 RTSP、建设未批准
的后端中继或透明重试 POST。
