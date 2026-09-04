<template>
  <section>
    <p>选择 MP4（H.264）视频。当前上限：{{ limit }} MiB。</p>
    <input
      ref="file"
      type="file"
      aria-label="上传视频"
      accept="video/mp4"
      :disabled="disabled"
      @change="selectFile">
    <p v-if="asset">已上传：{{ asset.fileName }}（{{ asset.sizeBytes }} 字节）</p>
    <p v-if="uploading">正在上传…</p>
  </section>
</template>
<script>
export default { name: 'VideoUploadPanel',
props: { capability: { type: Object, default: null }, asset: { type: Object, default: null }, disabled: Boolean, uploading: Boolean },
  computed: { limit() { return this.capability ? (this.capability.maxInputBytes / 1048576).toFixed(1) : '—' } },
  methods: { selectFile(event) {
    const file = event.target.files[0]; event.target.value = ''
    if (!file || this.disabled) return
    if (!this.capability || file.type !== 'video/mp4' || !this.capability.inputMediaTypes.includes(file.type)) {
      this.$emit('invalid', '请选择当前能力支持的 MP4（H.264）视频'); return
    }
    if (!file.size || file.size > this.capability.maxInputBytes) { this.$emit('invalid', '文件为空或超过当前能力的上传限制'); return }
    this.$emit('file', file)
  } } }
</script>
