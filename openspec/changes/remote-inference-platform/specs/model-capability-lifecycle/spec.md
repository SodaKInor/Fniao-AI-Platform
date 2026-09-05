## Purpose

定义本项目对业务能力与外部服务绑定的管理，使旧本地模型入口能够逐项停用或迁移，业务用户看到的功能与真实对接能力一致，并在外部模型替换和旧代码清理后保留可解释的历史成果。

## ADDED Requirements

### Requirement: Business capability bindings hide GPU implementation

系统 SHALL 使用稳定业务能力标识映射外部接口及必要参数，保存启停状态和输入输出约束。用户 SHALL 不需要提供本地权重路径、算法框架或显卡配置。

#### Scenario: Production GPU service replaces development endpoint
- **WHEN** 管理员切换到符合相同业务契约的正式服务配置
- **THEN** 前端继续使用相同业务 API，后续请求记录所使用的服务绑定快照

### Requirement: Feature availability follows actual integration

系统 SHALL 根据本项目启停、用户权限、配置完整性以及实际可获得的外部健康信息判断可用性。前端隐藏入口之外，后端 SHALL 独立执行检查；外部不提供模型级就绪信息时 SHALL 不虚构“模型已就绪”。

#### Scenario: Direct call to disabled capability
- **WHEN** 用户绕过界面直接请求已停用的能力
- **THEN** 后端拒绝执行并返回停用原因

#### Scenario: Stub capability is available for development
- **WHEN** 开发环境显式启用 stub 并且对应契约夹具可用
- **THEN** 系统可以提供模拟业务入口，但必须向管理状态和结果元数据标明模拟来源，真实 provider 可用性仍保持未确认

#### Scenario: Stub is absent from production
- **WHEN** 正式配置未连接真实 provider
- **THEN** 对应能力显示未配置或不可用，不把开发 stub 状态继承为生产可用

### Requirement: Missing legacy implementations are not offered as runnable

系统 SHALL 对缺少目标后端实现、已从构建排除或明确淘汰的执行功能停用入口；保留管理查询 SHALL 不被描述为算法已可运行。

#### Scenario: Legacy training has no target backend API
- **WHEN** 用户打开对应功能区域
- **THEN** 界面显示停用原因或不提供执行入口，不发送必然缺失的训练请求

### Requirement: Legacy image and video actions migrate only to confirmed capabilities

旧图片与上传视频执行动作 SHALL 逐项映射至统一任务接口；实时视频来源仅在伴随流变更具有授权的本地 `streamSourceId` 且 provider 可映射时启用。任何缺少真实接口、输入/成果约束或来源映射的入口 SHALL 保持停用，不能回退到旧 Java 算法或在浏览器传输 RTSP 秘密。

#### Scenario: Old image action has a confirmed remote binding
- **WHEN** 旧图片业务已映射到启用的 `image-detection.v1` 绑定
- **THEN** 执行动作创建统一持久任务，不再调用旧本地模型

#### Scenario: Legacy camera lacks provider source identity
- **WHEN** 旧视频源没有可授权的 `streamSourceId` 或真实 provider 无法映射该来源
- **THEN** 页面和后端执行入口保持停用，不改成后端视频中继或提交明文 RTSP

### Requirement: Chat and training execution entries are retired

系统 SHALL 退役全部 MaxKB、tchat、easyAi 智能聊天执行入口及训练执行入口。前端菜单、旧页面动作和后端直接请求 SHALL 一致拒绝新执行；数据库历史、已有业务记录及与其他业务共用的管理能力 SHALL 保留。

#### Scenario: User calls a retired chat endpoint directly
- **WHEN** 已登录用户绕过菜单请求 MaxKB、tchat 或 easyAi 聊天执行接口
- **THEN** 后端拒绝执行，不转发到旧服务，也不删除历史会话数据

#### Scenario: User opens a retired training action
- **WHEN** 用户访问仍保留管理查询的训练区域
- **THEN** 系统不提供训练执行动作，并清楚说明该执行入口已退役

### Requirement: Retirement respects ownership and preserves history

系统 SHALL 在本项目停用业务绑定时阻止新请求，按已约定的查询/取消能力处理已有调用。清理本项目旧算法代码 SHALL 不删除历史成果，不自动触发同事 GPU 端模型/权重删除。

#### Scenario: Old local model code removed
- **WHEN** 旧入口已迁移或停用并清理本地算法依赖
- **THEN** 历史记录仍保留能力、输入参数和已知外部版本信息，成果仍可按保留策略访问

#### Scenario: Binding retired during external processing
- **WHEN** 停用的业务绑定仍有已发出的外部请求
- **THEN** 新请求被拒绝，已有调用按真实外部能力继续等待或请求取消，不伪称强制终止了同事服务

### Requirement: Cleanup is reference-driven and reversible by group

旧页面、执行代码和算法依赖 SHALL 仅在引用清单证明无保留调用者后分组清理。每组 SHALL 独立验证前后端构建、真实远程业务和历史成果读取；通用 WebSocket、播放器资产、管理 CRUD 或其他业务仍有引用时 SHALL 保留。

#### Scenario: Native dependency still has a non-retired caller
- **WHEN** OpenCV、ONNX Runtime、JavaCV、ASRT、RapidOCR、Tess4J 或相关脚本仍被保留业务引用
- **THEN** 该依赖不在本组删除，并在清理对照表记录调用者与后续处置

#### Scenario: Cleanup group passes all gates
- **WHEN** 一组旧入口或依赖经引用审查确认无调用者，且构建、真实远程流程和历史成果回归全部通过
- **THEN** 系统可提交该独立清理组，同时不修改 `backend-master`、GPU 源码、历史数据或 Vue/Java 大版本
