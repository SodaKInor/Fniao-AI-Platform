<template>
  <a-card title="实时事件分析">
    <a-alert
      v-if="capability && capability.simulated"
      type="info"
      show-icon
      message="模拟实时事件"
      description="此能力仅用于验证页面流程，不代表真实来源已接通。"
      style="margin-bottom: 24px" />
    <capability-panel v-model="selectedCode" :capabilities="streamCapabilities" :supported="supports" :disabled="locked" />
    <a-divider>已授权来源</a-divider>
    <stream-source-panel v-model="sourceId" :sources="sources" :disabled="locked" />
    <a-divider>查询参数</a-divider>
    <stream-parameters v-model="parameters" :disabled="locked" />
    <div style="margin-top: 24px">
      <a-button type="primary" :loading="submitting" :disabled="!canStart" @click="start">{{ draft ? '确认原启动' : '启动会话' }}</a-button>
      <a-button :disabled="submitting" style="margin-left: 12px" @click="reset">重置</a-button>
      <a-button :disabled="locked" style="margin-left: 12px" @click="load">刷新能力和来源</a-button>
    </div>
    <a-alert
      v-if="draft && !submitting"
      type="warning"
      show-icon
      style="margin-top: 16px"
      message="启动结果尚未确认"
      description="确认原启动会复用相同编号和内容；不会透明创建第二个会话。" />
    <a-alert v-if="error" type="error" show-icon :message="error" style="margin-top: 16px" />
  </a-card>
</template>
<script>
import CapabilityPanel from '@/modules/ai/capability/CapabilityPanel'
import StreamSourcePanel from '@/modules/ai/stream/StreamSourcePanel'
import StreamParameters from '@/modules/ai/stream/StreamParameters'
import { listCapabilities, listStreamSources, startStreamSession } from '@/modules/ai'
import { streamCapabilitySupported, errorMessage } from '@/modules/ai/result/presentation'
const defaults = () => ({ maxEventsPerPoll: 50, pollIntervalMillis: 2000, includeSnapshots: true })
function key() {
  const bytes = new Uint8Array(16)
  window.crypto.getRandomValues(bytes)
  return Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('')
}
export default {
  name: 'AiStreamStartPage',
  components: { CapabilityPanel, StreamSourcePanel, StreamParameters },
  data: () => ({ capabilities: [],
    sources: [],
    selectedCode: '',
    sourceId: '',
    parameters: defaults(),
    draft: null,
    submitting: false,
    loading: false,
    error: '',
    viewActive: false }),
  computed: {
    streamCapabilities() { return this.capabilities.filter(streamCapabilitySupported) },
    capability() { return this.streamCapabilities.find(c => c.code === this.selectedCode) },
    source() { return this.sources.find(s => s.streamSourceId === this.sourceId) },
    locked() { return this.submitting || !!this.draft },
    canStart() {
      return !this.submitting && (!!this.draft ||
        (!!this.source && this.source.available && !!this.capability && this.capability.available))
    }
  },
  created() { this.generation = 0 },
  mounted() { this.activate() },
  activated() { this.activate() },
  deactivated() { this.leave() },
  beforeDestroy() { this.leave() },
  beforeRouteLeave(to, from, next) { this.leave(); next() },
  methods: {
    supports: streamCapabilitySupported,
    activate() { if (!this.viewActive) { this.viewActive = true; this.load() } },
    leave() { this.viewActive = false; this.generation++; this.loading = false; this.submitting = false },
    async load() {
      const ticket = ++this.generation
      this.loading = true; this.error = ''
      try {
        const [capabilities, sources] = await Promise.all([listCapabilities(), listStreamSources()])
        if (!this.viewActive || ticket !== this.generation) return
        this.capabilities = capabilities; this.sources = sources
        const capability = capabilities.find(c => c.available && streamCapabilitySupported(c))
        if (!this.draft) this.selectedCode = capability ? capability.code : ''
        if (!this.draft && (!this.source || !this.source.available)) {
          const source = sources.find(item => item.available)
          this.sourceId = source ? source.streamSourceId : ''
        }
      } catch (error) { if (this.viewActive && ticket === this.generation) this.error = errorMessage(error) } finally { if (ticket === this.generation) this.loading = false }
    },
    reset() { this.generation++; this.draft = null; this.parameters = defaults(); this.error = '' },
    valid() {
      const p = this.parameters
      return Number.isInteger(p.maxEventsPerPoll) && p.maxEventsPerPoll >= 1 && p.maxEventsPerPoll <= 200 &&
        Number.isInteger(p.pollIntervalMillis) && p.pollIntervalMillis >= 250 && p.pollIntervalMillis <= 30000 &&
        typeof p.includeSnapshots === 'boolean'
    },
    async start() {
      if (!this.canStart && !this.draft) return
      if (!this.valid()) { this.error = '请检查事件数与查询间隔范围'; return }
      if (!this.draft) {
        this.draft = { key: key(),
          request: { capabilityCode: 'video-stream-analysis.v1',
            streamSourceId: this.source.streamSourceId,
            parameters: { ...this.parameters } } }
      }
      const ticket = ++this.generation
      this.submitting = true; this.error = ''
      try {
        const session = await startStreamSession(this.draft.request, this.draft.key)
        if (!this.viewActive || ticket !== this.generation) return
        this.draft = null
        this.$router.push({ name: 'AiStreamSession', params: { sessionId: session.sessionId } })
      } catch (error) { if (this.viewActive && ticket === this.generation) this.error = errorMessage(error) } finally { if (ticket === this.generation) this.submitting = false }
    }
  }
}
</script>
