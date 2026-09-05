# 远程 AI 改造工作入口

本项目负责业务前后端、向同事接口发送请求、保存和展示成果。GPU 端工程由同事独立维护。

- [代码架构与约束](ARCHITECTURE.md)：模块职责、依赖方向、文件规模与验收规则。
- [并行实施计划](PARALLEL_PLAN.md)：独立目录、依赖、合并与交接方式。
- [文件归属表](FILE_OWNERSHIP.md)：哪些文件由哪个工作包修改。
- [工作包登记](work-packages.json)：任务编号、分支、目录和前置关系。
- [可分次复制的新对话提示词](PROMPTS.md)：按“串行 1 → 并行 2A/2B → 串行 3”复制到对应新对话。
- [Git 基线说明](REPOSITORY_BASELINE.md)：跟踪范围、保留在本机的配置与制品。
- 总体设计与任务：项目根目录 openspec/changes/remote-inference-platform/。

最终集成目录为 `/Users/twowt88/Documents/ChatGPT/Fniao-AI-Platform`，当前分支为 `codex/final-layout`。阶段 A 在该目录串行执行；随后从同一 `08A_SHA` 在同级 `/Users/twowt88/Documents/ChatGPT/Fniao-AI-Platform-worktrees` 创建 database-layout 与 remote-boundary 两个工作树并行执行；最后回到最终目录串行合并和交付。

**目录存在不等于工作树正确。** 每个并行对话开始前必须核对 Git 根、分支、共同 `08A_SHA` 和干净状态。只合并已提交分支，禁止用复制、软链接、硬链接或整目录覆盖替代 Git 合并。01—07 的旧 WGAI 路径属于历史验收记录，不再作为第 8 批写入位置。
