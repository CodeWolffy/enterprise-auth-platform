<script setup lang="ts">
import { computed } from 'vue';

import { ElDescriptions, ElDescriptionsItem } from 'element-plus';

import { formatDateTime } from '#/utils/datetime';
import { operationStatusMeta } from '#/utils/log-status';

const props = defineProps<{
  row?: Record<string, any> | null;
}>();

const detailItems = computed(() => {
  const row = props.row ?? {};
  const status = operationStatusMeta(row.status);
  return [
    { label: '操作用户', value: row.operator || '-' },
    { label: '操作类型', value: row.eventType || '-' },
    { label: '操作地址', value: row.clientIp || '-' },
    { label: '操作地点', value: row.location || '-' },
    { label: '请求方法', value: row.method || '-' },
    { label: '操作状态', value: status.label },
    { label: '请求时长', value: row.requestTime == null ? '-' : `${row.requestTime}ms` },
    { label: '创建时间', value: formatDateTime(row.createdAt) },
    { label: '请求 ID', value: row.requestId || '-' },
    { label: '描述', value: row.msg || '-' },
  ];
});

const payloadText = computed(() => {
  const raw = props.row?.payloadJson || props.row?.paramsJson || props.row?.contentJson || '{}';
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
});
</script>

<template>
  <div class="log-detail-page">
    <el-descriptions :column="2" border>
      <el-descriptions-item v-for="item in detailItems" :key="item.label" :label="item.label">
        {{ item.value }}
      </el-descriptions-item>
    </el-descriptions>

    <div class="payload-block">
      <div class="payload-title">请求载荷</div>
      <pre>{{ payloadText }}</pre>
    </div>
  </div>
</template>

<style scoped lang="scss">
.log-detail-page {
  display: grid;
  gap: 16px;
}

.payload-block {
  padding: 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.payload-title {
  margin-bottom: 10px;
  font-weight: 600;
}

pre {
  margin: 0;
  max-height: 360px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.6;
}
</style>
