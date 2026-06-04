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
          <p class="muted-line">审批处理结果按任务留痕，便于后续接入操作日志、催办、转签和完整审计链路。</p>
        </div>
        <el-button size="small" :loading="loading" @click="loadTasks">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="pageData.records" stripe>
        <el-table-column label="任务" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="workflow-name-cell">
              <strong>{{ row.stepName }}</strong>
              <small>实例 #{{ row.instanceId }} · 步骤 {{ row.stepIndex + 1 }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="taskStatusTag(row.status)" effect="plain">{{ taskStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理人" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.assigneeUsername || row.assigneeUserId || '-' }}</template>
        </el-table-column>
        <el-table-column label="意见" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.comment || '-' }}</template>
        </el-table-column>
        <el-table-column label="完成时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.completedAt) }}</template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无已办任务" />
        </template>
      </el-table>

      <div class="footer-bar">
        <span>共 {{ pageData.total }} 条已办</span>
        <el-pagination
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

    <el-drawer v-model="detailVisible" title="已办详情" size="560px">
      <template v-if="detailItem">
        <el-descriptions :column="2" border class="drawer-section">
          <el-descriptions-item label="任务 ID">{{ detailItem.id }}</el-descriptions-item>
          <el-descriptions-item label="实例 ID">{{ detailItem.instanceId }}</el-descriptions-item>
          <el-descriptions-item label="步骤名称">{{ detailItem.stepName }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ taskStatusText(detailItem.status) }}</el-descriptions-item>
          <el-descriptions-item label="处理人">{{ detailItem.assigneeUsername || detailItem.assigneeUserId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ formatDateTime(detailItem.completedAt) }}</el-descriptions-item>
          <el-descriptions-item label="处理意见" :span="2">{{ detailItem.comment || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type { TagProps } from 'element-plus'
import { queryWorkflowDoneTasks } from '@/api/modules'
import type { PageResult } from '@/types/api'
import type { WorkflowTaskView } from '@/types/workflow'
import { formatDateTime } from '@/utils/datetime'

const loading = ref(false)
const detailVisible = ref(false)
const detailItem = ref<WorkflowTaskView | null>(null)

const query = reactive({
  page: 1,
  size: 20,
})

const pageData = ref<PageResult<WorkflowTaskView>>({ total: 0, page: 1, size: 20, records: [] })
const approvedCount = computed(() => pageData.value.records.filter((item) => item.status === 'APPROVED').length)
const rejectedCount = computed(() => pageData.value.records.filter((item) => item.status === 'REJECTED').length)

void loadTasks()

async function loadTasks() {
  loading.value = true
  try {
    pageData.value = await queryWorkflowDoneTasks({ page: query.page, size: query.size })
  } finally {
    loading.value = false
  }
}

async function handlePageChange(nextPage: number) {
  query.page = nextPage
  await loadTasks()
}

async function handleSizeChange(nextSize: number) {
  query.size = nextSize
  query.page = 1
  await loadTasks()
}

function openDetail(row: WorkflowTaskView) {
  detailItem.value = row
  detailVisible.value = true
}

function taskStatusText(status: string) {
  return ({ PENDING: '待处理', APPROVED: '已通过', REJECTED: '已驳回', CANCELLED: '已取消', TRANSFERRED: '已转签' } as Record<string, string>)[status] ?? status
}

function taskStatusTag(status: string): TagProps['type'] {
  if (status === 'APPROVED') {
    return 'success'
  }
  if (status === 'PENDING') {
    return 'warning'
  }
  if (status === 'REJECTED') {
    return 'danger'
  }
  return 'info'
}
</script>

<style scoped lang="scss">
.workflow-page {
  position: relative;
}

.workflow-stat--primary {
  background:
    linear-gradient(135deg, rgba(22, 119, 255, 0.14), rgba(20, 184, 166, 0.1)),
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
    color: var(--text-soft);
    font-size: 12px;
  }
}
</style>