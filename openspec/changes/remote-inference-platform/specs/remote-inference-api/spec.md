## Purpose

定义业务后端对接同事维护的外部 GPU 服务的稳定契约，使前端通过统一业务接口提交输入和获得成果，保持身份与服务凭据隔离，并在外部能力不完整或故障时提供准确的可用性和错误反馈。

## ADDED Requirements

### Requirement: Backend mediates inference access

本项目 SHALL 由业务后端验证用户身份和能力权限，再用独立服务凭据访问批准的外部地址。浏览器 SHALL 不获得服务凭据，不允许任意指定 GPU URL 或执行路径。

#### Scenario: Unauthorized request
- **WHEN** 未登录或无业务能力权限的用户提交请求
- **THEN** 后端拒绝请求且不调用外部服务

### Requirement: Local algorithms remain disabled in remote mode

系统 SHALL 在 disabled/mock/remote 模式的执行入口实施守卫。remote 模式 SHALL 不调用管理机旧本地算法；mock 成果 SHALL 明确标识为模拟数据。

#### Scenario: External service unavailable
- **WHEN** remote 模式下外部服务不可达
- **THEN** 显示服务不可用，不自动运行旧本地模型，历史成果和管理功能仍可访问

### Requirement: Provider contract is adapted to stable business output

系统 SHALL 按双方确认的输入、输出和错误契约与外部服务通信，再转换为稳定业务结果。返回版本信息存在时 SHALL 如实保存，不存在时 SHALL 标为未提供，不能推测。

#### Scenario: Colleague changes endpoint format behind an adapter
- **WHEN** 已确认的服务接口格式通过适配器更新且业务成果契约保持一致
- **THEN** 前端仍使用相同业务 API，历史记录保留原请求/适配配置和已知版本信息

#### Scenario: Invalid output schema
- **WHEN** 外部响应不符合约定的成果格式
- **THEN** 系统显示接口协议错误，不把无法解释的数据标记为成功成果

### Requirement: Provider capabilities are explicit

系统 SHALL 支持对接普通同步请求/返回接口。外部异步查询、取消、回调或请求去重 SHALL 仅在双方确认支持时启用，不能因本地存在任务 ID 而推定外部存在对应能力。

#### Scenario: Sync-only provider
- **WHEN** 同事仅提供一次请求返回成果的接口
- **THEN** 本项目可以在后台执行该调用并向前端返回本地任务 ID，同时不展示未提供的外部查询/取消能力

### Requirement: Errors preserve cause and user session

系统 SHALL 区分输入无效、业务无权限、服务不可达、外部处理失败、结果未确认和协议错误。GPU 服务凭据错误 SHALL 作为服务配置故障报告，不使业务用户退出登录。

#### Scenario: Provider credential rejected
- **WHEN** 外部服务拒绝后端凭据
- **THEN** 用户看到服务故障，自己的登录会话保持有效，管理员能按 requestId 定位错误

#### Scenario: Empty valid result
- **WHEN** 外部返回符合约定的空检测列表或其他有效空成果
- **THEN** 系统保存成功结果，不将其误报为模型故障

### Requirement: Service communication uses configured trust boundaries

系统 SHALL 仅访问批准的服务地址与成果下载地址，真实业务素材通过可验证的加密通道传输。凭据 SHALL 不进入普通用户响应与日志。

#### Scenario: Invalid certificate or unapproved artifact host
- **WHEN** HTTPS 验证失败或返回成果指向未经批准的下载主机
- **THEN** 系统拒绝传输/下载并报告连接或成果获取错误，不自动跳过校验

### Requirement: Remote integration has explicit module boundaries

新增远程接入代码 SHALL 按架构文档拆分接口、应用流程、领域规则、端口、外部适配、存储和持久化职责。前端 SHALL 分开页面编排、可复用组件、业务 API 和轮询生命周期。Controller 和页面 SHALL 不承担远程协议转换、数据库访问和整个任务流程；不得用单个大型文件容纳完整功能。

#### Scenario: New request-to-result capability is implemented
- **WHEN** 实施一个包含上传、调用、等待和成果展示的能力
- **THEN** 各职责位于相应模块，依赖方向符合架构约束，新增手写业务文件满足文件规模和单一职责检查

### Requirement: Parallel work uses isolated ownership and integration

并行工作包 SHALL 使用独立代码 worktree、分支和运行资源，并遵守文件归属与冻结契约。各包 SHALL 仅提交分配范围和验收证据，由集成任务统一验证、合并并更新整体完成状态。

#### Scenario: Client and frontend are implemented concurrently
- **WHEN** 两个对话分别开发远程客户端与前端页面
- **THEN** 它们从同一已集成契约版本开始，仅写入各自工作区，修改公共契约时先协调所有者，整体流程通过集成验收后才标记完成
