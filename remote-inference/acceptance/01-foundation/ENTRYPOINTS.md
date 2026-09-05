# 01 业务入口处置清单

状态：文档核对完成；所有处置均是后续工作包的任务，不表示本轮已改菜单、权限或算法。没有源码或数据删除授权。

核对了数据库中 158 条未删除的菜单/权限导航记录、原运行服务的 234 个 Swagger 路径、前端路径字面量及目标仓库 Controller 映射。前端通过 `src/permission.js:49` 和 `src/utils/util.js:85` 从登录后的菜单数据生成路由，因此静态页面存在不等于该账号拥有入口。本轮没有登录，未验证账号菜单可见性。

原始证据：menu-inventory.json、runtime-api-inventory.json、source-entrypoints.json。源码行号均为当前提交的一基行号；文本提取不构成完整动态调用分析。目标仓库另有不属于 jeecg-system-biz 编译源根的模型 Controller 模板，不能当成第二个已部署实现。

| 区域或入口 | 处置 | 证据与后续要求 |
|---|---|---|
| 登录、用户/角色、字典、组织、系统管理与现有通用管理导航 | 保留管理 | 菜单完整导航记录已保存；首页与接口文档可访问。没有进行登录/权限业务验收，不因本轮 AI 改造删除管理功能 |
| 模型库、模型绑定库、识别结果历史、AI 基础库 | 保留管理 | 菜单分别指向 tab/TabAiModelList、TabAiModelBundList、TabAiHistoryList、TabAiBaseList，页面存在；运行 `/tab/` 有 40 个路径。保留登记和历史，不宣称模型可执行 |
| POST `/tab/tabAiHistory/addIdentify` | 迁移执行 | TabAiHistoryController.java:115–118 调用 startAi；TabAiHistoryServiceImpl.java:593 起按 spareOne 分派 V3/V5/V8/CV/OCR/ASR 本地执行。03 负责守卫，后续按确认业务迁移；本轮未调用 |
| `/tab/tabAiHistory/addIdentifyClose`、`addAudio` | 迁移执行 | 同 Controller.java:131、145；不能把旧关闭操作视为未来远程 GPU 取消。音频是否作为首个真实能力仍待双方确认 |
| `/video/tabVideoUtil/startVideoUtil`、`stopVideoUtil` | 需求待定 | Controller.java:119、149；ServiceImpl.java:59 实例化 AIModelYolo3；运行 `/video/` 8 个路径全部属于该区域。持续视频不属于首个文件请求样例，待需求确认前不删除 |
| AI 视频分析、视频拼接 | 需求待定 | 菜单 tab/live/AddressList、tab/live/test 页面存在；页面存在不证明摄像头、流服务或持续视频链路可用。新协议未覆盖持续视频，不纳入当前删除清单 |
| AI 事件订阅 | 保留管理；执行边界待迁移 | 菜单 TabAiSubscriptionList 存在；ShiroConfig.java:129 当前仍匿名。03/04b 按 3.4 收敛执行权限，不能仅隐藏菜单 |
| `/tab/testAI/**` 测试执行入口 | 停用 | AITestController 映射 `/tab/testAI`；ShiroConfig.java:102 为匿名。由 03 实施后端守卫，04b 配合入口；本轮只记录 |
| 视频告警、抽帧、配置等非 tabVideoUtil 页面 | 停用 | TabAiVideoSettingList.vue:221 引用 `/video/tabAiVideoSetting/list`，当前运行文档无该路径；目标实现缺失。以后有明确需求再分配，不删除页面或数据 |
| 训练脚本 `/train/tabTrainPython/*` | 停用 | TabTrainPythonList.vue:214、259 引用列表与 startOnePy；运行 `/train/` 路径为 0。缺实现不作为可运行功能提供 |
| 人脸 `/face/tabFacePic/extractFaceFeature` | 停用 | TabFaceTest.vue:343 有调用；运行 `/face/` 路径为 0。保留与重建需求待定，不因此删除身份数据或源码 |
| 数字人 `/szr/` | 停用 | 有前端区域，当前运行路径为 0；不纳入首期算法承诺，后续另定需求 |
| `/audio/` 设备/配置区域 | 停用 | 当前运行路径为 0；不能与已存在的 tabAiHistory/addAudio 混为一项已实现能力 |
| easyAi / tchat / teasy 的分类、配置及历史 | 需求待定 | 当前 `/chat/` 49 个、`/easy/` 25 个文档路径主要是 CRUD。是否保留此业务未确认，暂不删除数据 |
| easyAi 聊天/图片训练和演示执行 | 停用 | docker-core 明确排除五个旧算法演示/训练源文件。菜单含训练任务、语义模型训练；CRUD 构建通过不代表训练存在或可运行 |
| 三条 component=easy 的菜单 | 停用 | “轻量级模型”“在线识别”“训练日志”均指向不存在的 views/easy.vue，现有菜单仍可登记。04b 应说明停用，不凭菜单记录宣称可用 |
| MaxKB 模型配置与语言对话 | 需求待定 | 运行 7 个路径；TabMaxkbModelList.vue:241 把配置 URL 传给 userchat，后者使用 iframe。是否转为后端代理另行确认，现有数据保留 |
| 通用上传/匿名静态下载、`/websocket/{userId}` | 保留旧管理所需部分；新 AI 不复用其权限模型 | CommonController 的旧文件路径机制与 WebSocket.java:32 需由 03/04a 在新边界隔离；本轮未更改通用下载或旧用户通道 |

“停用”列表示应交给所有者的处置决定，尚未实施；“需求待定”列全部 `deletion_authorized=false`。清理包只能在替代流程通过、需求确定并获得相应范围后生成删除清单。本表没有删除清单。

运行状态只证明原服务的首页和文档正常。源码检查明确仍存在本地算法分派和匿名旧入口；这两项不能因本轮配置开关为 false、构建成功或知识图存在而标成已修复。
