# 固定模拟样例

全部 JSON、输入和成果均为本地构造的测试数据，没有执行推理，不能证明真实算法能力。
manifest.json 标注每个样例的契约、schema 与预期通过或拒绝；固定时间不是可用性承诺。

input.png 与 annotated.png 是 16×16 合成色块，用来验证文件字节、媒体、长度和 SHA-256，
不包含真人或业务素材，也不声称实际绘制了检测框。

## 1.0 图片兼容样例

1. asset.json 对应模拟上传 input.png。
2. submit.json 使用固定图片参数；accepted.json（202）和 success.json（200）保持同一本地任务。
3. success.json 的成果长度与哈希对应 annotated.png；empty.json 是有效空检测。
4. unknown.json 表达已发送后响应丢失，不授权自动再次推理；error.json 表达幂等冲突。
5. provider-*.json 只演示未确认的 v0.1 同步图片协议。
6. invalid-threshold.json 与 invalid-provider-url.json 必须拒绝。

## 1.1 上传视频与实时流样例

- video-submit.json、video-success.json、video-empty.json 覆盖有界视频参数、时间偏移、
  授权截图、有效空成果及缺省的可选标注视频。
- stream-sources.json 明确未确认映射的来源不可用；stream-start.json、stream-running.json、
  stream-events.json 和 stream-empty-events.json 覆盖不透明来源、会话与有效空事件页。
- stream-stop-unknown.json 明确停止响应丢失仍不是 STOPPED。
- invalid-video-provider-url.json、invalid-stream-rtsp.json、invalid-stream-gpu-url.json、
  invalid-stream-credentials.json 必须因未知或秘密字段被拒绝。
- provider-video-*.json 与 provider-stream-*.json 只验证未确认 v0.2 草案的严格形状，
  不证明真实方法、来源映射、事件查询或停止能力。

请求样例不带 simulated 字段，因为模式只能由后端配置选择。浏览器不能伪造模拟状态、
provider 地址、RTSP 地址或凭据。
