# 00 串行集成修复登记

本目录由 00 负责；不改写 03/04a/04b 的交付记录。业务 API 1.0.0、公共 DTO、领域与端口保持冻结。

| 修复 | 原归属 | 00 装配理由与验证 |
| --- | --- | --- |
| API CapabilityMapper 显式命名 aiCapabilityDtoMapper | 03 | 与 04a MyBatis mapper 同名；组合上下文验证两者可注入 |
| CapabilityQueryService 暴露有效能力视图，SubmissionCapabilities 在 config/jobs 组装 | 03 + 04a | 新提交遵循实际模式、绑定和有效限额；重复 key 先返回既有任务；application 不引用 client/config |
| Asset/Inference/JobController 允许 CGLIB 代理 | 04a | 完整宿主启动暴露 DictAspect 对 final 控制器无法代理；以真实 DictAspect 回归测试覆盖 |
| DbFixture 接受显式验收 JDBC；无 provider 策略时拒绝提交 | 04a 测试 | 仅使用 00 独立数据库，失败关闭与组合装配一致 |
| AiJwtFilter 及可选装配 | 03 + 00共享Shiro装配 | 真实HTTP匿名401原为旧text/html包装；保持原JWT/realm认证，只对AI链使用冻结JSON错误，旧管理全局jwt不变 |
| AiJwtFilter/AiAccessFilter/CapabilityController 错误显式填写 simulated=false | 03 + 00 | 实际响应schema检查发现鉴权/能力基础错误遗漏必填字段；仅补齐已有DTO字段，不改冻结类型 |

初次后端门禁：66 项 Java 测试、完整镜像构建、实际 Spring Boot 启动、真实密码/验证码登录与 Shiro 能力读取通过；不是以跳过测试的打包替代测试。控制器原始启动失败已修复后重建并复测。最终补充AI匿名/失效登录错误包装后为67项，真实JSON响应另经live-contracts校验。任务状态以README和总表为准。

迁移门禁：原 V001 在 00 执行两次；123 张非 AI 旧表结构与行摘要不变。验收用户/角色是在迁移比对之后显式添加到 00，未向原数据库或普通用户授予权限。

首次66项测试使用00独立AI表；最终67项回归改为00 MySQL实例中的唯一临时schema，应用原V001并给予已有foundation用户临时schema权限，结束时撤销该权限并仅删除本次创建的schema，保留已生成的演示任务和文件。

验收脚本和故障注入只归属此目录；凭据、原始日志、运行配置保存在 00/drafts/round3，不提交。首次运行配置缺少私有 .env 的问题已纠正为复制原 00 私有配置，不影响原服务。
