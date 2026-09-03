# WGAI 远程 GPU 接入检查

检查日期：2026-09-02。范围为当前工作区、运行中的本机四个容器、接口文档与数据库结构/配置完整性；没有连接 GPU 电脑，没有执行真实推理，没有评测算法精度。

## 结论

基础管理系统可运行，远程 GPU 推理链路尚未建立。缺项同时涉及接口实现、文件流转、任务状态、权限和前后端版本差异，不能只靠填写服务 IP 或打开算法开关解决。

建议保留当前管理系统，增加统一 AI 接入层和独立 GPU 服务；以业务能力管理模型，逐项退役旧实现。总体设计见 [design.md](design.md)，实施顺序见 [tasks.md](tasks.md)。

## 已确认的基础

| 检查项 | 本次结果 | 实际含义 |
|---|---|---|
| 前端、后端、MySQL、Redis | 四个容器均为 running / healthy | 基础容器运行正常，不等于模型可用 |
| 前端首页 | `http://127.0.0.1:8080/` 返回 HTTP 200 | 本机入口可访问 |
| 对外端口 | 仅前端绑定 `127.0.0.1:8080` | 当前其他电脑不能直接访问管理页面；不妨碍后端主动请求局域网 GPU 服务 |
| 数据库 | 当前库有 122 张表 | 不是只有空库，但不包含前端所有页面对应的表 |
| 旧模型登记 | `tab_ai_model` 有 5 条记录，5 条的三个文件路径均为空 | 是历史登记，不是已部署模型 |
| 模型绑定 | `tab_ai_model_bund` 为 0 条 | 目前没有可供旧识别链使用的绑定 |
| 上传目录 | 后端命名卷挂在 `/data/uploads` | 文件仅在当前后端环境可见 |
| 前端业务 API | 同源 `/jeecg-boot`，Nginx 转发 Java 后端 | 可沿用这一层隔离 GPU 服务 |
| 实际接口文档 | 234 个文档路径 | 文档路径带 `/jeecg-boot` 前缀，统计时已去除；数量不代表完整接口测试 |
| 源码版本基线 | ZIP 解压目录，无 Git 元数据 | 原 ZIP 与已有差异文件可用于回溯；正式清理前建议建立版本管理 |

以上数据库查询只读取数量、表名、配置是否为空，没有读取或记录用户密码、业务文件和服务密钥。登录后权限、上传下载闭环仍未在本次执行。

## 缺项与优先级

P0：接入远程模型/扩大访问范围前必须处理。P1：第一条真实推理链上线前完成。P2：后续治理。

| 编号 | 级别 | 当前证据 | 缺少什么及影响 |
|---|---|---|---|
| A01 | P0 | `TabAiHistoryServiceImpl.startAi` 按 `spareOne` 分支调用本地方法；区域检测直接实例化 `AIModelYolo3`。[E1][E2] | 缺统一远程客户端、服务地址、鉴权、版本化协议；现在点击运行仍进入 Java 算法代码 |
| A02 | P0 | 配置声明 algorithm/gpu/plc 开关，但目标后端源码检索未发现消费这些开关的执行守卫；OpenCV 启动开关、ASR 和部分摄像头入口有真实检查。[E3] | `WGAI_ALGORITHM_ENABLED=false` 不能保证所有旧推理端点被禁用；远程模式必须在服务层拒绝本地执行，前端同步禁用 |
| A03 | P0 | 前端有训练、人脸、数字人、更多视频页面；对应 controller 只在只读参考版中发现，当前运行接口文档亦缺少。[E4] | 这些属于功能实现缺口。按业务保留清单隐藏/退役，必要的再实现，不能作为“待填写配置”处理 |
| A04 | P0 | Shiro 将 `/tab/testAI/**`、`/tab/tabAiSubscription/**`、`/tab/tabAiBase/**` 设为匿名；部分写接口的权限注解被注释。[E5] | 扩大局域网访问或启用新能力前收紧匿名规则、接口操作权限和资产/任务归属；仅隐藏菜单不够 |
| A05 | P0 | 文件路径是容器本地路径，上传响应返回相对路径；模型引擎同样从 uploadpath 拼接权重路径。[E6][E7] | GPU 主机不能读取 `/data/uploads/...`、浏览器相对 URL 或本机 Windows 路径；需要真正传输输入文件、下载输出文件 |
| A06 | P1 | 没有发现统一 GPU 服务的就绪检查、任务协议和节点凭据配置。[E3] | 缺固定地址/端口、节点监听、防火墙、服务鉴权、模型清单与运行时校验；ASR 的 host/port 只适用于现有专用 SDK |
| A07 | P1 | 旧视频通过线程与 Redis 标志控制；统一请求超时 90 秒，Nginx 读/写超时 600 秒。[E2][E8] | 缺持久化任务编号、排队、取消确认、幂等、异常恢复；延长浏览器超时不能可靠处理长视频/训练 |
| A08 | P1 | `TabAiModel` 以权重/配置/names 文件和 `spare*` 字段表示模型。[E7] | 缺能力、版本、参数/结果 schema、服务节点、启停状态及历史版本快照；后续换模型还得改业务分支 |
| A09 | P1 | 本地上传实现调用 `mf.getBytes()`；最大上传 2GB；通用静态文件下载匿名。[E6][E5] | 大文件可能造成高内存占用；需要按媒体类型限额、流式传输、资产归属和受控下载，不能直接复制现有路径机制 |
| A10 | P1 | WebSocket `onOpen` 直接用路径 `userId` 登记连接；路径中有 rtsp 会进入摄像头服务；该路径在 Shiro 中匿名。[E9] | 不能直接拿旧通道传新任务敏感结果；首期采用带登录凭据的任务查询，后续推送需绑定真实身份与任务访问权 |
| A11 | P1 | MaxKB 页面将配置 URL 放进 iframe；表单与列表处理 `apiKey`。[E10] | 不是统一后端代理。若保留问答，重新设计后端调用与响应流，服务凭据不能随普通模型目录下发 |
| A12 | P1 | `docker-core` 仅排除 easyAi 演示/训练源码，OpenCV、JavaCV、ASRT 仍参与核心构建。[E11] | 开关关闭不等于依赖解耦；远程迁移后清理引用，再移除 native/模型运行依赖及私有 JAR 构建步骤 |
| A13 | P1 | 当前后端健康检查读取 `doc.html`。[E3] | 不能体现 DB/Redis 业务就绪、远端 GPU 离线、某个模型加载失败；分别提供核心健康与 AI 可用性 |
| A14 | P1 | 首次初始化 SQL 清空历史绑定、权重路径和部分外部配置，仅空卷执行。[E12] | 新框架需要增量迁移与版本记录；不能重跑清理 SQL，更不能删除卷重建 |
| A15 | P2 | 当前 Compose 持久化已有；审阅的部署配置未提供自动备份恢复演练或日志轮转参数 | 补数据库/资产/模型清单备份、保留期、容量限制、恢复演练；持久化不等于备份 |
| A16 | P2 | Vue 2.6、Vue CLI 3、Java 8、Spring Boot 2.6.6。[E13] | 远程拆分完成后单列框架升级，避免把重写界面、升级权限框架和迁移算法同时进行 |

