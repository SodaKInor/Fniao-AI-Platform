# 远程推理工作入口

本项目负责业务前后端、provider HTTP 适配、成果持久化和展示；GPU 服务、模型、权重、CUDA 与驱动由同事独立维护。

- [代码架构与约束](ARCHITECTURE.md)
- [文件归属与边界](FILE_OWNERSHIP.md)
- [第 8 批施工记录](PARALLEL_PLAN.md)
- [本地与正式运行](../../deploy/remote-inference/README.md)
- [契约与 fixtures](../../remote-inference/README.md)
- [最终集成报告](../FINAL_INTEGRATION_REPORT.md)
- [OpenSpec 总任务](../../openspec/changes/remote-inference-platform/tasks.md)
- [实时流任务](../../openspec/changes/remote-video-streaming/tasks.md)

当前本地 RC 只证明 `simulated/stub` 与 `disabled`。正式 Compose 默认加载 remote 配置，但模式为 `disabled`，不包含 stub 服务，也没有真实 GPU 地址或凭据。只有显式叠加开发 stub profile 才会调用 `remote-inference/stub`。

第 8 批提示词、临时工作树路径与 01—07 验收记录已经冻结为历史施工证据。它们不是活动运行入口，不得据此重新使用旧 WGAI、旧应用目录或临时工作树。
