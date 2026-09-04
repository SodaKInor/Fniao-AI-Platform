<template>
  <section>
    <a-radio-group :value="value" :disabled="disabled" @change="$emit('input', $event.target.value)">
      <a-radio v-for="source in sources" :key="source.streamSourceId" :value="source.streamSourceId" :disabled="!source.available">
        {{ source.displayName }}<span v-if="!source.available">（{{ source.unavailableReason || '当前不可用' }}）</span>
      </a-radio>
    </a-radio-group>
    <a-empty v-if="!sources.length" description="暂无已授权来源" />
  </section>
</template>
<script>
export default { name: 'StreamSourcePanel', props: { sources: { type: Array, default: () => [] }, value: { type: String, default: '' }, disabled: Boolean } }
</script>
