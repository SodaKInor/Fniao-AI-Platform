# 固定模拟样例

全部 JSON、输入和成果均为本地构造的测试数据，没有执行推理，不能证明真实算法能力。manifest.json 标注每个样例的来源、schema 与预期通过/拒绝；响应自身也包含 simulated=true 和说明。固定时间用于复现，不代表目前文件仍未过期。

`input.png` 是 16×16 合成色块；`annotated.png` 是另一个合成色块，用来验证文件字节、媒体、长度与 SHA-256，不声称实际画出了检测框。两者均为 PNG，无真人或业务素材。

1. `asset.json` 对应模拟上传 input.png，取得 mock_input_0001。
2. 以 Idempotency-Key `mock_request_0001` 提交 `submit.json`。参数固定 threshold=0.5 / maxDetections=10 / annotate=true。
3. 短等待未完成时返回 `accepted.json`（202），后续读取返回 `success.json`（200）；两者是同一个 mock_job_0001。本地任务完成且文件回存后，成果资产 mock_output_0001 的长度/哈希必须对应 annotated.png。
4. `empty.json` 有效空检测和空文件列表为成功；`unknown.json` 模拟已发送后响应丢失，不自动再次推理；`error.json` 模拟复用 key 的不同请求冲突。
5. `provider-*.json` 仅演示未确认的同步协议：metadata 与输入文件 multipart，返回结构化内容及同服务相对成果引用；空成果/错误分别有样例。
6. `invalid-threshold.json`、`invalid-provider-url.json` 必须拒绝。其他 JSON 必须符合各自 schema；还检查跨文件 ID、模拟标识、媒体/哈希和状态组合。

示例请求文件自身严格遵守业务 schema，故没有额外的 simulated 请求字段；其模拟身份由本目录说明和 manifest 指定。任何实际调用所选能力的模式由后端配置决定，浏览器不能伪造 simulated 改变执行模式。
