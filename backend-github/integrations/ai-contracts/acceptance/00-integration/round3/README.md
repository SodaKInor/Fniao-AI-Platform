# 第三轮串行集成验收

状态：**AWAITING_BROWSER_CAPTCHA_APPROVAL**。代码已按序合并，真实后端组合验收通过；页面联合验收尚未完成，不能宣称 4.7 通过或释放依赖工作。

## 合并与修复

| 阶段 | 完整提交 |
| --- | --- |
| 共同起点 | ab9809d23919ea5d61dfc7d8b34d7f30bb9d607c |
| 冻结契约 | 5a55ca5cc6ea8fde09898f44519d62c715af12db |
| 03 快进交付 | 2e74b32c438895895249f0da22ff37c591153d74 |
| 04a 交付 | 561c9dfd1a479dad4bef9ea1854fbd5cf1bc95b7 |
| 04a 保留历史合并 | 3a755156c5db995d6b3b09e9f657975ae8d84505 |
| 后端跨包修复与门禁 | 1040e85eade51fd73d1fcf949339012e7da192f5 |
| 04b 交付 | ce5671aac656077fc78b3877608a16b1173315b1 |
| 后端门禁通过后合入 04b | a7976296d14bdff47beb328702dbf975ced7348e |
| 实际响应契约装配修复 | d8b845b70bda3281ec6c5a8284ca36e2f5515b0f |

三包交付工作树干净；源码哈希分别核对 84/35/25 项，改动路径无交集，契约未改写。详见 preflight.json。跨包修复及原归属见 FIXES.md；只修改装配和测试缺口，不扩展恢复、取消或旧系统清理。

本记录的阶段验收提交完整 SHA 登记于代码树外的 00-integration/HANDOFF.md 和 WGAI-parallel/WORKSPACES.json，避免自身哈希循环。**阶段提交不是全部通过的统一放行提交。**

## 逐任务结论

| 任务 | 结论 | 实际证据 |
| --- | --- | --- |
| 3.1 | PASS | 03 的模式/旧 native 守卫测试；67 项组合 Java 测试；runtime-modes.json 实际 disabled、未确认 remote 不接收新任务且核心健康 |
| 3.2 | PASS | 03 的独立凭据/批准地址/CA/限额配置消费者和协议夹具，冻结检查；正式端点和协议仍未确认，不开放 remote 派发 |
| 3.3 | PASS | 03 协议夹具复跑；api-e2e.json 的空成果、PROVIDER_AUTH 与实际业务登录仍有效 |
| 3.4 | PENDING | 后端匿名/无权/旧执行直接请求守卫及保留管理查询通过；前端静态/16 项测试通过，但实际页面停用与菜单联合验收待验证码授权 |
| 3.5 | PASS | 有效能力装配、disabled/remote 配置缺失和绑定停用均实际拒绝；不宣称真实模型就绪 |
| 4.1 | PASS | migration.json：原 V001 重复执行、123 张非 AI 旧表结构和行摘要不变；随后只在 00 添加专用用户/角色/能力种子 |
| 4.2 | PASS | api-e2e.json/safety-e2e.json：真实上传、限额、四角色、跨用户输入/任务/成果拒绝，匿名旧静态路径不能读取新文件，目录700/文件600 |
| 4.3 | PASS | 实际 200/202、同 requestId 后台完成、持久状态事件；UNKNOWN 只派发一次；Java 队列/事务测试 |
| 4.4 | PASS | safety-e2e.json：六次并发提交同 ID、一次派发，不同参数冲突；终态重复提交返回原记录 |
| 4.5 | PASS | 实际文件大小和 SHA-256 与数据库及私有卷相同；回收断流三次后 FAILED/ARTIFACT_TRANSFER，无成果资产及残片；模式停用后历史可读；协议地址/重定向测试 |
| 4.6 | PASS（包内及集成静态门禁） | 同交付源码、冻结响应契约、16 项页面/API/轮询/Blob 测试、完整前端构建与定向 lint；实际浏览器联合操作仍属于未通过的4.7 |
| 4.7 | PENDING | 已打开真实前端登录页；验证码尚待用户授权。没有以 04b 内存业务 API 或接口测试冒充页面完整演示 |

总表只更新 10 个已验证任务；连同第一轮为 **21/41**。3.4 与4.7保持未完成。05/06/08 暂不同步到放行点，也不启动依赖工作；07继续等待。后续页面验收通过后才形成统一验收提交并登记放行边界。

## 证据解释

