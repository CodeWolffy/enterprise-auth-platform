<script setup lang="ts">
import type { TagProps } from 'element-plus';

import type { PageResult } from '#/types/api';
import type { WorkflowTaskUrgeView, WorkflowTaskView } from '#/types/workflow';

import { computed, nextTick, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import {
  ElAlert,
  ElBadge,
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElDrawer,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElPagination,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  approveWorkflowTask,
  listWorkflowTaskUrges,
  queryWorkflowTodoTasks,
  rejectWorkflowTask,
  transferWorkflowTask,
  urgeWorkflowTask,
} from '#/api/modules';
import { formatDateTime as formatInstantDateTime } from '#/utils/datetime';

const route = useRoute();
const loading = ref(false);
const submitting = ref(false);
const urging = ref(false);
const actionVisible = ref(false);
const transferVisible = ref(false);
const detailVisible = ref(false);
const urgeVisible = ref(false);
const urgeTask = ref<null | WorkflowTaskView>(null);
const urgeHistory = ref<WorkflowTaskUrgeView[]>([]);
const urgeComment = ref('');
const actionType = ref<'approve' | 'reject'>('approve');
const currentTask = ref<null | WorkflowTaskView>(null);
const detailItem = ref<null | WorkflowTaskView>(null);
const comment = ref('');
const transferForm = reactive({
  targetUserId: undefined as number | undefined,
  comment: '',
});

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
const focusedTaskId = computed(() => normalizeTaskId(route.query.taskId));
const actionableCount = computed(
  () => pageData.value.records.filter((item) => item.actionable).length,
);
const asWorkflowTask = (row: unknown) => row as WorkflowTaskView;

watch(focusedTaskId, async () => {
  query.page = 1;
  await loadTasks();
  await focusTaskFromQuery();
});

void loadTasks().then(focusTaskFromQuery);

async function loadTasks() {
  loading.value = true;
  try {
    pageData.value = await queryWorkflowTodoTasks({
      page: query.page,
      size: query.size,
      taskId: focusedTaskId.value ?? undefined,
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

function normalizeTaskId(value: unknown) {
  const raw = Array.isArray(value) ? value[0] : value;
  const parsed = Number(raw);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}

async function focusTaskFromQuery() {
  const taskId = focusedTaskId.value;
  if (!taskId) {
    return;
  }
  const existsInCurrentPage = pageData.value.records.some(
    (item) => item.id === taskId,
  );
  if (!existsInCurrentPage && query.page !== 1) {
    query.page = 1;
    await loadTasks();
  }
  await nextTick();
  if (pageData.value.records.some((item) => item.id === taskId)) {
    document
      .querySelector('.workflow-task-row--focused')
      ?.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }
}

function taskRowClassName({ row }: { row: WorkflowTaskView }) {
  return focusedTaskId.value === row.id ? 'workflow-task-row--focused' : '';
}

function openDetail(row: WorkflowTaskView) {
  detailItem.value = row;
  detailVisible.value = true;
}

async function openUrgeDialog(row: WorkflowTaskView) {
  urgeTask.value = row;
  urgeComment.value = '';
  urgeVisible.value = true;
  await loadUrgeHistory(row.id);
}

async function openUrgeHistory(row: WorkflowTaskView) {
  urgeTask.value = row;
  urgeVisible.value = true;
  urgeComment.value = '';
  await loadUrgeHistory(row.id);
}

async function loadUrgeHistory(taskId: number) {
  urgeHistory.value = await listWorkflowTaskUrges(taskId);
}

async function submitUrge() {
  if (!urgeTask.value) {
    return;
  }
  urging.value = true;
  try {
    const result = await urgeWorkflowTask(
      urgeTask.value.id,
      urgeComment.value || undefined,
    );
    urgeComment.value = '';
    ElMessage.success(`已催办，累计 ${result.totalUrgeCount} 次`);
    await loadUrgeHistory(urgeTask.value.id);
    await loadTasks();
  } finally {
    urging.value = false;
  }
}

function openAction(row: WorkflowTaskView, type: 'approve' | 'reject') {
  currentTask.value = row;
  actionType.value = type;
  comment.value = '';
  actionVisible.value = true;
}

function openTransfer(row: WorkflowTaskView) {
  currentTask.value = row;
  transferForm.targetUserId = undefined;
  transferForm.comment = '';
  transferVisible.value = true;
}

async function submitAction() {
  if (!currentTask.value) {
    return;
  }
  submitting.value = true;
  try {
    if (actionType.value === 'approve') {
      await approveWorkflowTask(
        currentTask.value.id,
        comment.value.trim() || undefined,
      );
      ElMessage.success('审批已通过');
    } else {
      await rejectWorkflowTask(
        currentTask.value.id,
        comment.value.trim() || undefined,
      );
      ElMessage.success('任务已驳回');
    }
    actionVisible.value = false;
    currentTask.value = null;
    await loadTasks();
  } finally {
    submitting.value = false;
  }
}

async function submitTransfer() {
  if (!currentTask.value) {
    return;
  }
  if (!transferForm.targetUserId) {
    ElMessage.warning('请输入目标用户 ID');
    return;
  }
  submitting.value = true;
  try {
    await transferWorkflowTask(currentTask.value.id, {
      targetUserId: transferForm.targetUserId,
      comment: transferForm.comment.trim() || undefined,
    });
    ElMessage.success('任务已转签');
    transferVisible.value = false;
    currentTask.value = null;
    await loadTasks();
  } finally {
    submitting.value = false;
  }
}

function formatCandidates(row: WorkflowTaskView) {
  const users =
    row.candidateUserIds.length > 0
      ? `候选人：${row.candidateUserIds.join(', ')}`
      : '';
  const groups =
    row.candidateGroupCodes.length > 0
      ? `候选组：${row.candidateGroupCodes.join(', ')}`
      : '';
  return [users, groups].filter(Boolean).join('；') || '未配置候选范围';
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
          <p class="muted-line">
            候选人和候选组由后端基于当前登录态、权限和租户上下文判断，前端只展示可处理结果。
          </p>
        </div>
        <ElButton size="small" :loading="loading" @click="loadTasks">
          刷新
        </ElButton>
      </div>

      <ElTable
        v-loading="loading"
        :data="pageData.records"
        :row-class-name="taskRowClassName"
        stripe
      >
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
        <ElTableColumn label="候选范围" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatCandidates(asWorkflowTask(row)) }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="tenantId" label="租户" width="130" />
        <ElTableColumn label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="催办" width="100" align="center">
          <template #default="{ row }">
            <ElBadge
              v-if="row.urgeCount > 0"
              :value="row.urgeCount"
              :max="99"
              type="danger"
              class="urge-badge"
            >
              <ElButton
                link
                type="warning"
                @click="openUrgeHistory(asWorkflowTask(row))"
              >
                已催
              </ElButton>
            </ElBadge>
            <ElButton
              v-else
              link
              type="info"
              @click="openUrgeHistory(asWorkflowTask(row))"
            >
              催办
            </ElButton>
          </template>
        </ElTableColumn>
        <ElTableColumn fixed="right" label="操作" width="320">
          <template #default="{ row }">
            <ElButton
              link
              type="primary"
              @click="openDetail(asWorkflowTask(row))"
            >
              详情
            </ElButton>
            <ElButton
              :disabled="!row.actionable || submitting"
              link
              type="success"
              @click="openAction(asWorkflowTask(row), 'approve')"
            >
              通过
            </ElButton>
            <ElButton
              :disabled="!row.actionable || submitting"
              link
              type="warning"
              @click="openTransfer(asWorkflowTask(row))"
            >
              转签
            </ElButton>
            <ElButton
              :disabled="!row.actionable || submitting"
              link
              type="danger"
              @click="openAction(asWorkflowTask(row), 'reject')"
            >
              驳回
            </ElButton>
            <ElButton
              :disabled="urging"
              link
              type="info"
              @click="openUrgeDialog(asWorkflowTask(row))"
            >
              催办
            </ElButton>
          </template>
        </ElTableColumn>
        <template #empty>
          <ElEmpty description="暂无待办任务" />
        </template>
      </ElTable>

      <div class="footer-bar">
        <span>共 {{ pageData.total }} 条待办</span>
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

    <ElDialog
      v-model="actionVisible"
      :title="actionType === 'approve' ? '审批通过' : '驳回任务'"
      width="520px"
    >
      <ElForm label-position="top">
        <ElFormItem label="处理意见">
          <ElInput
            v-model="comment"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="actionVisible = false">取消</ElButton>
        <ElButton
          :type="actionType === 'approve' ? 'success' : 'danger'"
          :loading="submitting"
          @click="submitAction"
        >
          {{ actionType === 'approve' ? '确认通过' : '确认驳回' }}
        </ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="transferVisible" title="转签任务" width="520px">
      <ElAlert
        title="转签后当前待办会关闭，并为目标用户生成同一审批节点的新待办。"
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom: 14px"
      />
      <ElForm label-position="top">
        <ElFormItem label="目标用户 ID" required>
          <ElInputNumber
            v-model="transferForm.targetUserId"
            :min="1"
            :precision="0"
            style="width: 100%"
          />
        </ElFormItem>
        <ElFormItem label="转签说明">
          <ElInput
            v-model="transferForm.comment"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="transferVisible = false">取消</ElButton>
        <ElButton type="warning" :loading="submitting" @click="submitTransfer">
          确认转签
        </ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="urgeVisible" title="催办任务" width="520px">
      <ElAlert
        :title="
          urgeTask
            ? `将向 ${urgeTask.assigneeUsername || '当前处理人'} 发起催办提醒`
            : '将向当前处理人发起催办提醒'
        "
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom: 14px"
      />
      <ElForm label-position="top">
        <ElFormItem label="催办说明">
          <ElInput
            v-model="urgeComment"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
          />
        </ElFormItem>
        <ElFormItem label="催办历史">
          <div v-if="urgeHistory.length === 0" class="muted-inline">
            暂无催办记录
          </div>
          <div v-else class="urge-history">
            <div
              v-for="record in urgeHistory"
              :key="record.id"
              class="urge-history-item"
            >
              <div>
                <strong>{{ record.urgedByUsername }}</strong>
                <small>{{ formatDateTime(record.urgedAt) }}</small>
              </div>
              <p>{{ record.comment || '未填写说明' }}</p>
            </div>
          </div>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="urgeVisible = false">关闭</ElButton>
        <ElButton type="primary" :loading="urging" @click="submitUrge">
          确认催办
        </ElButton>
      </template>
    </ElDialog>

    <ElDrawer v-model="detailVisible" title="任务详情" size="560px">
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
            <ElTag :type="taskStatusTag(detailItem.status)" effect="plain">
              {{ taskStatusText(detailItem.status) }}
            </ElTag>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="候选范围" :span="2">
            <div class="candidate-detail">
              <ElTag
                v-for="userId in detailItem.candidateUserIds"
                :key="`user-${userId}`"
                effect="plain"
              >
                用户 {{ userId }}
              </ElTag>
              <ElTag
                v-for="groupCode in detailItem.candidateGroupCodes"
                :key="`group-${groupCode}`"
                type="success"
                effect="plain"
              >
                {{ groupCode }}
              </ElTag>
              <span
                v-if="
                  detailItem.candidateUserIds.length === 0 &&
                  detailItem.candidateGroupCodes.length === 0
                "
                class="muted-inline"
                >未配置候选范围</span
              >
            </div>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="创建时间">
            {{ formatDateTime(detailItem.createdAt) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="可处理">
            <ElTag
              :type="detailItem.actionable ? 'success' : 'info'"
              effect="plain"
            >
              {{ detailItem.actionable ? '是' : '否' }}
            </ElTag>
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

:deep(.workflow-task-row--focused) {
  --el-table-tr-bg-color: #fff7e6;

  td {
    background-color: #fff7e6 !important;
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

.urge-badge {
  display: inline-flex;
}

.urge-history {
  display: grid;
  gap: 10px;
  max-height: 260px;
  overflow: auto;
}

.urge-history-item {
  padding: 10px 12px;
  background: var(--bg-card-muted);
  border: 1px solid var(--line);
  border-radius: 12px;

  div {
    display: flex;
    gap: 12px;
    align-items: center;
    justify-content: space-between;
  }

  small {
    color: var(--text-soft);
  }

  p {
    margin: 8px 0 0;
    line-height: 1.6;
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
