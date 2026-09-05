<template>
  <a-card title="上传视频分析">
    <a-alert
      v-if="capability && capability.simulated"
      type="info"
      show-icon
      message="模拟视频分析"
      description="此能力返回模拟数据，不代表真实分析效果。"
      style="margin-bottom: 24px" />
    <capability-panel v-model="selectedCode" :capabilities="videoCapabilities" :supported="supports" :disabled="locked || uploading" />
    <a-button size="small" style="margin-top: 12px" :disabled="locked || uploading" @click="loadCapabilities">刷新能力</a-button>
    <a-divider />
    <video-upload-panel
      :capability="capability"
      :asset="asset"
      :disabled="locked || uploading || !available"
      :uploading="uploading"
      @file="upload"
      @invalid="error = $event" />
    <a-divider />
    <video-parameters v-model="parameters" :disabled="locked" />
    <div style="margin-top: 24px">
      <a-button type="primary" :loading="submitting" :disabled="uploading || !asset || (!draft && !available)" @click="submit">{{ draft ? '确认原提交' : '提交视频任务' }}</a-button>
      <a-button :disabled="submitting || uploading" style="margin-left: 12px" @click="newTask">开始新任务</a-button>
      <router-link to="/ai/history" style="margin-left: 16px">查看任务历史</router-link>
    </div>
    <a-alert
      v-if="draft && !submitting"
      type="warning"
      show-icon
      style="margin-top: 16px"
      message="提交结果尚未确认"
      description="确认原提交会复用相同编号和内容；不要自动创建另一任务。" />
    <a-alert v-if="error" type="error" show-icon :message="error" style="margin-top: 16px" />
  </a-card>
</template>
<script>
import CapabilityPanel from '@/modules/ai/capability/CapabilityPanel'
import VideoUploadPanel from '@/modules/ai/asset/VideoUploadPanel'
import VideoParameters from '@/modules/ai/video/VideoParameters'
import { listCapabilities, uploadAsset, submitVideoJob } from '@/modules/ai'
import { videoCapabilitySupported, errorMessage } from '@/modules/ai/result/presentation'
const defaults = () => ({ threshold: 0.5,
  sampleIntervalMillis: 1000,
  maxEvents: 100,
  includeSnapshots: true,
  annotate: false })
function key() {
  const bytes = new Uint8Array(16)
  window.crypto.getRandomValues(bytes)
  return Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('')
}
export default {
  name: 'AiVideoInferencePage',
  components: { CapabilityPanel, VideoUploadPanel, VideoParameters },
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
    videoCapabilities() { return this.capabilities.filter(videoCapabilitySupported) },
    capability() { return this.videoCapabilities.find(c => c.code === this.selectedCode) },
    available() { return this.capability && this.capability.available },
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
    supports: videoCapabilitySupported,
    activate() { if (!this.viewActive) { this.viewActive = true; this.loadCapabilities() } },
    leave() { this.viewActive = false; this.generation++; this.capabilityGeneration++; this.uploading = false; this.submitting = false },
    async loadCapabilities() {
      const ticket = ++this.capabilityGeneration
      try {
        const values = await listCapabilities()
        if (!this.viewActive || ticket !== this.capabilityGeneration) return
        this.capabilities = values
        if (!this.selectedCode && !this.draft) {
          const first = values.find(c => c.available && videoCapabilitySupported(c))
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
    valid() {
      const p = this.parameters
      return Number.isFinite(p.threshold) && p.threshold >= 0 && p.threshold <= 1 &&
        Number.isInteger(p.sampleIntervalMillis) && p.sampleIntervalMillis >= 100 && p.sampleIntervalMillis <= 60000 &&
        Number.isInteger(p.maxEvents) && p.maxEvents >= 1 && p.maxEvents <= 1000 &&
        typeof p.includeSnapshots === 'boolean' && typeof p.annotate === 'boolean'
    },
    async submit() {
      if (this.submitting || this.uploading || !this.asset || (!this.draft && !this.available)) return
      if (!this.valid()) { this.error = '请检查阈值、采样间隔和事件数范围'; return }
      if (!this.draft) {
        this.draft = { key: key(),
          request: { capabilityCode: 'video-file-analysis.v1',
            inputAssetId: this.asset.assetId,
            parameters: { ...this.parameters } } }
      }
      const ticket = ++this.generation
      this.submitting = true; this.error = ''
      try {
        const job = await submitVideoJob(this.draft.request, this.draft.key)
        if (!this.viewActive || ticket !== this.generation) return
        this.draft = null; this.asset = null
        this.$router.push({ name: 'AiJobDetail', params: { requestId: job.requestId } })
      } catch (error) { if (this.viewActive && ticket === this.generation) this.error = errorMessage(error) } finally { if (ticket === this.generation) this.submitting = false }
    }
  }
}
</script>
