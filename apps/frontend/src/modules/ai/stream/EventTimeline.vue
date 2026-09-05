<template>
  <section>
    <a-empty v-if="!events.length" description="尚无事件" />
    <a-timeline v-else>
      <a-timeline-item v-for="event in events" :key="event.eventId">
        <strong>{{ event.eventType }}</strong> · {{ offset(event.offsetMillis) }}
        <span v-if="event.score != null"> · 置信度 {{ event.score }}</span>
        <span v-if="event.occurredAt"> · {{ event.occurredAt }}</span>
        <a-button v-if="event.snapshotAssetId" size="small" style="margin-left: 8px" @click="$emit('snapshot', event.snapshotAssetId)">查看截图</a-button>
      </a-timeline-item>
    </a-timeline>
  </section>
</template>
<script>
import { formatOffset } from '@/modules/ai/result/presentation'
export default { name: 'EventTimeline', props: { events: { type: Array, default: () => [] } }, methods: { offset: formatOffset } }
</script>
