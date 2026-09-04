<template>
  <section v-if="job">
    <a-tag v-if="job.simulated" color="purple">模拟数据 · 未执行真实推理</a-tag>
    <h2>{{ labels[job.state] || '未支持的任务状态' }}</h2>
    <p>任务编号：{{ job.requestId }}</p>
    <p>任务类型：{{ typeLabel(job) }}</p>
    <p>能力：{{ job.capabilityCode }} ／ {{ job.capabilityVersion }}</p>
    <p>创建时间：{{ job.createdAt }} ｜ 更新时间：{{ job.updatedAt }}</p>
    <p v-if="job.parameters">阈值 {{ job.parameters.threshold }} · 最大检测数 {{ job.parameters.maxDetections }} · 标注图片 {{ job.parameters.annotate ? '是' : '否' }}</p>
    <p v-if="job.videoParameters">阈值 {{ job.videoParameters.threshold }} · 采样间隔 {{ job.videoParameters.sampleIntervalMillis }} 毫秒 · 最大事件数 {{ job.videoParameters.maxEvents }} · 截图 {{ job.videoParameters.includeSnapshots ? '是' : '否' }} · 标注视频 {{ job.videoParameters.annotate ? '是' : '否' }}</p>
    <a-alert
      v-if="job.state === 'UNKNOWN'"
      type="warning"
      show-icon
      message="结果无法确认，原请求可能已经处理"
      description="此任务不会自动重新提交。刷新或重新查询只读取已保存的记录。" />
    <a-alert v-if="job.error" style="margin-top: 12px" type="warning" show-icon :message="describe(job.error)" />
    <p v-if="!terminal" style="margin-top: 12px">正在查询业务记录。离开页面会停止查询，但不会取消处理。</p>
  </section>
</template>

<script>
import { stateLabels, errorMessage, jobTypeLabel } from '@/services/ai/presentation'
import { terminalStates } from '@/services/ai/jobPolling'
export default {
  name: 'JobStatusPanel',
  props: { job: { type: Object, default: null } },
  data: () => ({ labels: stateLabels }),
  computed: { terminal() { return this.job && terminalStates.includes(this.job.state) } },
  methods: { describe: errorMessage, typeLabel: jobTypeLabel }
}
</script>
