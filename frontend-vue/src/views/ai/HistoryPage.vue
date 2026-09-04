<template>
  <a-card title="任务历史">
    <router-link to="/ai/inference">新建任务</router-link>
    <router-link to="/ai/video" style="margin-left: 16px">新建视频任务</router-link>
    <a-select v-model="state" aria-label="筛选任务状态" style="width: 180px; margin: 0 16px" @change="refresh">
      <a-select-option value="">全部状态</a-select-option>
      <a-select-option v-for="(label, value) in labels" :key="value" :value="value">{{ label }}</a-select-option>
    </a-select>
    <a-button :loading="loading" @click="refresh">刷新历史</a-button>
    <a-alert v-if="error" type="error" show-icon :message="error" style="margin-top: 16px" />
    <a-list :data-source="items" :loading="loading" style="margin-top: 16px">
      <a-list-item slot="renderItem" slot-scope="job">
        <a-list-item-meta>
          <router-link slot="title" :to="{ name: 'AiJobDetail', params: { requestId: job.requestId } }">{{ job.requestId }}</router-link>
          <span slot="description">{{ typeLabel(job) }} · {{ job.capabilityCode }} ／ {{ job.capabilityVersion }} · {{ job.createdAt }}</span>
        </a-list-item-meta>
        <a-tag v-if="job.simulated" color="purple">模拟</a-tag>
        <span>{{ labels[job.state] || '未支持的状态' }}</span>
      </a-list-item>
    </a-list>
    <a-button v-if="nextCursor" :loading="loading" @click="loadMore">加载更多</a-button>
  </a-card>
</template>

<script>
import { listJobs } from '@/api/ai'
import { stateLabels, errorMessage, jobTypeLabel } from '@/services/ai/presentation'
export default {
  name: 'AiHistoryPage',
  data: () => ({ state: '',
    labels: stateLabels,
    items: [],
    nextCursor: null,
    loading: false,
    error: '',
    viewActive: false }),
  created() { this.generation = 0 },
  mounted() { this.activate() },
  activated() { this.activate() },
  deactivated() { this.leave() },
  beforeDestroy() { this.leave() },
  beforeRouteLeave(to, from, next) { this.leave(); next() },
  methods: {
    typeLabel: jobTypeLabel,
    activate() { if (!this.viewActive) { this.viewActive = true; this.refresh() } },
    leave() { this.viewActive = false; this.generation++; this.loading = false },
    refresh() { this.generation++; this.items = []; this.nextCursor = null; this.fetchPage() },
    loadMore() { if (!this.loading && this.nextCursor) this.fetchPage(this.nextCursor) },
    async fetchPage(cursor) {
      const ticket = this.generation
      this.loading = true; this.error = ''
      try {
        const page = await listJobs({ cursor, state: this.state })
        if (!this.viewActive || ticket !== this.generation) return
        const seen = new Set(this.items.map(job => job.requestId))
        this.items = this.items.concat(page.items.filter(job => !seen.has(job.requestId)))
        this.nextCursor = page.nextCursor || null
      } catch (error) { if (this.viewActive && ticket === this.generation) this.error = errorMessage(error) } finally { if (ticket === this.generation) this.loading = false }
    }
  }
}
</script>
