<template>
  <a-row :gutter="24">
    <a-col :xs="24" :sm="8"><label>每次最多事件数</label><br><a-input-number
      aria-label="每次最多事件数"
      :value="value.maxEventsPerPoll"
      :min="1"
      :max="200"
      :precision="0"
      :disabled="disabled"
      @change="change('maxEventsPerPoll', $event)" /></a-col>
    <a-col :xs="24" :sm="8"><label>查询间隔（毫秒）</label><br><a-input-number
      aria-label="事件查询间隔"
      :value="value.pollIntervalMillis"
      :min="250"
      :max="30000"
      :precision="0"
      :disabled="disabled"
      @change="change('pollIntervalMillis', $event)" /></a-col>
    <a-col :xs="24" :sm="8"><label>保存截图</label><br><a-switch aria-label="保存实时截图" :checked="value.includeSnapshots" :disabled="disabled" @change="change('includeSnapshots', $event)" /></a-col>
  </a-row>
</template>
<script>
export default { name: 'StreamParameters',
props: { value: { type: Object, required: true }, disabled: Boolean },
  methods: { change(key, value) { this.$emit('input', { ...this.value, [key]: value }) } } }
</script>