关于 A16：Vue 官方已确认 Vue 2 于 2023-12-31 结束维护。这支持后续安排升级，但不能据此判断本项目所有旧模型都应删除。[Vue 官方说明](https://v2.vuejs.org/eol/)

## 前后端能力对应关系

这里区分“代码/接口存在”和“模型执行可用”。未用伪造登录或触发算法端点来测试功能。

| 能力区域 | 前端 | 当前目标后端与运行文档 | 处理建议 |
|---|---|---|---|
| 模型登记、绑定、历史 | 有 | `/tab/` 文档路径 40 个；本地识别分支仍在 | 保留业务数据和展示，替换执行与版本管理 |
| 区域入侵 | 有 | `/video/tabVideoUtil` 文档路径 8 个；绑定旧 V5 本地执行 | 若保留视频业务，改为远程会话/任务适配 |
| 视频告警、抽帧/视频配置、新订阅 | 有 | 当前 `/video/` 只有上述区域入侵接口；其他 controller 只在参考版出现 | 默认不承诺可用，依据首批视频需求重建必要部分 |
| 训练脚本/训练任务 | 有 | `/train/` 文档路径 0 个，目标 controller 扫描也未发现 | 默认停用；训练以后作为独立任务类型设计 |
| 人脸功能 | 有 | `/face/` 为 0；目标源码缺对应 controller | 若有明确需求再接适配器和身份库业务 |
| 数字人 | 有 | `/szr/` 为 0；目标源码缺对应 controller | 默认不纳入首期 |
| 语音 | 有旧入口 | `/tab/tabAiHistory/addAudio` 存在，但 `/audio/` 设备/配置模块缺失 | 保留 ASR 能力时走新的统一协议，不能混同“语音 API 已存在”与完整模块 |
| MaxKB | 有 | 7 个文档路径；前端聊天使用 iframe | 独立外部集成，需决定保留后再接后端代理 |
| easyAi 聊天/图片训练 | 有相关 CRUD | `/chat/`、`/easy/` 的 CRUD 仍在；部分执行类被构建 profile 排除 | 配置页面可见不等于训练可运行；作为优先退役候选 |

## 模型淘汰原则

1. 按需要保留的业务能力建立清单，不以 YOLO 版本、目录名或文件年龄直接判断效果。
2. 旧本地推理、演示入口、缺后端页面、已从构建排除的训练功能优先隔离。
3. 为保留能力接入一个真实模型，使用用户场景样本验证精度、延迟、显存和许可条件后再替换。
4. 退役模型先禁止新任务，处理正在运行的任务和订阅，再清理菜单、字典、绑定、依赖及权重；历史结果保留模型版本和参数快照。
5. 没有需求决定前，不删除具体模型、数据库表或参考版内容。

## 还需要配置的值

| 类别 | 必须补齐 | 本次能否验证 |
|---|---|---|
| GPU 电脑 | OS、GPU 厂商/型号/显存、驱动、可支持的推理运行时 | 未提供，未连接 |
| 网络 | 固定 IP/局域网 DNS、服务端口、GPU 监听地址、仅允许后端主机访问的防火墙规则 | 未提供 GPU 地址，未测试跨机可达性 |
| 服务 | 节点编号、服务凭据、TLS/可信 CA、启动与崩溃恢复、磁盘目录 | 尚未实现统一服务 |
| 模型 | 业务能力、模型 ID/版本、权重校验值、输入输出/参数 schema、资源要求 | 尚未选择首批保留能力 |
| 请求策略 | 上传限额、同步等待预算、总截止时间、队列容量、并发、幂等保留期 | 设计给出初始值，需联调压测校准 |
| 结果 | 文件保存期、下载权限、历史记录、备份目标 | 需要业务策略；不能让结果只保存在 GPU 临时目录 |

首期采用后端转发文件并主动取结果时，**无需让 GPU 电脑访问本机 `127.0.0.1:8080`，也无需开放 MySQL/Redis 端口**。如果以后要 GPU 回调、拉取本机文件或让其他设备打开前端，再单独配置可达的管理机地址和对应入口。

## 证据索引

路径中的行号为本次源码的 1-based 行号。

- E1：[历史识别控制器](../../../backend-github/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/demo/tab/controller/TabAiHistoryController.java)，115–118；[识别分派服务](../../../backend-github/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/demo/tab/service/impl/TabAiHistoryServiceImpl.java)，593–703。
- E2：[区域检测服务](../../../backend-github/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/demo/video/service/impl/TabVideoUtilServiceImpl.java)，45–75。
- E3：[部署 Compose](../../../deploy/docker-compose.yml)、[Docker 后端配置](../../../deploy/backend/application-docker.yml)、[环境示例](../../../deploy/.env.example)；目标源码中配置消费点为 `JeecgSystemApplication.java:37` 和 `TabAiHistoryServiceImpl.java:61–77`。
- E4：[训练入口](../../../frontend-vue/src/views/train/TabTrainPythonList.vue)，214、259；[人脸入口](../../../frontend-vue/src/views/face/TabFaceTest.vue)，343；[视频配置入口](../../../frontend-vue/src/views/video/TabAiVideoSettingList.vue)，221；两版 controller 映射与运行接口文档交叉核对。
- E5：[Shiro 配置](../../../backend-github/jeecg-boot-base-core/src/main/java/org/jeecg/config/shiro/ShiroConfig.java)，94、102、129–130、145、163。
- E6：[通用上传/下载](../../../backend-github/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/system/controller/CommonController.java)，66–125、134–156、213 起。
- E7：[模型实体](../../../backend-github/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/tab/entity/TabAiModel.java)，58–94；[本地模型加载](../../../backend-github/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/tab/AIModel/AIModelYolo3.java)，695–703、1062–1070。
- E8：[前端请求设置](../../../frontend-vue/src/utils/request.js)，14–20；[Nginx 配置](../../../deploy/frontend/nginx.conf)。
- E9：[WebSocket](../../../backend-github/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/message/websocket/WebSocket.java)，32、49–58。
- E10：[嵌入聊天页](../../../frontend-vue/src/views/maxkb/userchat.vue)，3–7、17–26；[模型表单](../../../frontend-vue/src/views/maxkb/modules/TabMaxkbModelForm.vue)，21–38；[跳转代码](../../../frontend-vue/src/views/maxkb/TabMaxkbModelList.vue)，239–242。
- E11：[业务模块 POM](../../../backend-github/jeecg-module-system/jeecg-system-biz/pom.xml)，43–66、82–94；[核心构建文件](../../../deploy/backend/Dockerfile)。
- E12：[首次初始化清理 SQL](../../../deploy/db/002-local-sanitize.sql)，5–25。
- E13：[前端依赖](../../../frontend-vue/package.json)、[后端依赖](../../../backend-github/pom.xml)，12、18。

## 验证边界

- Graphify 首先定位了模型、历史、视频、上传和参考版关联；宽查询结果被截断，因此没有把图中未出现的节点当作“不存在”。结论再由源码、运行接口文档和数据库验证。
- Serena 服务可访问、文本检索可用；符号索引因服务 PATH 找不到 Node 而未初始化。本次没有把符号检索失败当成源码缺失，也没有修改开发工具配置。
- 本次未执行会重启容器/写探针的完整 `verify.sh`。此前部署记录中的完整验收不是本次重新执行的结果。
- 没有验证登录后页面、模型推理、显卡可见性、跨机网络、压测或数据库恢复。以上列为后续验收项。
