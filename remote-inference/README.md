# AI 接口共同交付

当前冻结 contracts/business/v1.1/business.openapi.json（内部 1.1.0），并保留不可变的
contracts/business/v1/business.openapi.json（1.0.0 图片兼容基线）。外部图片草案
contracts/provider/v0.1.openapi.json 与视频/流扩展草案 contracts/provider/v0.2.openapi.json
均为 UNCONFIRMED，不是已上线接口。后续 03/04a/04b 必须从 00 记录的同一冻结提交开始，
不自行改公开字段或类型。

- [1.1 图片、上传视频和实时流语义](contracts/business/v1.1/SEMANTICS.md)
- [1.0 图片兼容基线](contracts/business/v1/SEMANTICS.md)
- [模拟夹具说明](fixtures/README.md)
- [开发服务交接模板](handoff/development.md)
- [正式服务交接模板](handoff/production.md)
- [本包检查与审阅](acceptance/02-contract/README.md)
- [来源、备份和环境基线](acceptance/01-foundation/README.md)

内部任务负责登录、用户归属、输入/成果、幂等和等待。外部提供方只通过已冻结 provider
端口接入；前端看不到服务凭据、路径、原始协议。同步提供方不必实现任务系统。图片查询、
取消、去重及视频/流查询、事件、停止、去重均未经确认；相关 feature flag 全为 false，
真实资料不全时对应能力保持 disabled。

API DTO 使用已有 Jackson annotations，未增加依赖。领域和端口使用 Java 8 标准库，可单独编译；
每个公开类型单文件且不使用无约束 Map。本包没有客户端、仓储实现、Controller、业务编排或
前端实现。实际实现必须执行 schema 中的限制与语义校验，声明本身不是运行时保护。

契约引用均为文档内 JSON Pointer，不依赖网络 schema。OpenAPI 校验器同时检查 1.0、1.1 和
两份 provider 草案，并验证 manifest 的正反样例。私有制品和构建输入依照 01 的复现步骤取得，
不复制真实配置。

当前目录是契约、fixtures、独立开发 stub、历史验收证据和接口交接材料的唯一版本化来源。
运行 `node validate-boundary.cjs` 验证契约/fixtures、目录边界、活动链接和模拟/正式配置隔离。
