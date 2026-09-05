# 远程推理部署边界

根配置 `deploy/docker-compose.yml` 已内置 `remote-ai` Spring profile，默认值为：

- `WGAI_INFERENCE_MODE=disabled`
- `WGAI_INFERENCE_DEVELOPMENT_STUB=false`
- `WGAI_INFERENCE_PROVIDER_KEY=remote`
- provider 地址为空

因此正式默认配置不会启动、引用或回退 stub。浏览器始终只访问业务后端；业务后端仅在能力资料、地址、TLS/CA 与服务鉴权均确认后，通过 provider HTTP 接口访问 GPU 服务。

## 本地模拟

从 Git 根执行以下配置检查或显式开发启动：

```sh
docker compose --project-directory deploy --env-file deploy/.env \
  -f deploy/docker-compose.yml \
  -f deploy/remote-inference/stub.override.yml \
  --profile remote-ai-stub config
```

`stub.override.yml` 是唯一会创建 `remote-ai-stub` 服务的入口。它使用公开开发 token、合成来源和固定 fixtures，所有能力与结果均标识 `simulated/stub`。开发数据库需要演示绑定时，只能在隔离副本按 V001→V002 后应用 `database/seeds/stub/stub-bindings.example.sql`；seed 不属于正式迁移集合。

## 真实服务配置

`prod.env.example` 是无密钥模板，默认 disabled 且地址为空。真实 GPU URL、批准 origin、token 文件与私有 CA 只写入未跟踪的 `deploy/.env` 或部署秘密配置：

- token 使用 `secrets.override.yml` 与 `WGAI_INFERENCE_TOKEN_SOURCE` 只读挂载；
- 私有 CA 使用 `ca.override.yml` 与 `WGAI_INFERENCE_CA_SOURCE` 只读挂载；
- 正常公网 CA 可留空 `WGAI_INFERENCE_CA_FILE`，继续使用 JVM 信任库；
- 禁止关闭证书或主机名校验，禁止把 GPU/RTSP URL 或服务凭据发送给浏览器。

`core.override.yml` 只为外部 Compose 兼容保留；本仓库的根 Compose 不需要重复叠加它。真实资料先保存到仓库外，分别用 `validate-contract-intake.cjs` 和 `validate-real-integration-evidence.cjs` 做 fail-closed 校验。模板故意不完整，校验通过也不等于 RTX 5070/4090 已验收。

## 当前门禁

- 图片、上传视频、流来源/会话/事件/停止可通过独立 HTTP stub 做本地协议与业务闭环验证。
- 正式配置没有已确认真实服务时，相应能力保持 disabled。
- RTX 5070 局域网与单张扩容 48GB RTX 4090 仍需实际业务后端容器、真实素材、服务版本、契约版本、成果回存与历史读取证据。
