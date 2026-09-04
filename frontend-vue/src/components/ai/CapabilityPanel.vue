<template>
  <section>
    <a-select aria-label="选择业务能力" :value="value" :disabled="disabled" style="width: 100%" @change="$emit('input', $event)">
      <a-select-option
        v-for="item in capabilities"
        :key="item.code"
        :value="item.code"
        :disabled="!item.available || !supports(item)">
        {{ item.displayName }}{{ item.simulated ? '（模拟）' : '' }}
      </a-select-option>
    </a-select>
    <a-empty v-if="!capabilities.length" description="暂无可用业务能力" />
    <p v-for="item in unavailable" :key="item.code" style="margin-top: 12px">
      {{ item.displayName }}：{{ supports(item) ? item.unavailableReason || '当前不可用' : '当前页面不支持此能力类型' }}
    </p>
  </section>
</template>

<script>
import { capabilitySupported } from '@/services/ai/presentation'
export default {
  name: 'CapabilityPanel',
  props: { capabilities: { type: Array, default: () => [] },
value: { type: String, default: '' },
disabled: Boolean,
    supported: { type: Function, default: capabilitySupported } },
  computed: { unavailable() { return this.capabilities.filter(item => !item.available || !this.supports(item)) } },
  methods: { supports(item) { return this.supported(item) } }
}
</script>
