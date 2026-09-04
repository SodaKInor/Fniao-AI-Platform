<template>
  <section>
    <a-alert v-if="!supported" type="warning" show-icon message="当前页面不支持此视频成果格式" />
    <template v-else>
      <event-timeline :events="result.events" @snapshot="selectedId = $event" />
      <authorized-image
        v-if="selectedId"
        :key="selectedId"
        :asset-id="selectedId"
        :load-asset="loadSnapshot"
        :describe-error="describeError"
        style="margin-top: 16px" />
      <p v-if="!result.events.length">此视频没有检测到事件。</p>
      <div v-if="result.annotatedVideo" style="margin-top: 16px">
        <p>{{ result.annotatedVideo.fileName }} · {{ result.annotatedVideo.sizeBytes }} 字节</p>
        <a-button :loading="downloading" @click="downloadVideo">下载标注视频</a-button>
      </div>
    </template>
    <a-alert v-if="error" type="error" show-icon :message="error" style="margin-top: 16px" />
  </section>
</template>
<script>
import EventTimeline from './EventTimeline'
import AuthorizedImage from './AuthorizedImage'
import { supportedVideoResult } from '@/services/ai/presentation'
export default {
  name: 'VideoResultPreview',
  components: { EventTimeline, AuthorizedImage },
  props: { result: { type: Object, required: true },
    loadAsset: { type: Function, required: true },
    describeError: { type: Function, required: true } },
  data: () => ({ selectedId: '', downloading: false, error: '' }),
  computed: { supported() { return supportedVideoResult(this.result) } },
  created() { this.generation = 0; this.urls = new Map() },
  watch: { result() { this.release() } },
  deactivated() { this.release() },
  beforeDestroy() { this.release() },
  methods: {
    loadSnapshot(id) {
      const asset = this.result.snapshots.find(item => item.assetId === id)
      if (!asset) return Promise.reject(new Error('截图记录不存在'))
      return this.loadAsset(asset)
    },
    release() {
      this.generation++
      this.urls.forEach((timer, url) => { clearTimeout(timer); URL.revokeObjectURL(url) })
      this.urls.clear(); this.selectedId = ''; this.downloading = false; this.error = ''
    },
    async downloadVideo() {
      if (this.downloading || !this.supported || !this.result.annotatedVideo) return
      const ticket = this.generation
      this.downloading = true; this.error = ''
      try {
        const asset = this.result.annotatedVideo
        const blob = await this.loadAsset(asset)
        if (ticket !== this.generation) return
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url; link.download = asset.fileName
        document.body.appendChild(link); link.click(); link.remove()
        this.urls.set(url, setTimeout(() => { URL.revokeObjectURL(url); this.urls.delete(url) }, 1000))
      } catch (error) { if (ticket === this.generation) this.error = this.describeError(error) } finally { if (ticket === this.generation) this.downloading = false }
    }
  }
}
</script>
