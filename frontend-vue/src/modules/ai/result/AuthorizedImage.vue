<template>
  <section v-if="assetId">
    <a-button :loading="busy" @click="load">加载授权截图</a-button>
    <img v-if="url" :src="url" alt="授权事件截图" style="max-width: 100%; max-height: 480px; display: block; margin-top: 12px">
    <a-alert v-if="error" type="error" show-icon :message="error" style="margin-top: 12px" />
  </section>
</template>
<script>
export default {
  name: 'AuthorizedImage',
  props: { assetId: { type: String, default: '' },
    loadAsset: { type: Function, required: true },
    describeError: { type: Function, required: true } },
  data: () => ({ url: '', busy: false, error: '' }),
  watch: { assetId() { this.release() } },
  created() { this.generation = 0 },
  deactivated() { this.release() },
  beforeDestroy() { this.release() },
  methods: {
    release() {
      this.generation++
      if (this.url) URL.revokeObjectURL(this.url)
      this.url = ''; this.busy = false; this.error = ''
    },
    async load() {
      if (!this.assetId || this.busy) return
      const ticket = ++this.generation
      this.busy = true; this.error = ''
      try {
        const blob = await this.loadAsset(this.assetId)
        if (ticket !== this.generation) return
        if (this.url) URL.revokeObjectURL(this.url)
        this.url = URL.createObjectURL(blob)
      } catch (error) { if (ticket === this.generation) this.error = this.describeError(error) } finally { if (ticket === this.generation) this.busy = false }
    }
  }
}
</script>
