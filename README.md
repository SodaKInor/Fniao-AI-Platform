# Fniao AI Platform

这是可独立克隆、构建和部署的 Fniao AI Platform 模块化单体。业务后端负责向独立 GPU 服务发送请求、保存返回成果并提供给前端；GPU 服务由另一位开发者维护。

当前状态：本地 `simulated/stub` 与 `disabled` 发布候选已经完成；RTX 5070 局域网与单张扩容 48GB RTX 4090 的真实服务验收仍等待同事交付。

- [代码架构与约束](docs/remote-inference/ARCHITECTURE.md)
- [目录整合记录](docs/remote-inference/PARALLEL_PLAN.md)
- [文件归属](docs/remote-inference/FILE_OWNERSHIP.md)
- [本地运行说明](docs/remote-inference/README.md)
- [最终集成报告](docs/FINAL_INTEGRATION_REPORT.md)
- [分批任务与验收](openspec/changes/remote-inference-platform/tasks.md)
- [原始审计](openspec/changes/remote-inference-platform/audit.md)
- [源码基线（历史）](docs/remote-inference/REPOSITORY_BASELINE.md)

顶层运行边界为 `apps/backend`、`apps/frontend`、`database`、`remote-inference`、`deploy`、`docs`、`openspec` 和 `tools`。浏览器只访问业务后端；业务后端通过 provider HTTP 接口访问 GPU 服务。正式配置缺少真实服务资料时保持 disabled，不引用或回退开发 stub。

真实配置、数据库转储、上传素材、权重、私有制品和缓存不进入版本库。新环境按 [部署说明](deploy/README_DEPLOY.md) 把私有基线和制品放入被忽略的位置；原项目版权、许可证及来源信息保留在对应文件中。
