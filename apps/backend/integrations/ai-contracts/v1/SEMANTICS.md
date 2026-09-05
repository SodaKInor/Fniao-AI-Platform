# Business v1 和公共 Java 边界

内部契约版本 1.0.0；本轮仅冻结声明和模拟样例。真实提供方接口 `provider-draft/v0.1.openapi.json` 为未确认草案，不可据此启用 remote。控制器继续使用现有 `org.jeecg.common.api.vo.Result<T>` 和 `X-Access-Token` 登录令牌，不创建第二套登录或包装类型。

## 身份、响应与权限

`requestId` 始终是本地持久任务 ID，与 `JobRequest.requestId`、`JobDto.requestId`、路径中的 `{id}` 同义；providerRequestId 仅为对方关联 ID，不能替代本地身份。接受提交前必须持久保存用户、输入、参数、配置快照和幂等身份；不能先发出推理再补记录。

- POST /infer：最多等待 waitMillis（0—1500，默认 1500）后返回。已终止观察的状态 SUCCEEDED/FAILED/UNKNOWN/CANCELLED 返回 200；PENDING/DISPATCHING/WAITING/FETCHING_RESULT 返回 202。后台只继续这一次调用。
- POST /jobs：首次提交持久保存后返回 202；重复请求对应记录已终止观察则 200，否则 202。它与 /infer 使用同一去重空间，不能因换接口创建第二条任务。
- GET /jobs/{id} 和历史查询永远只读本地数据，返回 200。200/202 和 success 表示 API 操作，不意味着模型成功；检查 result.state 才能判断任务。
- 业务 JSON 沿用 Result 的 success/message/code/result/timestamp，code 与 HTTP 状态一致。GPU 鉴权错误是任务 PROVIDER_AUTH，不能映射成登录 401 或触发前端登出。
- 输入上传完成、文件校验和资产元数据提交后返回 201。下载成功是原始二进制；错误在响应未开始时使用 Result<ErrorDto>，流中断不能再拼接 JSON。
- 当前用户从登录会话取得；请求体没有 ownerId。任务、输入引用、成果、取消均核对归属。不存在和其他用户资源统一 404；有归属但已过期的资产返回 410。下载路径必须经过登录和资产权限，不能复用旧匿名静态文件目录。
- DTO 响应省略 null 字段（现有 Jackson 的 NON_NULL 注解），Instant 以 UTC ISO-8601 字符串输出，非 epoch 秒；后续 Controller/mapper 装配须保持此序列化规则。禁止把 domain.JobRecord 直接序列化为 API 响应。

## 首个能力和输入约束

`image-detection.v1` 是固定的模拟图片检测能力。参数为 threshold 数值 [0,1]、maxDetections 整数 [1,100]、annotate 布尔值；三个字段全部必填。示例值 0.5 / 10 / true。不接受任意 Map、GPU 地址、模型路径或未知字段。JSON 重复键必须拒绝。

成果 `detection.v1` 包含正整数 imageWidth/imageHeight 和 detections；每条有 label、score 和归一化 box(x,y,width,height)。应用校验 x+width<=1、y+height<=1，所有数字有限，条目数不超过该请求 maxDetections。有效空成果有 detections=[]、artifacts=[]，仍为 SUCCEEDED；传输错误或未解析数据不能用空成果掩盖。首版最多一个 PNG/JPEG 成果；annotate=false 时成果列表为空，annotate=true 允许有效空检测不生成成果。

本地模拟上限：输入/单成果各 10 MiB、PNG/JPEG、短等待 1500ms。开发实现初始并发 1、排队上限 20、外呼总预算 120s，仅为本地模拟实现配置，不声称真实 GPU 容量。应用必须按实际能力描述和更严格的配置校验字节、媒体签名、图像维度与解码资源预算。参考模拟维度上限 4096×4096。真实媒体/文件大小/超时/并发在第 5 批确认前保持能力不可用。历史资产过期策略与输入/输出保留时间由应用配置；模拟样例固定有效期是测试数据。

## 可复现的本地去重

唯一约束为 `(ownerId, idempotencyKey)`，key 为 8—128 个字母、数字、下划线或连字符；同一用户跨 /infer 和 /jobs 共用。createOrGet 必须在一次原子事务内创建，或取回已有记录并比较摘要；不同摘要抛 IdempotencyConflictException，映射 409。不能依赖进程锁或前端禁按钮。

请求摘要 SHA-256 输入为以下顺序的 UTF-8 文本，每一行都以 LF 结尾，无 BOM：

```text
wgai-inference-v1
image-detection.v1
<inputAssetId>
<threshold 规范十进制>
<maxDetections 十进制整数>
<annotate 小写 true/false>
<retryOfRequestId 或空串>
```

threshold 使用 Java BigDecimal.stripTrailingZeros().toPlainString()，零固定为 `0`；0.50 和 0.5 视为相同，禁止 NaN/Infinity。身份字段必须满足 schema 的受限字符集，避免换行歧义。服务 URL、当前绑定版本、waitMillis、HTTP 路径、时间和新生成 requestId 不参与摘要。资产内容不可替换，输入资产 ID 即稳定引用。应用先按用户/key 处理已存在请求，再检查新请求的当前能力可用性；已有相同请求即使绑定已停用也返回原快照，不重新派发。