- java-tests.json：交付 29 + 33 项，以及新增 5 项装配/代理/认证错误回归，共67项通过。完整 Spring Boot 镜像实际启动、真实密码/验证码登录、Shiro 能力读取另见 spring-boot-smoke.json。
- api-e2e.json：只模拟供应商，其余为真实 Java/MySQL/Redis/登录与权限链；保留脱敏业务响应、requestId、状态事件、文件大小/哈希及派发次数。供应商错误不是业务401。
- safety-e2e.json：真实 HTTP 截断和 JSON 错误，不代替尚未完成的浏览器下载中断验收。
- runtime-modes.json：实际重启为 disabled 和未确认 remote，核心健康，既有历史/成果可读，新任务不持久化/不派发；完成后还原mock。
- frontend-checks.json/artifacts.json：16项测试、定向静态检查、真实前后端制品哈希。正式 JAR 不包含00故障注入类。既有构建脚本跳过测试，但本轮有独立执行的67项测试和完整应用启动验收，不能单凭打包记通过。
- architecture.json：106个AI Java源文件的导入边界和方法跨度检查。无新Java>400行/方法>80行；19个前端新模块无Vue>350行、JS>300行或方法>80行。旧文件的专项审查见下表，不扩展重写。
- live-contracts.json：真实Java响应逐条套用冻结JSON schema及HTTP/code一致性检查；包括匿名401的AI专用错误格式，不修改全局管理登录协议。
- menu-e2e.json：真实四账号菜单/权限响应，经实际前端导航模块转换后核对；无AI菜单账号不会得到新路由，viewer能力不可执行。不是浏览器操作证明。
- Graphify 已在00正确根路径执行AST更新；6个既有Vue解析警告，部分社区名以中心节点重新命名，未调用付费语义标注。共享Serena指向原工作区且服务初始化问题仍在，未切换项目或跨树写入。

| 旧文件触发项 | 专项结论 |
| --- | --- |
| ShiroConfig.shiroFilter 108行 | 原有路由清单仍保留；本轮只注入可选AI过滤器并调用独立链装配器，全局jwt不变 |
| TabAiHistoryServiceImpl 924行，startAi113/sendUrl88行 | 每个旧执行方法入口先拒绝，依赖访问前终止；直接方法调用测试覆盖；未拆分旧算法系统 |
| AITestController.testAIModel 88行 | 薄守卫位于读取模型/执行native前；路由和直接方法两层拒绝 |
| TabAiModelBundList.data 86行、TabEasyConfigList.data 95行（脚本跨度） | 原有表列/表单配置；本轮只移除执行调用或停用训练操作，管理查询/编辑保留 |

新页面只负责表单和呈现，API、轮询、导航和成果渲染分模块；后端控制器、应用流程、provider、持久化和私有存储没有跨层混写。

## 复现（仅限00）

在 `/Users/twowt88/Documents/ChatGPT/WGAI-parallel/00-integration/code` 执行，以下 `$R3` 只是命令缩写。依赖保留既有版本，无新增公共构建依赖。

```sh
R3=backend-github/integrations/ai-contracts/acceptance/00-integration/round3/scripts
node "$R3/preflight.cjs"
node "$R3/runtime.cjs" prepare
docker compose -f ../drafts/round3/compose.json up -d mysql redis
node "$R3/migration.cjs"
# 最终Java脚本使用00实例中的唯一临时schema，测试后撤销临时授权并删除该schema，不清空演示库。
python3 "$R3/java-tests.py"
node "$R3/seed.cjs"
docker build -f deploy/backend/Dockerfile -t wgai-integration-backend:round3 .
docker build -f deploy/frontend/Dockerfile -t wgai-integration-frontend:round3 .
node --test backend-github/integrations/ai-contracts/acceptance/04b-frontend/tests/*.test.cjs
node "$R3/frontend-evidence.cjs"
/Users/twowt88/Documents/ChatGPT/WGAI-parallel/02-contract/drafts/validation-venv/bin/python "$R3/contracts-architecture.py"
openspec validate remote-inference-platform --strict
node "$R3/fault-runtime.cjs"
docker compose -f ../drafts/round3/compose-acceptance.json up -d
node "$R3/http.cjs"
node "$R3/api-e2e.cjs"
node "$R3/runtime-modes.cjs"
node "$R3/safety-e2e.cjs"
node "$R3/menu-e2e.cjs"
/Users/twowt88/Documents/ChatGPT/WGAI-parallel/02-contract/drafts/validation-venv/bin/python "$R3/live-contracts.py"
graphify update .
python3 "$R3/artifacts.py"
```

前端测试需要本00工作树的 `frontend-vue/node_modules`，用仓库锁文件安装；不要复用原项目或他包的目录。frontend-evidence读取本轮真实构建/测试日志，复跑时先将相应输出保存到00/drafts/round3的同名日志。API脚本使用00自建账号和真实验证码登录；账号、token、秘密配置不会写入证据。故障类只从00私有目录挂载到验收启动配置，不属于应用运行包。

## 保留的本机演示与关闭方法

- 页面：<http://127.0.0.1:18100>，后端：<http://127.0.0.1:19100/jeecg-boot>；均只绑定回环地址。
- 私密账号：`/Users/twowt88/Documents/ChatGPT/WGAI-parallel/00-integration/drafts/round3/accounts.private.json`（600）。owner_a、owner_b具有执行权限，viewer无执行权限，nomenu无AI菜单且无执行权限。都不是管理员。
- 稳定演示使用 `../drafts/round3/compose.json`，mode=mock，独立MySQL/Redis/私有文件卷；最终不保留故障启动覆盖。登录验证码需通过真实页面输入，不默认绕过。
- 关闭00演示（保留全部卷/数据）：

```sh
docker compose -f /Users/twowt88/Documents/ChatGPT/WGAI-parallel/00-integration/drafts/round3/compose.json stop frontend backend mysql redis
```

不执行 `down -v`。原8080服务不受影响；未推送、未部署生产、未改原数据库/迁移/默认权限、未改他包HANDOFF、未归档整体变更。恢复/取消、真实GPU、05真实回归和正式发布仍未验收。
