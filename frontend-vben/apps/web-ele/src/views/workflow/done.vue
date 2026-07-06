<script setup lang="ts">
import type { TagProps } from 'element-plus';

import type { PageResult } from '#/types/api';
import type { WorkflowTaskView } from '#/types/workflow';

import { computed, reactive, ref } from 'vue';

import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElEmpty,
  ElPagination,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { queryWorkflowDoneTasks } from '#/api/workflow';
import { formatDateTime as formatInstantDateTime } from '#/utils/datetime';

const loading = ref(false);
const detailVisible = ref(false);
const detailItem = ref<null | WorkflowTaskView>(null);

const query = reactive({
  page: 1,
  size: 20,
});

const pageData = ref<PageResult<WorkflowTaskView>>({
  total: 0,
  page: 1,
  size: 20,
  records: [],
});
const approvedCount = computed(
  () =>
    pageData.value.records.filter((item) => item.status === 'APPROVED').length,
);
const rejectedCount = computed(
  () =>
    pageData.value.records.filter((item) => item.status === 'REJECTED').length,
);
const asWorkflowTask = (row: unknown) => row as WorkflowTaskView;

void loadTasks();

async function loadTasks() {
  loading.value = true;
  try {
    pageData.value = await queryWorkflowDoneTasks({
      page: query.page,
      size: query.size,
    });
  } finally {
    loading.value = false;
  }
}

async function handlePageChange(nextPage: number) {
  query.page = nextPage;
  await loadTasks();
}

async function handleSizeChange(nextSize: number) {
  query.size = nextSize;
  query.page = 1;
  await loadTasks();
}

function openDetail(row: WorkflowTaskView) {
  detailItem.value = row;
  detailVisible.value = true;
}

function taskStatusText(status: string) {
  return (
    (
      {
        PENDING: '待处理',
        APPROVED: '已通过',
        REJECTED: '已驳回',
        CANCELLED: '已取消',
        TRANSFERRED: '已转签',
      } as Record<string, string>
    )[status] ?? status
  );
}

function taskStatusTag(status: string): TagProps['type'] {
  if (status === 'APPROVED') {
    return 'success';
  }
  if (status === 'PENDING') {
    return 'warning';
  }
  if (status === 'REJECTED') {
    return 'danger';
  }
  return 'info';
}

function formatDateTime(value?: null | string) {
  return formatInstantDateTime(value);
}
</script>

<template>
  <div class="panel-stack workflow-page">
    <section class="dashboard-grid">
      <article class="stat-card workflow-stat workflow-stat--primary">
        <span class="eyebrow">Done</span>
        <strong>{{ pageData.total }}</strong>
        <span>我的已办总数</span>
      </article>
      <article class="stat-card workflow-stat">
        <span class="eyebrow">Approved</span>
        <strong>{{ approvedCount }}</strong>
        <span>当前页已通过</span>
      </article>
      <article class="stat-card workflow-stat">
        <span class="eyebrow">Rejected</span>
        <strong>{{ rejectedCount }}</strong>
        <span>当前页已驳回</span>
      </article>
    </section>

    <section class="dashboard-panel workflow-console">
      <div class="panel-head">
        <div>
          <span class="eyebrow">我的已办</span>
          <h3>审批记录</h3>
          <p class="muted-line">
            审批处理结果按任务留痕，集中呈现处理人、意见、完成时间和实例关联。
          </p>
        </div>
        <ElButton size="small" :loading="loading" @click="loadTasks">
          刷新
        </ElButton>
      </div>

      <ElTable v-loading="loading" :data="pageData.records" stripe>
        <ElTableColumn label="任务" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="workflow-name-cell">
              <strong>{{ row.stepName }}</strong>
              <small
                >实例 #{{ row.instanceId }} · 步骤
                {{ row.stepIndex + 1 }}</small
              >
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="110">
          <template #default="{ row }">
            <ElTag :type="taskStatusTag(row.status)" effect="plain">
              {{ taskStatusText(row.status) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="处理人" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.assigneeUsername || row.assigneeUserId || '-' }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="意见" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.comment || '-' }}</template>
        </ElTableColumn>
        <ElTableColumn label="完成时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.completedAt) }}
          </template>
        </ElTableColumn>
        <ElTableColumn fixed="right" label="操作" width="100">
          <template #default="{ row }">
            <ElButton
              link
              type="primary"
              @click="openDetail(asWorkflowTask(row))"
            >
              详情
            </ElButton>
          </template>
        </ElTableColumn>
        <template #empty>
          <ElEmpty description="暂无已办任务" />
        </template>
      </ElTable>

      <div class="footer-bar">
        <span>共 {{ pageData.total }} 条已办</span>
        <ElPagination
          background
          layout="sizes, prev, pager, next"
          :current-page="query.page"
          :page-size="query.size"
          :page-sizes="[10, 20, 50]"
          :total="pageData.total"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <ElDrawer v-model="detailVisible" title="已办详情" size="560px">
      <template v-if="detailItem">
        <ElDescriptions :column="2" border class="drawer-section">
          <ElDescriptionsItem label="任务 ID">
            {{ detailItem.id }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="实例 ID">
            {{ detailItem.instanceId }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="步骤名称">
            {{ detailItem.stepName }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="状态">
            {{ taskStatusText(detailItem.status) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="处理人">
            {{
              detailItem.assigneeUsername || detailItem.assigneeUserId || '-'
            }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="完成时间">
            {{ formatDateTime(detailItem.completedAt) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="处理意见" :span="2">
            {{ detailItem.comment || '-' }}
          </ElDescriptionsItem>
        </ElDescriptions>
      </template>
    </ElDrawer>
  </div>
</template>

<style scoped lang="scss">
.workflow-page {
  position: relative;
}

.workflow-stat--primary {
  background:
    linear-gradient(135deg, rgb(22 119 255 / 14%), rgb(20 184 166 / 10%)),
    var(--bg-card);
}

.workflow-console {
  min-height: 560px;
}

.muted-line {
  margin: 8px 0 0;
  color: var(--text-soft);
}

.workflow-name-cell {
  display: grid;
  gap: 4px;

  small {
    font-size: 12px;
    color: var(--text-soft);
  }
}

.footer-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
}

@media (max-width: 860px) {
  .footer-bar {
    display: grid;
  }
}
</style>
