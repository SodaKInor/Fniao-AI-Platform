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

后续组继续执行本地执行/训练代码清理与零调用者原生算法依赖清理；每组均重新构建并回归。

## 第二组 A：后端功能模块

- 将 182 个生产 AI Java 文件从旧的横向技术层目录迁入 capability、asset、job、result、image、
  video、stream、provider、operations、legacy 十个功能根，功能内继续保留必要分层。
- 独立模块检查确认旧技术层根无 Java 文件、无 audio/chat/training 空模块、类导入图无环、功能代码
  不反向导入 provider 适配器，job 不导入 stream。
- Java 8 回归 43/43、83 个公共契约类型、八模块后端构建、OpenSpec strict 2/2 通过。第一次实启发现
  视频控制器不可代理后已修复；最终镜像健康启动，图片/视频/流/成果下载/历史/跨用户拒绝再次通过。
- 证据见 `MODULE_MATRIX.md`、`group2-backend-modules.actual.json`、
  `group2-backend-build.actual.json` 和 `group2-backend-runtime.actual.json`。
