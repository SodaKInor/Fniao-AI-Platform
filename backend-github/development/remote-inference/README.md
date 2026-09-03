# 远程 AI 改造工作入口

本项目负责业务前后端、向同事接口发送请求、保存和展示成果。GPU 端工程由同事独立维护。

- [代码架构与约束](ARCHITECTURE.md)：模块职责、依赖方向、文件规模与验收规则。
- [并行实施计划](PARALLEL_PLAN.md)：独立目录、依赖、合并与交接方式。
- [文件归属表](FILE_OWNERSHIP.md)：哪些文件由哪个工作包修改。
- [工作包登记](work-packages.json)：任务编号、分支、目录和前置关系。
- [六轮新对话提示词](PROMPTS.md)：按轮次和工作包复制到对应新对话。
- [Git 基线说明](REPOSITORY_BASELINE.md)：跟踪范围、保留在本机的配置与制品。
- 总体设计与任务：项目根目录 openspec/changes/remote-inference-platform/。

本地任务入口位于 /Users/twowt88/Documents/ChatGPT/WGAI-parallel/。每个目录包含 START_HERE.md、AGENTS.md、TASKS.md 和 HANDOFF.md；实际隔离代码工作区应位于各目录的 code/。

**任务文件夹已准备好不等于 Git worktree 已创建，也不代表业务已实现。** 以本地 WORKSPACES.json 的状态和实际 git worktree list 为准。Git 基线尚未建立时，先执行 01-foundation；其他包可以整理自己目录下的草案，但不能改原始 WGAI 源码。禁止把原项目代码软链接或硬链接到多个目录冒充隔离。
