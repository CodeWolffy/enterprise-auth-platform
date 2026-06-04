<template>
  <div class="panel-stack workflow-page">
    <section class="dashboard-grid">
      <article class="stat-card workflow-stat workflow-stat--primary">
        <span class="eyebrow">Todo</span>
        <strong>{{ pageData.total }}</strong>
        <span>我的待办总数</span>
      </article>
      <article class="stat-card workflow-stat">
        <span class="eyebrow">Actionable</span>
        <strong>{{ actionableCount }}</strong>
        <span>当前页可处理</span>
      </article>
    </section>

    <section class="dashboard-panel workflow-console">
      <div class="panel-head">
        <div>
          <span class="eyebrow">我的待办</span>
          <h3>审批任务</h3>
          <p class="muted-line">候选人和候选组由后端基于当前登录态、权限和租户上下文判断，前端只展示可处理结果。</p>
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
        <el-table-column label="候选范围" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ formatCandidates(row) }}</template>
        </el-table-column>
        <el-table-column prop="tenantId" label="租户" width="130" />
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" width="280">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button :disabled="!row.actionable || submitting" link type="success" @click="openAction(row, 'approve')">通过</el-button>
            <el-button :disabled="!row.actionable || submitting" link type="warning" @click="openTransfer(row)">转签</el-button>
            <el-button :disabled="!row.actionable || submitting" link type="danger" @click="openAction(row, 'reject')">驳回</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无待办任务" />
        </template>
      </el-table>

      <div class="footer-bar">
        <span>共 {{ pageData.total }} 条待办</span>
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

    <el-dialog v-model="actionVisible" :title="actionType === 'approve' ? '审批通过' : '驳回任务'" width="520px">
      <el-form label-position="top">
        <el-form-item label="处理意见">
          <el-input v-model="comment" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actionVisible = false">取消</el-button>
        <el-button :type="actionType === 'approve' ? 'success' : 'danger'" :loading="submitting" @click="submitAction">
          {{ actionType === 'approve' ? '确认通过' : '确认驳回' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="transferVisible" title="转签任务" width="520px">
      <el-alert
        title="转签后当前待办会关闭，并为目标用户生成同一审批节点的新待办。"
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom: 14px"
      />
      <el-form label-position="top">
        <el-form-item label="目标用户 ID" required>
          <el-input-number v-model="transferForm.targetUserId" :min="1" :precision="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="转签说明">
          <el-input v-model="transferForm.comment" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="warning" :loading="submitting" @click="submitTransfer">确认转签</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="任务详情" size="560px">
      <template v-if="detailItem">
        <el-descriptions :column="2" border class="drawer-section">
          <el-descriptions-item label="任务 ID">{{ detailItem.id }}</el-descriptions-item>
          <el-descriptions-item label="实例 ID">{{ detailItem.instanceId }}</el-descriptions-item>
          <el-descriptions-item label="步骤名称">{{ detailItem.stepName }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="taskStatusTag(detailItem.status)" effect="plain">{{ taskStatusText(detailItem.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="候选范围" :span="2">
            <div class="candidate-detail">
              <el-tag v-for="userId in detailItem.candidateUserIds" :key="`user-${userId}`" effect="plain">用户 {{ userId }}</el-tag>
              <el-tag v-for="groupCode in detailItem.candidateGroupCodes" :key="`group-${groupCode}`" type="success" effect="plain">{{ groupCode }}</el-tag>
              <span v-if="!detailItem.candidateUserIds.length && !detailItem.candidateGroupCodes.length" class="muted-inline">未配置候选范围</span>
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(detailItem.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="可处理">
            <el-tag :type="detailItem.actionable ? 'success' : 'info'" effect="plain">{{ detailItem.actionable ? '是' : '否' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="处理意见" :span="2">{{ detailItem.comment || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type { TagProps } from 'element-plus'
import { ElMessage } from 'element-plus'
import { approveWorkflowTask, queryWorkflowTodoTasks, rejectWorkflowTask, transferWorkflowTask } from '@/api/modules'
import type { PageResult } from '@/types/api'
import type { WorkflowTaskView } from '@/types/workflow'
import { formatDateTime } from '@/utils/datetime'

const loading = ref(false)
const submitting = ref(false)
const actionVisible = ref(false)
const transferVisible = ref(false)
const detailVisible = ref(false)
const actionType = ref<'approve' | 'reject'>('approve')
const currentTask = ref<WorkflowTaskView | null>(null)
const detailItem = ref<WorkflowTaskView | null>(null)
const comment = ref('')
const transferForm = reactive({
  targetUserId: undefined as number | undefined,
  comment: '',
})

const query = reactive({
  page: 1,
  size: 20,
})

const pageData = ref<PageResult<WorkflowTaskView>>({ total: 0, page: 1, size: 20, records: [] })
const actionableCount = computed(() => pageData.value.records.filter((item) => item.actionable).length)

void loadTasks()

async function loadTasks() {
  loading.value = true
  try {
    pageData.value = await queryWorkflowTodoTasks({ page: query.page, size: query.size })
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

function openAction(row: WorkflowTaskView, type: 'approve' | 'reject') {
  currentTask.value = row
  actionType.value = type
  comment.value = ''
  actionVisible.value = true
}

function openTransfer(row: WorkflowTaskView) {
  currentTask.value = row
  transferForm.targetUserId = undefined
  transferForm.comment = ''
  transferVisible.value = true
}

async function submitAction() {
  if (!currentTask.value) {
    return
  }
  submitting.value = true
  try {
    if (actionType.value === 'approve') {
      await approveWorkflowTask(currentTask.value.id, comment.value.trim() || undefined)
      ElMessage.success('审批已通过')
    } else {
      await rejectWorkflowTask(currentTask.value.id, comment.value.trim() || undefined)
      ElMessage.success('任务已驳回')
    }
    actionVisible.value = false
    currentTask.value = null
    await loadTasks()
  } finally {
    submitting.value = false
  }
}

async function submitTransfer() {
  if (!currentTask.value) {
    return
  }
  if (!transferForm.targetUserId) {
    ElMessage.warning('请输入目标用户 ID')
    return
  }
  submitting.value = true
  try {
    await transferWorkflowTask(currentTask.value.id, {
      targetUserId: transferForm.targetUserId,
      comment: transferForm.comment.trim() || undefined,
    })
    ElMessage.success('任务已转签')
    transferVisible.value = false
    currentTask.value = null
    await loadTasks()
  } finally {
    submitting.value = false
  }
}

function formatCandidates(row: WorkflowTaskView) {
  const users = row.candidateUserIds.length ? `候选人：${row.candidateUserIds.join(', ')}` : ''
  const groups = row.candidateGroupCodes.length ? `候选组：${row.candidateGroupCodes.join(', ')}` : ''
  return [users, groups].filter(Boolean).join('；') || '未配置候选范围'
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
.candidate-detail {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.muted-inline {
  color: var(--text-soft);
}
</style>