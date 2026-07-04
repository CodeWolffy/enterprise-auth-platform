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

import { getPage } from '#/api/upms/sys-login-log';
import { formatDateTime } from '#/utils/datetime';
import { loginStatusMeta } from '#/utils/log-status';

const props = defineProps<{
  userName?: string;
}>();

const loading = ref(false);
const query = reactive({
  userName: '',
  clientIp: '',
  status: '',
  current: 1,
  size: 10,
});
const page = ref<{ records: any[]; total: number }>({ total: 0, records: [] });

const resolvedUserName = computed(
  () => props.userName?.trim() || query.userName.trim(),
);

async function load() {
  loading.value = true;
  try {
    const response: any = await getPage({
      page: query.current,
      size: query.size,
      userName: resolvedUserName.value || undefined,
      clientIp: query.clientIp || undefined,
      status: query.status || undefined,
    });
    page.value = {
      total: response?.total ?? 0,
      records: response?.records ?? [],
    };
  } catch {
    ElMessage.error('登录日志加载失败');
  } finally {
    loading.value = false;
  }
}

function reset() {
  query.userName = props.userName?.trim() || '';
  query.clientIp = '';
  query.status = '';
  query.current = 1;
  void load();
}

onMounted(() => {
  query.userName = props.userName?.trim() || '';
  void load();
});
</script>

<template>
  <div class="user-log-panel">
    <div class="toolbar">
      <ElInput
        v-model="query.userName"
        placeholder="登录用户"
        clearable
        style="width: 180px"
      />
      <ElInput
        v-model="query.clientIp"
        placeholder="登录地址"
        clearable
        style="width: 180px"
      />
      <ElSelect
        v-model="query.status"
        placeholder="操作状态"
        clearable
        style="width: 140px"
      >
        <ElOption label="成功" value="SUCCESS" />
        <ElOption label="失败" value="FAILED" />
        <ElOption label="锁定" value="LOCKED" />
      </ElSelect>
      <ElButton type="primary" @click="load">搜索</ElButton>
      <ElButton @click="reset">重置</ElButton>
    </div>

    <ElTable v-loading="loading" :data="page.records" border>
      <ElTableColumn prop="userName" label="登录用户" />
      <ElTableColumn prop="ipAddr" label="登录地址" />
      <ElTableColumn prop="location" label="登录地点" />
      <ElTableColumn label="登录时间" width="180">
        <template #default="scope">
          {{ formatDateTime(scope.row.createdAt) }}
        </template>
      </ElTableColumn>
      <ElTableColumn prop="browser" label="浏览器" />
      <ElTableColumn prop="os" label="操作系统" />
      <ElTableColumn label="操作状态">
        <template #default="scope">
          <ElTag :type="loginStatusMeta(scope.row.status).type">
            {{ loginStatusMeta(scope.row.status).label }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="msg" label="操作描述" show-overflow-tooltip />
      <template #empty>
        <ElEmpty description="暂无登录日志" />
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
