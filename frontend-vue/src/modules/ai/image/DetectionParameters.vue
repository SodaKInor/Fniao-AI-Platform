<template>
  <a-row :gutter="24">
    <a-col :xs="24" :sm="8">
      <label>检测阈值</label><br>
      <a-input-number
        aria-label="检测阈值"
        :value="value.threshold"
        :min="0"
        :max="1"
        :step="0.1"
        :disabled="disabled"
        @change="change('threshold', $event)" />
    </a-col>
    <a-col :xs="24" :sm="8">
      <label>最大检测数</label><br>
      <a-input-number
        aria-label="最大检测数"
        :value="value.maxDetections"
        :min="1"
        :max="100"
        :precision="0"
        :disabled="disabled"
        @change="change('maxDetections', $event)" />
    </a-col>
    <a-col :xs="24" :sm="8">
      <label>生成标注图片</label><br>
      <a-switch aria-label="生成标注图片" :checked="value.annotate" :disabled="disabled" @change="change('annotate', $event)" />
    </a-col>
  </a-row>
</template>

<script>
export default {
  name: 'DetectionParameters',
  props: { value: { type: Object, required: true }, disabled: Boolean },
  methods: { change(key, value) { this.$emit('input', { ...this.value, [key]: value }) } }
}
</script>
