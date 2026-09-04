# 07-cleanup 分组验收

本目录记录从 `722aad3`（第 6.5 轮本地模拟验收提交）开始的串行清理证据。模拟服务只用于
验证本地业务和故障语义；RTX 5070/4090、真实视频与 RTSP 来源、远程取消/停止仍未验证，
不得据此标记真实联调完成。

## 第一组：菜单、旧页面与聊天入口

- 删除 MaxKB、tchat、easyAi 的 59 个旧前端页面；动态菜单统一解析为停用说明页，不再尝试
  加载已删除组件。
- 停用 MaxKB 的外部连接测试动作，匿名请求返回 401，授权请求返回
  `409 / CAPABILITY_UNAVAILABLE`；MaxKB、聊天和 easyAi 的管理表、CRUD 与历史行保留。
- 前端状态机 27/27、Java 8 权限与契约 43/43、OpenSpec 严格校验 2/2 通过。
- 前端生产镜像与后端 Java 8 `prod,docker-core` 八模块镜像构建通过。两制品装入隔离的
  18100/19100 环境后，四账号登录、图片、上传视频、实时事件、成果下载、历史与跨用户拒绝通过。
- 证据见 `group1-runtime.actual.json`、`group1-retirement.actual.json` 和
  `group1-build.actual.json`。

## 第二组 A：后端功能模块

- 将 182 个生产 AI Java 文件从旧的横向技术层目录迁入 capability、asset、job、result、image、
  video、stream、provider、operations、legacy 十个功能根，功能内继续保留必要分层。
- 独立模块检查确认旧技术层根无 Java 文件、无 audio/chat/training 空模块、类导入图无环、功能代码
  不反向导入 provider 适配器，job 不导入 stream。
- Java 8 回归 43/43、83 个公共契约类型、八模块后端构建、OpenSpec strict 2/2 通过。第一次实启发现
  视频控制器不可代理后已修复；最终镜像健康启动，图片/视频/流/成果下载/历史/跨用户拒绝再次通过。
- 证据见 `MODULE_MATRIX.md`、`group2-backend-modules.actual.json`、
  `group2-backend-build.actual.json` 和 `group2-backend-runtime.actual.json`。

## 第二组 B：前端功能模块

- 将 31 个生产 AI 前端文件从 api、components、services、views 四套横向目录迁入 asset、capability、
  image、job、legacy、result、stream、video 八个功能根，并由模块根入口统一导出公共 API。
- 独立模块检查确认旧横向目录没有活动文件、没有 audio/chat/training/provider/operations 空模块、局部
  import 均可解析且无文件级环，六个动态菜单目标均指向实际文件。
- 前端行为回归 27/27、局部 ESLint、本机生产构建和容器镜像构建通过。镜像装入隔离环境后，使用测试
  账号在浏览器中逐项打开图片、上传视频、实时事件、历史、任务详情和流会话详情；模拟标识、时间线、
  截图入口和停止终态均正常显示。
- 证据见 `group2-frontend-modules.actual.json`、`group2-frontend-build.actual.json`、
  `group2-frontend-runtime.actual.json` 和 `group2-frontend-browser.actual.json`。

## 第三组：旧本地执行与训练入口

- 删除 31 个后端本地图片/视频/OCR/训练执行文件与 99 个对应旧前端页面；旧模型绑定新增动作不再
  下载外部图片，通用 WebSocket 不再在连接时启动 RTSP/本地识别线程。
- 六个遗留执行入口对匿名访问返回 401、对已登录访问返回
  `409 / CAPABILITY_UNAVAILABLE`；模型、绑定、订阅、基础配置和历史管理 CRUD 及数据库表行保留。
- 用户明确要求为后续新目录重构保留可复用依赖，因此 OpenCV、ONNX Runtime、JavaCV、ASRT、
  RapidOCR、Tess4J、私有 JAR 安装/Java 8 OpenCV 重编译脚本及播放器制品均未删除；生产 Java
  已无这些本地算法的活动引用。
- Java 8 测试 60/60、前端行为 27/27、前后端生产构建与镜像构建、本地 simulated
  图片/上传视频/流/截图/停止/历史/跨用户拒绝均通过。真实供应商仍未验证。
- 证据见 `group3-legacy-execution.actual.json`、`group3-direct-retirement.actual.json`、
  `group3-java8-tests.actual.json`、`group3-backend-build.actual.json`、
  `group3-frontend-build.actual.json` 和 `group3-runtime.actual.json`。

本轮到此不继续全项目目录重构；最终集成只生成明确标注 simulated/disabled 的本地候选与新目录重构
HANDOFF，不把真实 GPU/RTSP 未知项标记为成功。
