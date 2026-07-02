<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import {
  ElButton,
  ElEmpty,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getPage } from '#/api/upms/sys-log';
import { formatDateTime } from '#/utils/datetime';
import { operationStatusMeta } from '#/utils/log-status';

const props = defineProps<{
  operator?: string;
}>();

const loading = ref(false);
const query = reactive({
  operator: '',
  clientIp: '',
  status: '',
  current: 1,
  size: 10,
});
const page = ref<{ total: number; records: any[] }>({ total: 0, records: [] });

const resolvedOperator = computed(() => props.operator?.trim() || query.operator.trim());

async function load() {
  loading.value = true;
  try {
    const response: any = await getPage({
      page: query.current,
      size: query.size,
      operator: resolvedOperator.value || undefined,
      clientIp: query.clientIp || undefined,
      status: query.status || undefined,
    });
    page.value = { total: response?.total ?? 0, records: response?.records ?? [] };
  } catch {
    ElMessage.error('操作日志加载失败');
  } finally {
    loading.value = false;
  }
}

function reset() {
  query.operator = props.operator?.trim() || '';
  query.clientIp = '';
  query.status = '';
  query.current = 1;
  void load();
}

onMounted(() => {
  query.operator = props.operator?.trim() || '';
  void load();
});
</script>

<template>
  <div class="user-log-panel">
    <div class="toolbar">
      <ElInput v-model="query.operator" placeholder="操作用户" clearable style="width: 180px" />
      <ElInput v-model="query.clientIp" placeholder="操作地址" clearable style="width: 180px" />
      <ElSelect v-model="query.status" placeholder="操作状态" clearable style="width: 140px">
        <ElOption label="成功" value="1" />
        <ElOption label="失败" value="0" />
      </ElSelect>
      <ElButton type="primary" @click="load">搜索</ElButton>
      <ElButton @click="reset">重置</ElButton>
    </div>

    <ElTable v-loading="loading" :data="page.records" border>
      <ElTableColumn prop="operator" label="操作用户" />
      <ElTableColumn prop="eventType" label="操作类型" />
      <ElTableColumn prop="clientIp" label="操作地址" />
      <ElTableColumn prop="location" label="操作地点" />
      <ElTableColumn prop="method" label="操作方法" />
      <ElTableColumn label="操作状态">
        <template #default="scope">
          <ElTag :type="operationStatusMeta(scope.row.status).type">{{ operationStatusMeta(scope.row.status).label }}</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="requestTime" label="请求时长">
        <template #default="scope">
          {{ scope.row.requestTime == null ? '-' : `${scope.row.requestTime}ms` }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="创建时间" width="180">
        <template #default="scope">
          {{ formatDateTime(scope.row.createdAt) }}
        </template>
      </ElTableColumn>
      <template #empty>
        <ElEmpty description="暂无操作日志" />
      </template>
    </ElTable>
  </div>
</template>

<style scoped lang="scss">
.user-log-panel {
  display: grid;
  gap: 12px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
</style>
