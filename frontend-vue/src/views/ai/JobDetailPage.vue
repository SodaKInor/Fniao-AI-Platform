<template>
  <a-card title="任务详情">
    <router-link to="/ai/history">任务历史</router-link>
    <router-link to="/ai/inference" style="margin-left: 16px">新建任务</router-link>
    <a-divider />
    <a-spin v-if="loading" />
    <job-status-panel :job="job" />
    <a-alert v-if="error" type="error" show-icon :message="error" style="margin-top: 16px" />
    <a-button v-if="error" style="margin-top: 12px" @click="load">重新查询</a-button>
    <template v-if="job && job.state === 'SUCCEEDED' && job.result">
      <a-divider>成果</a-divider>
      <result-preview :key="job.requestId" :result="job.result" :load-asset="download" :describe-error="describe" />
    </template>
  </a-card>
</template>

<script>
import JobStatusPanel from '@/components/ai/JobStatusPanel'
import ResultPreview from '@/components/ai/ResultPreview'
import { getJob, downloadAsset } from '@/api/ai'
import { createJobPolling } from '@/services/ai/jobPolling'
import { errorMessage } from '@/services/ai/presentation'
export default {
  name: 'AiJobDetailPage',
  components: { JobStatusPanel, ResultPreview },
  data: () => ({ job: null, error: '', loading: false, viewActive: false }),
  watch: { '$route.params.requestId'() { if (this.viewActive) this.load() } },
  created() {
    this.polling = createJobPolling({ getJob,
      onUpdate: job => { this.job = job; this.loading = false },
      onError: error => { this.error = errorMessage(error); this.loading = false } })
  },
  mounted() { this.activate() },
  activated() { this.activate() },
  deactivated() { this.leave() },
  beforeDestroy() { this.leave() },
  beforeRouteLeave(to, from, next) { this.leave(); next() },
  beforeRouteUpdate(to, from, next) { this.polling.stop(); next() },
  methods: {
    download: downloadAsset,
    describe: errorMessage,
    activate() { if (!this.viewActive) { this.viewActive = true; this.load() } },
    leave() { this.viewActive = false; this.polling.stop(); this.loading = false },
    load() {
      this.polling.stop(); this.error = ''; this.job = null
      const id = this.$route.params.requestId
      if (!/^[A-Za-z0-9_-]{1,80}$/.test(id || '')) { this.error = '任务编号无效'; return }
      this.loading = true
      this.polling.start(id)
    }
  }
}
</script>