UNKNOWN 后的新尝试必须来自用户明确操作，使用新 key、新 requestId，校验 retryOfRequestId 的归属并保存关联；原记录保持不变。本版本没有自动重试端口或重试 API。应用应告知原请求可能已经处理，不从轮询、页面刷新或网络拦截器自动生成新 key。

## 状态及原子边界

| 当前状态 | 允许的新状态 | 条件 |
|---|---|---|
| PENDING | DISPATCHING | 仅 claimPending 原子取得派发权 |
| PENDING | CANCELLED | 仅 cancelPending，与 claimPending 竞争同一记录 |
| DISPATCHING | WAITING / FETCHING_RESULT / FAILED / UNKNOWN | 同 token、预期 version 的 updateClaimed；直接返回可跳过 WAITING |
| WAITING | FETCHING_RESULT / FAILED / UNKNOWN | 同 token、预期 version；不产生第二次推理 |
| FETCHING_RESULT | FETCHING_RESULT / SUCCEEDED / FAILED | 已有完整 ProviderResult 检查点；仅重新获取同一成果，不再 infer |
| SUCCEEDED / FAILED / CANCELLED / UNKNOWN | 无 | 首版保护记录，迟到响应不可覆盖；UNKNOWN 是未知事实，不是确认失败 |

createOrGet 创建 PENDING/version=0，token/checkpoint/result/error 初始为空。claimPending 只从匹配 version 的 PENDING 变为 DISPATCHING，持久保存独占 token 并 version+1；返回空则调用者不得发送。不能在任何 HTTP/文件 I/O 期间保持数据库事务或行锁。候选扫描 findPending 不授予派发权。

updateClaimed 同时检查 token、version、合法转换，成功才 version+1；JobUpdate 是可变字段的完整替换，不是忽略 null 的补丁。进入 FETCHING_RESULT 前必须保存非空 ProviderResult，包含结构化结果和外部成果引用；后续更新保留它。SUCCEEDED 必须有 InferenceResult 且 error 为空，所有成果文件与归属资产元数据已提交；FAILED/UNKNOWN 必须有 JobError 且 result 为空。FETCHING_RESULT 可保存明确传输错误并在期限内重取同一引用；不可把失败下载标成成功。

cancelPending 还检查 ownerId/version；与派发同时发生只有一方成功。重复取消已 CANCELLED 时应用返回已有记录；其他已派发状态因外部取消未确认返回 409 CANCEL_NOT_SUPPORTED，不改状态、不承诺 GPU 停止。确认远程查询/取消后须由 02/00 扩展协议，不自行补签名或把结束等待视为执行取消。

ProviderException 的 ExecutionCertainty 由适配器根据真实证据给出：连接建立前明确未发送可为 NOT_STARTED；已确认执行失败为 CONFIRMED_FAILED；发出后断线/超时、未经确认的 500、意外 202 或格式错误为 UNKNOWN。前两者转 FAILED，后者转 UNKNOWN。错误分类不授权自动再次推理；SDK/HTTP 层的透明 POST 重试必须关闭。外部结果未确认时，禁止自动重排到 PENDING。

首版 JobRepository 覆盖 03/04a 所需派发、查询、取消和检查点。第 6 批重启恢复可在交接后扩展受控扫描/恢复端口；没有定义用模糊全表更新绕过 token/version 的方法。

## 流与文件责任

| 调用 | 打开和关闭责任 | 完成含义 |
|---|---|---|
| InferenceProvider.infer | Provider 在一次调用中最多打开一次 ProviderRequest.input，所有出口关闭；未打开无需关闭 | 返回标准 ProviderResult 或分类异常；不修改仓储、不重发 |
| ProviderArtifactReader.open | Reader 验证配置绑定、引用、过期、允许来源及重定向；成功返回后调用者关闭 InputStream，关闭必须连带关闭网络响应；打开失败由 reader 清理 | 返回有界流，读取过程 IOException 由应用归类为成果传输失败 |
| ArtifactStore.write | 借用调用者传入的流，Store 不关闭它；应用使用 try-with-resources 关闭 | 核对可选长度/哈希，计算真实长度/SHA-256，原子发布完整文件；失败清理部分文件 |
| ArtifactStore.open | Store 打开，调用者关闭 | 根据内部 storageKey 受控读取；应用在调用之前核对资产归属/过期 |
| ArtifactStore.delete | 应用只用于已授权清理或未入库孤儿；缺失文件视为成功 | 不修改任务、不删除模型、不调用供应商 |

providerKey/adapterId 选后端配置；ProviderArtifact.reference 只能由匹配 reader 解释，不能成为 API URL、本地路径或允许任意主机的请求。临时引用只留内部检查点；成功结果只含本地 assetId。远端离线时本地历史和已回存文件仍可读。若文件写成但元数据入库失败，应用清理未引用文件；若部分成果已经入库，保留检查点并有界重试收集，不发送第二次推理。

domain 和 port 只依赖 Java 8 标准库和领域类型；domain 不导入任何其他层。API DTO 单独表达业务 JSON，只复用两个规范化枚举，不复用领域记录、数据库实体或供应商 wire DTO。三仓储端口职责分别为任务、私有资产元数据、本地能力绑定；存储和 provider 不持有它们。43 个顶层公开类型各自单文件，真实依赖与物理行数详见 acceptance/02-contract 的检查结果。
