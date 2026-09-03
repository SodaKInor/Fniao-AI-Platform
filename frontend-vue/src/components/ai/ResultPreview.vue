<template>
  <section>
    <a-alert v-if="!supported" type="warning" show-icon message="当前页面不支持此成果格式" />
    <template v-else>
      <detection-result :data="result.data" />
      <div v-for="asset in result.artifacts" :key="asset.assetId" style="margin-top: 16px">
        <p>{{ asset.fileName }} · {{ asset.sizeBytes }} 字节 · 有效期至 {{ asset.expiresAt }}</p>
        <img v-if="previewUrl" :src="previewUrl" alt="标注成果预览" style="max-width: 100%; max-height: 480px; display: block; margin-bottom: 16px">
        <a-button :loading="busy" @click="readAsset(asset, false)">预览成果</a-button>
        <a-button :loading="busy" style="margin-left: 8px" @click="readAsset(asset, true)">下载成果</a-button>
      </div>
      <p v-if="!result.artifacts.length">此任务没有生成成果文件。</p>
    </template>
    <a-alert v-if="error" type="error" show-icon :message="error" style="margin-top: 16px" />
  </section>
</template>

<script>
import DetectionResult from './renderers/DetectionResult'
import { supportedResult } from '@/services/ai/presentation'
export default {
  name: 'ResultPreview',
  components: { DetectionResult },
  props: { result: { type: Object, required: true },
    loadAsset: { type: Function, required: true },
    describeError: { type: Function, required: true } },
  data: () => ({ previewUrl: '', error: '', busy: false }),
  computed: { supported() { return supportedResult(this.result) } },
  watch: { result() { this.release() } },
  created() { this.generation = 0; this.downloadUrls = new Map() },
  deactivated() { this.release() },
  beforeDestroy() { this.release() },
  methods: {
    release() {
      this.generation++
      if (this.previewUrl) URL.revokeObjectURL(this.previewUrl)
      this.downloadUrls.forEach((timer, url) => { clearTimeout(timer); URL.revokeObjectURL(url) })
      this.downloadUrls.clear()
      this.previewUrl = ''; this.error = ''; this.busy = false
    },
    async readAsset(asset, download) {
      if (this.busy || !this.supported) return
      const ticket = this.generation
      this.busy = true; this.error = ''
      try {
        const blob = await this.loadAsset(asset)
        if (ticket !== this.generation) return
        const url = URL.createObjectURL(blob)
        if (download) {
          const link = document.createElement('a')
          link.href = url; link.download = asset.fileName
          document.body.appendChild(link); link.click(); link.remove()
          // Let the browser consume the link before releasing it. Leaving the
          // page still releases outstanding download URLs immediately.
          this.downloadUrls.set(url, setTimeout(() => {
            URL.revokeObjectURL(url); this.downloadUrls.delete(url)
          }, 1000))
        } else {
          if (this.previewUrl) URL.revokeObjectURL(this.previewUrl)
          this.previewUrl = url
        }
      } catch (error) {
        if (ticket === this.generation) this.error = this.describeError(error)
      } finally {
        if (ticket === this.generation) this.busy = false
      }
    }
  }
}
</script>
