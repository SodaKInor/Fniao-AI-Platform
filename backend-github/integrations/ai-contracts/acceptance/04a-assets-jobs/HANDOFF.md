# 04a-assets-jobs 交接

状态：READY_FOR_INTEGRATION（4.1—4.5 包内验收完成，等待 00 集成）。

- 工作树：/Users/twowt88/Documents/ChatGPT/WGAI-parallel/04a-assets-jobs/code
- 分支：work/remote-inference/04a-assets-jobs
- 原源码基线：8c7fa382a344e82eb13828d53b9fd9e018a5a461
- 共同集成起点：ab9809d23919ea5d61dfc7d8b34d7f30bb9d607c
- 冻结契约：5a55ca5cc6ea8fde09898f44519d62c715af12db；内部业务 API 1.0.0，外部协议仍未真实确认。
- 03、04b 与 00 的共同祖先及冻结内容已再次核对，见 scope-check.json。

## 本包完成内容

- 4.1：五张新增表与增量迁移；三个仓储端口实现，归属、不可变请求快照、token/version、容量、状态事件同事务；迁移重复执行后 123 张旧表的结构/数据摘要不变。
- 4.2：私有上传、权限和授权下载；有界读写、PNG/JPEG 签名/完整性及解码预算、SHA-256、临时文件清理、原子发布；旧匿名路径无法读取新文件。
- 4.3：受理前保存身份和输入；数据库限额加有界执行器；/infer 短等待与 /jobs 受理共用本地记录；后台只等待原调用，UNKNOWN 不自动派发。
- 4.4：规范摘要与数据库 owner/key 唯一性；跨接口并发去重、不同输入冲突、停用后原记录查询、明确新尝试关联旧 UNKNOWN。
- 4.5：保存标准化成果检查点；通过冻结 reader 收集最多三次；检查长度、哈希、期限及结构，完整文件和资产元数据均提交后才成功；历史不依赖供应商在线。

输入保留 7 天、成果 30 天，可配置，不加入自动删除任务。取消只完成冻结仓储原子方法；未实现取消 API、6.x 恢复流程或 4.7。

## 代码提交（按依赖顺序合并）

- `4451cb4d70b4c5733f1e619636f0b9919f3ebd8d` feat(ai): persist assets and atomic inference job state
- `43d597e52812542c43f6a0dbc8aec265b3276f93` feat(ai): store bounded private assets with integrity checks
- `669e8bcc0238d7e6127b8420fc5460b5196d1aee` feat(ai): dispatch durable jobs and expose private result APIs

测试与本交接材料在独立验收提交中；最终交付提交同时记录在工作包入口 HANDOFF.md。完整改动路径见 scope-check.json。

## 验收证据

README.md 为复现索引；verification.json、junit.txt、migration-checks.json、layer-checks.json、scope-check.json、graphify-checks.json 分别记录运行测试、迁移、分层、归属和索引证据。测试涵盖真实 MySQL 并发与事务、Spring 装配、后台线程、现有 Shiro 身份下的接口权限、成果异常和落盘。

新增业务 Java 35 文件，最大 129 行；domain/port/api DTO 保持冻结。没有修改前端、旧后端、公共 POM/配置或 OpenSpec 总表。数据库迁移仅为本包新增 V001 文件。

## 集成所需装配与限制

- 00 先应用新增迁移，并为 `/data/ai-private`（或配置替代位置）准备本包独立持久私有目录/卷；不得挂在旧上传、webapp 或静态路径下。
- 03 提供 InferenceProvider 与 ProviderArtifactReader Bean，并确认其 providerKey/adapterId 与能力描述匹配。能力仓储只读本地绑定；本包没有在正式配置中安装测试替身或自动启用未知能力。配置与绑定字段见 README。
- 当前能力描述和配置中更严格的输出限额传递给 reader 和存储；外呼/传输超时与 URL/DNS/TLS/重定向实现归 03。
- 权限测试用真实 Shiro Subject/LoginUser 类型及受控测试身份，不冒充真实令牌签发/验证链或 03 执行权限守卫验收。
- reader 拒绝来源/重定向的测试验证本包错误处理；不冒充 HTTP 地址策略实测。没有真实 GPU、前端页面组合、正式接入验收；4.7 交 00。
- 数据库无法确认元数据提交时不会删除可能已被引用的文件。进程崩溃后的在途状态对账和孤儿处理属于 06 恢复工作，本包不宣称完成 6.1。
- Graphify 在本 worktree 更新；保留六个旧 Vue 解析警告。Serena 指向原 WGAI，仅检查服务说明，没有切换共享项目或跨工作树写入。

## 回退与后续

代码可按相反提交顺序追加 revert，保留新增数据库表与私有文件。与当前数据兼容的回退配置保持远程执行守卫，不恢复旧本地算法，不清空历史数据、不重跑初始化清理 SQL。运行测试只使用本包副本，结束后停止本包测试 MySQL，保留其独立卷。未推送、未归档整体变更；由 00 核实并更新总任务勾选。
