<template>
  <a-row :gutter="16">
    <a-col :xs="24" :sm="5"><label>检测阈值</label><br><a-input-number
      aria-label="视频检测阈值"
      :value="value.threshold"
      :min="0"
      :max="1"
      :step="0.1"
      :disabled="disabled"
      @change="change('threshold', $event)" /></a-col>
    <a-col :xs="24" :sm="5"><label>采样间隔（毫秒）</label><br><a-input-number
      aria-label="采样间隔"
      :value="value.sampleIntervalMillis"
      :min="100"
      :max="60000"
      :precision="0"
      :disabled="disabled"
      @change="change('sampleIntervalMillis', $event)" /></a-col>
    <a-col :xs="24" :sm="5"><label>最大事件数</label><br><a-input-number
      aria-label="最大事件数"
      :value="value.maxEvents"
      :min="1"
      :max="1000"
      :precision="0"
      :disabled="disabled"
      @change="change('maxEvents', $event)" /></a-col>
    <a-col :xs="12" :sm="4"><label>保存截图</label><br><a-switch aria-label="保存视频截图" :checked="value.includeSnapshots" :disabled="disabled" @change="change('includeSnapshots', $event)" /></a-col>
    <a-col :xs="12" :sm="4"><label>生成标注视频</label><br><a-switch aria-label="生成标注视频" :checked="value.annotate" :disabled="disabled" @change="change('annotate', $event)" /></a-col>
  </a-row>
</template>
<script>
export default { name: 'VideoParameters',
props: { value: { type: Object, required: true }, disabled: Boolean },
  methods: { change(key, value) { this.$emit('input', { ...this.value, [key]: value }) } } }
</script>
