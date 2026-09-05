# 04a-assets-jobs 交接

状态：`READY_FOR_00_ACCEPTANCE`。04a 包内实现与验证完成；尚未由 00 接受，不释放 04b。

- 共同起点：`0bafd30726e82de74cfeb58ebad12393b36841c7`
- 冻结 1.1.0 契约交付：`1177de8be45123d043d7cb26b845ee9d94c26784`
- 实现提交：`7c8a80f505200fe1088154ad4e3accd0b2ebf1df`
- 分支：`work/remote-inference/04a-assets-jobs`
- 工作树：`/Users/twowt88/Documents/ChatGPT/WGAI-parallel/04a-assets-jobs/code`

## 合入内容

1. 图片/视频统一持久任务、严格类型快照、视频时间线/截图/可选标注视频与历史读取。
2. PENDING 原子取消；已派发任务不伪造取消成功。`FETCHING_RESULT` 恢复只重取成果。
3. V002 新增 source/session/event 三表，不删除、不改写 V001 或历史表。
4. 五个流业务端点、来源归属、持久幂等、唯一启动、查询恢复、事件去重/游标、截图和停止边界。
5. MP4/H.264 私有存储及独立视频上限；浏览器边界无 RTSP/GPU URL/凭据/provider identity。
6. 生产流功能保持硬关闭，待 05 提供真实接口证据后由其明确开启。

## 验收摘要

- JUnit：49/49 PASS，Java 8 / class major 52。
- 迁移：V001→V002 重建与各两次执行 PASS；123 张历史表、V001 结构与行摘要不变。
- 完整后端镜像：`wgai-04a-assets-jobs:round5-7c8a80f`
  / `sha256:74380adf50d0b9c9c1f510e94ae876eff589e16eb790c45315641cff0c9424c0`。
- 分层、归属、冻结契约、Graphify 更新和隔离环境清理均 PASS。

00 应从实现提交核实 64 个归属路径，独立重跑迁移、Java 8 用例和完整构建。通过后再记录新的共同
起点并释放 04b。真实 provider 尚未确认，因此 04a 的 provider 替身结果不能被记为 5.1 或真实联调成功。

## 保留的未完成项

- 文件任务 DISPATCHING/WAITING 的完整重启对账、远程取消确认、遥测与故障注入交给 06。
- 真实图片/视频/流请求、source ID 映射、远程事件和停止能力交给 05。
- 页面与浏览器回归交给 04b/00。任何外部能力未知继续保持 disabled/UNKNOWN。

回退只追加 revert 代码；保留 V001/V002 表和已产生的历史数据，不执行删除迁移。
