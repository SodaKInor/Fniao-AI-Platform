# 并行文件归属

基准路径为每个工作包自己的 code/。后端新模块的简称 AI_ROOT 指 backend-github/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/ai/。相应 src/test 下的测试按同一功能划分；不得通过测试目录反向修改他包实现。

| 区域/文件 | 首期所有者 | 约束 |
|---|---|---|
| Git 基线、忽略规则、来源、备份及工作区创建 | 01-foundation | 建立基线时可在原 WGAI 处理必要仓库元数据；之后各包均用 worktree |
| 集成分支、OpenSpec 总表、总体架构、构建依赖/锁文件及公共装配冲突 | 00-integration | 核实交接再合并；不代替同事开发 GPU |
| integrations/ai-contracts 契约、样例；AI_ROOT/domain、port、api/dto | 02-contract | 2.5 冻结公共类型；后续包提交变更请求，不私自改签名 |
| AI_ROOT/client/、config/provider/ | 03-client | 供应商协议、网络、凭据、响应转换 |
| AI_ROOT/application/capabilities/、api/controller/CapabilityController.java、api/mapper/capabilities/ | 03-client | 依赖冻结的 CapabilityRepository 端口；不能自行实现第二套能力实体 |
| 旧后端执行守卫、Shiro AI 入口、相关能力配置 | 03-client | 先按原审计定位具体文件；仅修改本次 AI 边界 |
| AI_ROOT/application/jobs/、application/assets/、storage/、persistence/、config/jobs/ | 04a-assets-jobs | 状态、归属、去重、成果存储、仓储实现 |
| AI_ROOT/api/controller 的任务/资产/提交 Controller，api/mapper/jobs/、api/mapper/assets/ | 04a-assets-jobs | 不修改 CapabilityController、公共 DTO 或供应商适配器 |
| backend-github/deploy/remote-ai/migrations/ | 04a-assets-jobs | 唯一迁移编号分配者，已有迁移不改写 |
| frontend-vue/src/api/ai、services/ai、components/ai、views/ai | 04b-frontend | 页面、组件、业务 API、轮询；不得编辑后端 |
| 旧前端入口的停用、AI 菜单/路由接入 | 04b-frontend | 是总任务 3.4 的前端配合部分；3.4 需后端和前端均验收后才能勾选 |
| backend-github/deploy/remote-ai/ 的开发联调模板与实测记录 | 05-lan | 与 03 初始配置分阶段交接；真实协议变化走 02，客户端实现问题交 03 或集成分派 |
| application/jobs 的恢复/取消、config/jobs 的运行配置 | 06-resilience | 先合入 04a，再交接归属；不与 04a 同时改同一文件 |
| 无用算法/依赖和其余旧入口 | 07-cleanup | 05、06 通过后接管明确清单；引用审查后分组清理 |
| backend-github/deploy/remote-ai/ 的正式发布资料与正式验收 | 08-release | 可提前写自己的发布草案；正式配置在 05 交付后接管，共享模板不并行改 |
| integrations/ai-contracts/acceptance/<包名>/ | 对应包 | 包之间使用不同子目录 |
| 各目录 HANDOFF.md 和 drafts/ | 对应包 | 保存本包进度/草案，不直接改他包记录 |

所有者表是首期分工，不改变 Git 的权限模型。实际合并由 00 检查变更路径；越界变更必须先明确重新分配，不可写完后以“顺手修复”为由合并。

公共文件（pom.xml、package.json、锁文件、共同配置、root AGENTS.md、OpenSpec 总表）由 00 统一集成。包需要新增依赖时在 HANDOFF.md 记录必要性和最小补丁，由集成应用并让相关分支同步，不同时升级依赖。

特别注意 05/06 可同时推进不同内容，但不是默认允许修改同一 client、配置或任务文件。真实联调发现实现问题时先分配修复所有者；完成并提交后才由另一个包继续使用。

