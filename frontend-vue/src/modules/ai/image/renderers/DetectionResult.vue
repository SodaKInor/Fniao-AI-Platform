<template>
  <section>
    <p>图片尺寸：{{ data.imageWidth }} × {{ data.imageHeight }}</p>
    <a-empty v-if="!data.detections.length" description="未检测到目标" />
    <a-table
      v-else
      :data-source="rows"
      :columns="columns"
      :pagination="false"
      size="small"
      row-key="key">
      <span slot="score" slot-scope="value">{{ (value * 100).toFixed(1) }}%</span>
      <span slot="box" slot-scope="box">x={{ box.x }}, y={{ box.y }}, w={{ box.width }}, h={{ box.height }}</span>
    </a-table>
    <p v-if="data.detections.length">位置以原图宽高归一化表示。</p>
  </section>
</template>

<script>
export default {
  name: 'DetectionResult',
  props: { data: { type: Object, required: true } },
  data: () => ({ columns: [
    { title: '目标', dataIndex: 'label' },
    { title: '置信度', dataIndex: 'score', scopedSlots: { customRender: 'score' } },
    { title: '位置', dataIndex: 'box', scopedSlots: { customRender: 'box' } }
  ] }),
  computed: { rows() { return this.data.detections.map((item, key) => ({ ...item, key })) } }
}
</script>
