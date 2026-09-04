# 第五轮 05 真实联调前置审计

05 已从 00 的前端统一验收提交
`b23f2fc8c5d1911af61dd0f55ad6a89d73c0d09d` 纯快进，02→03→04a→04b 的串行代码前置
已经满足。当前状态仍是 `WAITING_EXTERNAL`：没有取得可验证的 RTX 5070 开发服务契约或
运行输入，因此 5.1—5.4 全部保持未完成。

## 已核实

- 当前进程环境没有任何约定的 WGAI/AI/provider/GPU/RTX 配置变量名。
- 05 外层只有准备模板；地址仍为 `.invalid`，token/CA 仅是库外占位路径。
- 供应商 v0.1/v0.2 均明确为 `UNCONFIRMED`；视频提交、会话查询、事件查询、停止和供应商去重
  全部保持 false。
- 生产装配没有 remote provider Bean：图片 remote 无实例时拒绝，视频/流 remote 固定为 null，
  能力查询继续返回协议未确认。即使填入地址和凭据也不会误启用草案。
- 18106/19106 在审计时没有监听。本次未启动独立运行环境，未向局域网地址发请求。

## 解除阻塞所需的最小输入

需要实际服务提供方给出：开发 base URL 与路径/方法、TLS/CA、鉴权方式和库外凭据引用；图片、
MP4/H.264、已登记 source ID 的真实请求/成功/空/错误样例；输入与输出限额、并发、成果有效期；
请求身份和错误确定性；视频成果获取；流启动、会话/事件查询、截图和停止确认语义。若流来源
不能映射为服务端登记 ID，应停止实施并修订契约，不能回退到浏览器明文 RTSP 或后端中继。

这些资料到齐后，先冻结实际 provider 适配记录并补齐 03 所有者范围的 remote 装配，再从 05
实际后端容器完成应用层请求、成果回存、页面和历史验收。端口、ping、草案或模拟结果均不计作
真实联调成功。

机器可读结论见 `preflight.json`。`backend-github/deploy/remote-ai/validate-contract-intake.cjs`
提供资料到齐后的 fail-closed 校验，配套测试覆盖占位地址、HTTP、URL 凭据、内联密钥、RTSP、
视频限制和停止确认；模板按预期不能通过。本记录不勾选 OpenSpec 5.x，不释放 06、07 或 RC。

## 第二阶段证据门禁（准备完成，未执行真实联调）

新增 `backend-github/deploy/remote-ai/validate-real-integration-evidence.cjs`，用于实际联调完成后
核验脱敏证据。它不发起供应商请求，也不会启用 remote；其职责是防止以下内容被写成 05 成功：

- mock、draft fixture、宿主机请求或单纯端口可达；
- 未与私有 intake 原始字节哈希绑定的接口版本和能力；
- 图片、视频或流任一流程缺失，或者供应商调用次数不是 1；
- UNKNOWN 被记为成功、未确认停止被记为 STOPPED、未确认能力被扩大启用；
- 没有实际资产哈希、视频事件偏移、流事件时间戳/截图、页面展示或历史读取；
- 提交证据包含供应商 URL、RTSP、凭据或授权值，或引用文件大小/哈希不匹配。

`tests/real-integration-evidence.test.cjs` 覆盖上述边界，并验证 CLI 会核对引用文件内容且错误输出
不回显供应商坐标。本阶段共 11 项 Node 测试通过；详见
`real-integration-evidence-validation.json`。这仍是 fail-closed tooling 证据，`realProviderRequestAttempted`
保持 false，5.1—5.4 和伴随流 5.1—5.2 仍全部未完成。
