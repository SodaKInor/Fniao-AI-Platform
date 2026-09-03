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

### Requirement: Missing legacy implementations are not offered as runnable

系统 SHALL 对缺少目标后端实现、已从构建排除或明确淘汰的执行功能停用入口；保留管理查询 SHALL 不被描述为算法已可运行。

#### Scenario: Legacy training has no target backend API
- **WHEN** 用户打开对应功能区域
- **THEN** 界面显示停用原因或不提供执行入口，不发送必然缺失的训练请求

### Requirement: Retirement respects ownership and preserves history

系统 SHALL 在本项目停用业务绑定时阻止新请求，按已约定的查询/取消能力处理已有调用。清理本项目旧算法代码 SHALL 不删除历史成果，不自动触发同事 GPU 端模型/权重删除。

#### Scenario: Old local model code removed
- **WHEN** 旧入口已迁移或停用并清理本地算法依赖
- **THEN** 历史记录仍保留能力、输入参数和已知外部版本信息，成果仍可按保留策略访问

#### Scenario: Binding retired during external processing
- **WHEN** 停用的业务绑定仍有已发出的外部请求
- **THEN** 新请求被拒绝，已有调用按真实外部能力继续等待或请求取消，不伪称强制终止了同事服务
