# Fniao AI Platform

在现有 WGAI 前后端基础上进行二次开发。业务后端负责向独立 GPU 服务发送请求、保存返回成果并提供给前端；GPU 服务由另一位开发者维护。

当前状态：现有源码基线与改造计划，远程接入业务尚未实现。

- [代码架构与约束](backend-github/development/remote-inference/ARCHITECTURE.md)
- [并行工作包与依赖](backend-github/development/remote-inference/PARALLEL_PLAN.md)
- [文件归属](backend-github/development/remote-inference/FILE_OWNERSHIP.md)
- [六轮新对话提示词](backend-github/development/remote-inference/PROMPTS.md)
- [分批任务与验收](openspec/changes/remote-inference-platform/tasks.md)
- [原始审计](openspec/changes/remote-inference-platform/audit.md)
- [源码基线与本地配置说明](backend-github/development/remote-inference/REPOSITORY_BASELINE.md)

先准备基线和公共契约，再并行开发客户端、任务/资产后端和前端，由集成任务统一验收。真实局域网联调和正式服务器验收分别进行。

原 backend-master 仅作本地只读参考；真实配置、数据库转储、上传素材、权重、私有制品和缓存未包含在仓库中。新环境按基线说明补齐必要的本地配置与制品。原项目的版权、许可证及来源信息保留在对应文件中。
