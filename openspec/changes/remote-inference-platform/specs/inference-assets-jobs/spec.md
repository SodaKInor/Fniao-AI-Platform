## Purpose

定义本项目在请求外部推理服务时对输入资产、调用记录和返回成果的管理行为，确保访问权限一致、成果可长期查看，并根据对方实际提供的查询与取消能力准确处理等待、断线、重试及恢复。

## ADDED Requirements

### Requirement: Assets are private authorized resources

系统 SHALL 用不透明资产 ID 管理输入和输出，保存归属、类型、大小与内容哈希。访问 SHALL 校验权限，新 AI 私有资产 SHALL 不能经旧匿名静态路径绕过授权。

#### Scenario: Cross-user asset reference
- **WHEN** 用户引用或下载无权访问的资产
- **THEN** 系统拒绝请求，不传出或返回该文件内容

### Requirement: Transfer is bounded and output is persisted

系统 SHALL 按约定校验媒体和限额，以有界内存传输文件。收到成果后 SHALL 在本地保存结构化结果及文件并检查传输完整性；对方提供长度/校验值时 SHALL 核对。

#### Scenario: Temporary provider output
- **WHEN** 外部返回有有效期的成果下载引用
- **THEN** 本项目在约定窗口内受控取回成果，成功后使用本地资产提供历史查看，不将临时 URL 当永久成果

#### Scenario: Result transfer interrupted
- **WHEN** 成果下载未完成或已知校验值不匹配
- **THEN** 记录获取中或明确错误，不提前将任务标记为成功

### Requirement: Accepted calls have durable local identity

系统 SHALL 在向前端返回已受理前保存调用 ID、归属、输入、参数和服务配置快照。短等待超出预算 SHALL 返回同一记录的 202；刷新页面 SHALL 不重新提交。

#### Scenario: Long synchronous external call
- **WHEN** 外部同步调用仍在处理且前端短等待预算到期
- **THEN** 前端得到本地任务 ID 并查询本项目记录，后台继续原调用，不重新向外部提交

### Requirement: Local duplicates and remote retries are distinct

系统 SHALL 对同用户的幂等 key 与规范化请求摘要做本地去重。同 key 不同请求 SHALL 返回冲突；已经发出的外部请求 SHALL 仅在确认重试安全或对方明确支持幂等时自动重发。

#### Scenario: Duplicate browser submission
- **WHEN** 用户用同 key 重复提交相同输入
- **THEN** 返回已有本地调用记录，不创建第二条自动执行请求

#### Scenario: Response lost without external deduplication
- **WHEN** 请求已经发送、响应丢失且对方未确认去重能力
- **THEN** 系统保留结果未确认状态，不自动再次执行外部推理

### Requirement: Recovery reflects provider observability

系统 SHALL 恢复本地未派发任务；对已派发但结果未知的调用，仅在外部具备约定查询能力时自动对账，否则 SHALL 保留 UNKNOWN 及原因。新的显式尝试 SHALL 保留原记录关联。

#### Scenario: Backend restart with a sync-only provider
- **WHEN** 后端在等待外部同步响应时重启，且外部无查询接口
- **THEN** 恢复后记录仍可见，状态为结果未确认，不伪称恢复了外部执行结果

#### Scenario: Backend restart with external task lookup
- **WHEN** 外部提供已约定的任务查询且外部任务 ID 已持久保存
- **THEN** 后端恢复后查询原任务并按实际返回更新本地记录

### Requirement: Cancellation must not imply unsupported GPU control

系统 SHALL 可取消未派发的本地任务。已发送任务仅在外部支持取消时发起取消并等待确认；否则 SHALL 明确告知无法停止外部处理，关闭页面或终止等待不能标为已取消执行。

#### Scenario: Cancellation is unsupported remotely
- **WHEN** 用户尝试停止已发出的请求而对方无取消接口
- **THEN** 系统显示不支持停止外部处理，不把任务标记为 GPU 已停止

#### Scenario: Queued local request cancelled
- **WHEN** 有权限用户取消尚未派发的本地任务
- **THEN** 任务标记为已取消，且不再向外部发送

### Requirement: Completed results survive provider unavailability

系统 SHALL 在成果本地保存完成后标记 SUCCEEDED，并为任务查询、成果下载和取消应用一致的归属权限。历史成果在自身保留期内 SHALL 不依赖 GPU 服务持续在线。

#### Scenario: GPU service offline after success
- **WHEN** 外部服务在成果回存完成后下线
- **THEN** 有权限的用户仍可查看记录与下载本地成果
