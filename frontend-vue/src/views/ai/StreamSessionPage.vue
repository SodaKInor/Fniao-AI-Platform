<template>
  <a-card title="实时会话">
    <router-link to="/ai/streams">新建实时会话</router-link>
    <a-divider />
    <a-spin v-if="loading" />
    <section v-if="session">
      <h2>{{ labels[session.state] || '未支持的会话状态' }}</h2>
      <p>会话编号：{{ session.sessionId }}</p><p>来源编号：{{ session.streamSourceId }}</p>
      <p>创建时间：{{ session.createdAt }} ｜ 更新时间：{{ session.updatedAt }}</p>
      <a-alert v-if="session.state === 'STOP_REQUESTED' || session.state === 'UNKNOWN'" type="warning" show-icon message="停止或会话结果尚未确认" description="不会将未确认结果显示为已停止，也不会自动启动新会话。" />
      <a-alert v-if="session.error" type="warning" show-icon :message="describe(session.error)" style="margin-top: 12px" />
      <a-button v-if="canStop" :loading="stopping" style="margin-top: 12px" @click="stopRemote">停止会话</a-button>
    </section>
    <a-alert v-if="error" type="error" show-icon :message="error" style="margin-top: 16px" />
    <a-button v-if="error" style="margin-top: 12px" @click="load">重新查询</a-button>
    <a-divider>事件时间线</a-divider>
    <event-timeline :events="events" @snapshot="selectedId = $event" />
    <authorized-image
      v-if="selectedId"
      :key="selectedId"
      :asset-id="selectedId"
      :load-asset="download"
      :describe-error="describe"
      style="margin-top: 16px" />
  </a-card>
</template>
<script>
import EventTimeline from '@/components/ai/EventTimeline'
import AuthorizedImage from '@/components/ai/AuthorizedImage'
import { getStreamSession, getStreamEvents, stopStreamSession, downloadSnapshotAsset } from '@/api/ai'
import { createStreamPolling, streamTerminalStates } from '@/services/ai/streamPolling'
import { streamStateLabels, errorMessage } from '@/services/ai/presentation'
export default { name: 'AiStreamSessionPage',
  components: { EventTimeline, AuthorizedImage },
  data: () => ({ session: null,
    events: [],
    selectedId: '',
    error: '',
    loading: false,
    stopping: false,
    viewActive: false,
    labels: streamStateLabels }),
  computed: { canStop() { return this.session && !this.stopping && ['PENDING', 'STARTING', 'RUNNING'].includes(this.session.state) } },
  watch: { '$route.params.sessionId'() { if (this.viewActive) this.load() } },
  created() {
    this.polling = createStreamPolling({ getSession: getStreamSession,
      getEvents: getStreamEvents,
      onSession: session => { this.session = session; this.loading = false },
      onEvents: fresh => { this.events = this.events.concat(fresh) },
      onError: error => { this.error = errorMessage(error); this.loading = false } })
  },
  mounted() { this.activate() },
  activated() { this.activate() },
  deactivated() { this.leave() },
  beforeDestroy() { this.leave() },
  beforeRouteLeave(to, from, next) { this.leave(); next() },
  beforeRouteUpdate(to, from, next) { this.polling.stop(); next() },
  methods: {
    download: downloadSnapshotAsset,
    describe: errorMessage,
    activate() { if (!this.viewActive) { this.viewActive = true; this.load() } },
    leave() { this.viewActive = false; this.polling.stop(); this.loading = false; this.stopping = false },
    load() {
      this.polling.stop(); this.error = ''; this.session = null; this.events = []; this.selectedId = ''
      const id = this.$route.params.sessionId
      if (!/^[A-Za-z0-9_-]{1,80}$/.test(id || '')) { this.error = '会话编号无效'; return }
      this.loading = true
      this.polling.start(id)
    },
    async stopRemote() {
      if (!this.canStop) return
      const id = this.session.sessionId
      this.polling.stop(); this.stopping = true; this.error = ''
      try {
        const session = await stopStreamSession(id)
        if (!this.viewActive || this.$route.params.sessionId !== id) return
        this.session = session
        if (!streamTerminalStates.includes(session.state)) this.polling.resume(id)
      } catch (error) {
        if (this.viewActive && this.$route.params.sessionId === id) {
          this.error = errorMessage(error)
          this.polling.resume(id)
        }
      } finally { if (this.viewActive && this.$route.params.sessionId === id) this.stopping = false }
    }
  }
}
</script>
