<template>
  <a-card title="AI 推理">
    <a-alert
      v-if="capability && capability.simulated"
      type="info"
      show-icon
      message="模拟图片检测"
      description="此能力返回模拟数据，用于验证业务流程，不代表实际识别效果。"
      style="margin-bottom: 24px" />
    <capability-panel v-model="selectedCode" :capabilities="capabilities" :disabled="locked || uploading" />
    <a-button size="small" style="margin-top: 12px" :disabled="locked || uploading" @click="loadCapabilities">刷新能力</a-button>
    <a-divider />
    <upload-panel
      :capability="capability"
      :asset="asset"
      :disabled="locked || uploading || !available"
      :uploading="uploading"
      @file="upload"
      @invalid="error = $event" />
    <a-divider />
    <detection-parameters v-model="parameters" :disabled="locked" />
    <div style="margin-top: 24px">
      <a-button type="primary" :loading="submitting" :disabled="uploading || !asset || (!draft && !available)" @click="submit">
        {{ draft ? '确认原提交' : '提交任务' }}
      </a-button>
      <a-button :disabled="submitting || uploading" style="margin-left: 12px" @click="newTask">开始新任务</a-button>
      <router-link to="/ai/history" style="margin-left: 16px">查看任务历史</router-link>
    </div>
    <a-alert
      v-if="draft && !submitting"
      type="warning"
      show-icon
      style="margin-top: 16px"
      message="提交结果尚未确认"
      description="确认原提交会复用相同编号和内容。开始新任务前，请先检查历史，避免重复处理。" />
    <a-alert v-if="error" type="error" show-icon :message="error" style="margin-top: 16px" />
  </a-card>
</template>

<script>
import CapabilityPanel from '@/components/ai/CapabilityPanel'
import UploadPanel from '@/components/ai/UploadPanel'
import DetectionParameters from '@/components/ai/DetectionParameters'
import { listCapabilities, uploadAsset, submitInference } from '@/api/ai'
import { capabilitySupported, errorMessage } from '@/services/ai/presentation'

const defaults = () => ({ threshold: 0.5, maxDetections: 10, annotate: true })
export default {
  name: 'AiInferencePage',
  components: { CapabilityPanel, UploadPanel, DetectionParameters },
  data: () => ({ capabilities: [],
    selectedCode: '',
    asset: null,
    parameters: defaults(),
    draft: null,
    uploading: false,
    submitting: false,
    error: '',
    viewActive: false }),
  computed: {
    capability() { return this.capabilities.find(c => c.code === this.selectedCode) },
    available() { return this.capability && this.capability.available && capabilitySupported(this.capability) },
    locked() { return this.submitting || !!this.draft }
  },
  watch: { selectedCode() { if (!this.draft) this.asset = null } },
  created() { this.generation = 0; this.capabilityGeneration = 0 },
  mounted() { this.activate() },
  activated() { this.activate() },
  deactivated() { this.leave() },
  beforeDestroy() { this.leave() },
  beforeRouteLeave(to, from, next) { this.leave(); next() },
  methods: {
    activate() {
      if (this.viewActive) return
      this.viewActive = true
      this.loadCapabilities()
    },
    leave() { this.viewActive = false; this.generation++; this.capabilityGeneration++; this.uploading = false; this.submitting = false },
    async loadCapabilities() {
      const ticket = ++this.capabilityGeneration
      try {
        const capabilities = await listCapabilities()
        if (!this.viewActive || ticket !== this.capabilityGeneration) return
        this.capabilities = capabilities
        if (!this.selectedCode && !this.draft) {
          const first = capabilities.find(c => c.available && capabilitySupported(c))
          this.selectedCode = first ? first.code : ''
        }
      } catch (error) { if (this.viewActive && ticket === this.capabilityGeneration) this.error = errorMessage(error) }
    },
    async upload(file) {
      if (this.locked || this.uploading || !this.available) return
      const ticket = ++this.generation
      this.uploading = true; this.asset = null; this.error = ''
      try {
        const asset = await uploadAsset(file)
        if (this.viewActive && ticket === this.generation) this.asset = asset
      } catch (error) { if (this.viewActive && ticket === this.generation) this.error = errorMessage(error) } finally { if (ticket === this.generation) this.uploading = false }
    },
    newTask() { this.generation++; this.draft = null; this.asset = null; this.parameters = defaults(); this.error = '' },
    async submit() {
      if (this.submitting || this.uploading || !this.asset || (!this.draft && !this.available)) return
      const p = this.parameters
      if (!Number.isFinite(p.threshold) || p.threshold < 0 || p.threshold > 1 ||
          !Number.isInteger(p.maxDetections) || p.maxDetections < 1 || p.maxDetections > 100) {
        this.error = '请填写 0—1 的阈值及 1—100 的整数检测数'; return
      }
      if (!this.draft) {
        const bytes = new Uint8Array(16)
        window.crypto.getRandomValues(bytes)
        this.draft = { key: Array.from(bytes, b => b.toString(16).padStart(2, '0')).join(''),
          request: { capabilityCode: this.selectedCode, inputAssetId: this.asset.assetId, parameters: { ...p } },
          waitMillis: this.capability.maxWaitMillis }
      }
      const ticket = ++this.generation
      this.submitting = true; this.error = ''
      try {
        const job = await submitInference(this.draft.request, this.draft.key, this.draft.waitMillis)
        if (!this.viewActive || ticket !== this.generation) return
        this.draft = null; this.asset = null
        this.$router.push({ name: 'AiJobDetail', params: { requestId: job.requestId } })
      } catch (error) { if (this.viewActive && ticket === this.generation) this.error = errorMessage(error) } finally { if (ticket === this.generation) this.submitting = false }
    }
  }
}
</script>
