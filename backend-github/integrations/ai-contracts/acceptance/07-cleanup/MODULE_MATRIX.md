# AI 功能模块映射与依赖矩阵

本清单以第 6 批本地 stub/disabled 验收提交 `722aad3` 为共同起点。模块迁移不改变
`/ai/v1` 契约、数据库表、历史资产或真实 provider 的 disabled 状态。

## 后端映射

| 功能根 | 归属 | 可依赖功能根 |
| --- | --- | --- |
| `capability` | 能力快照、查询、持久化 | `job` 的有界错误语义 |
| `asset` | 私有上传、授权读取、文件存储 | `image`/`video` 文件校验，`job` 错误语义 |
| `result` | 成果类型、供应商成果引用、成果读取端口 | `asset`、`capability`、`image`、`video`、`job` |
| `image` | 图片 DTO、领域类型、接口与 provider 端口 | `asset`、`capability`、`result`、`job` |
| `video` | 上传视频 DTO、领域类型、接口与 provider 端口 | `asset`、`capability`、`result`、`job` |
| `job` | 持久任务、幂等、派发、恢复、取消、成果编排 | `asset`、`capability`、`image`、`video`、`result` |
| `stream` | 来源、会话、事件、截图、停止、恢复 | `asset`、`capability`、`result`、`job` 的共享状态/错误；不依赖 provider 实现 |
| `provider` | remote/mock 适配器与 HTTP 传输 | 只实现各功能端口；功能代码不得反向导入 provider 实现 |
| `operations` | Spring 装配、worker、全局 API 错误映射和指标 | 可装配全部保留功能 |
| `legacy` | 旧入口拒绝守卫 | 仅使用 `job` 的有界错误语义 |

`api` 是功能入口的组合层，允许调用 `job` 工作流；`job.domain` 中的状态、确定性和错误码是
现有 `1.1.0` 公共语义。检查器除验证上述显式边外，还验证 Java 类导入图无环、保留功能不反向
导入 provider 适配器、`job` 不导入 `stream`，因此 stream 与 job/provider 不形成运行时循环。

## 前端目标映射

| 功能根 | 页面/API/组件 |
| --- | --- |
| `capability` | 能力 API 与能力面板 |
| `asset` | 上传 API、图片/视频上传组件 |
| `job` | 任务 API、轮询、状态、历史与详情 |
| `result` | 响应规范化、授权图片与成果预览 |
| `image` | 图片检测页、参数和检测渲染器 |
| `video` | 上传视频分析页、参数与时间线成果 |
| `stream` | 来源、会话、事件、截图、轮询与实时页 |
| `legacy` | 菜单过滤、旧入口停用页 |

前端不建立 `provider` 或 `operations` 目录：浏览器不知道 provider 地址/凭据，运行装配属于后端。
不创建 `audio`、`chat`、`training` 空模块。

## 串行迁移与回滚点

1. `380e76e`：退役旧聊天页面并拒绝直接 MaxKB 执行。
2. 后端功能分包：先移动类型与端口，再移动应用、持久化、provider 和装配；完整构建及运行回归后提交。
3. 前端功能分包：按 API → 服务 → 组件 → 页面/路由更新；单元测试、生产构建及运行回归后提交。
4. 旧本地算法和依赖：每组先查引用，只删除无调用者执行代码；历史表、实体、CRUD 和通用播放器保留。

机器可重复检查入口：`scripts/check-backend-modules.py`。其 JSON 输出记录实际文件数、依赖边和循环检查结果。
