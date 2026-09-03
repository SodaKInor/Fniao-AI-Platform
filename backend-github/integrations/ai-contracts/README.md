# AI 接口共同交付

本轮交付 `v1/business.openapi.json`（内部 1.0.0）与 `provider-draft/v0.1.openapi.json`（外部未确认草案），不是已上线接口。后续 03/04a/04b 必须从 00 记录的同一冻结提交开始，不自行改公开字段/类型。

- [业务状态、身份、流和仓储语义](v1/SEMANTICS.md)
- [模拟夹具说明](examples/README.md)
- [开发服务交接模板](handoff/development.md)
- [正式服务交接模板](handoff/production.md)
- [本包检查与审阅](acceptance/02-contract/README.md)
- [来源、备份和环境基线](acceptance/01-foundation/README.md)

内部任务负责登录、用户归属、输入/成果、幂等和等待。外部提供方只通过 InferenceProvider / ProviderArtifactReader 接入；前端看不到服务凭据、路径、原始协议。同步提供方不必实现任务系统。外部查询/取消/去重未经确认均为 false；本版本端口只提供同步 infer 和受控成果读取。

API DTO 使用已有 Jackson annotations，未增加依赖。领域和端口使用 Java 8 标准库，可单独编译；每个公开类型单文件。本包没有客户端、仓储、Controller、业务编排或前端实现。实际实现必须执行 schema 中的限制与语义校验，声明本身不是运行时保护。

契约引用为内部 JSON Pointer，无依赖网络的 schema 引用；用 OpenAPI 校验器检查完整定义，并验证 manifest 列出的正反样例。私有制品和构建输入依照 01 的复现步骤取得，不复制真实配置。
