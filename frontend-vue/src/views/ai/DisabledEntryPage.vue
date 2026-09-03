<template>
  <a-card :title="$route.meta.title || '功能停用说明'">
    <a-alert type="warning" show-icon message="此入口已停用"
      description="当前版本没有此功能的已验收执行接口，因此不提供运行操作。原有源码和业务数据保留。" />
    <p style="margin-top: 20px">已有模型登记、绑定管理和识别历史可继续从原管理菜单查询。</p>
  </a-card>
</template>

<script>
export default { name: 'DisabledEntryPage' }
</script>
