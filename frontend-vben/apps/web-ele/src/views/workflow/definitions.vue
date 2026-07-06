<script setup lang="ts">
import type { FormInstance, FormRules, TagProps } from 'element-plus';

import type { PageResult } from '#/types/api';
import type {
  WorkflowDefinitionRequest,
  WorkflowDefinitionView,
  WorkflowStepInput,
  WorkflowStepView,
} from '#/types/workflow';

import { computed, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElDrawer,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  createWorkflowDefinition,
  deployWorkflowDefinition,
  disableWorkflowDefinition,
  queryWorkflowDefinitions,
} from '#/api/workflow';
import { formatDateTime as formatInstantDateTime } from '#/utils/datetime';

const router = useRouter();
const loading = ref(false);
const submitting = ref(false);
const createVisible = ref(false);
const detailVisible = ref(false);
const detailItem = ref<null | WorkflowDefinitionView>(null);
const formRef = ref<FormInstance>();

const query = reactive({
  status: '',
  page: 1,
  size: 20,
});

const pageData = ref<PageResult<WorkflowDefinitionView>>({
  total: 0,
  page: 1,
  size: 20,
  records: [],
});

const form = reactive({
  definitionKey: '',
  definitionName: '',
  stepsText: defaultStepsText(),
  remark: '',
});

const rules = reactive<FormRules>({
  definitionKey: [
    { required: true, message: '请输入定义标识', trigger: 'blur' },
  ],
  definitionName: [
    { required: true, message: '请输入定义名称', trigger: 'blur' },
  ],
  stepsText: [
    { required: true, message: '请输入审批步骤 JSON', trigger: 'blur' },
  ],
});

const deployedCount = computed(
  () =>
    pageData.value.records.filter((item) => item.status === 'DEPLOYED').length,
);
const stepCount = computed(() =>
  pageData.value.records.reduce((total, item) => total + item.steps.length, 0),
);
const asWorkflowDefinition = (row: unknown) => row as WorkflowDefinitionView;

void loadDefinitions();

async function loadDefinitions() {
  loading.value = true;
  try {
    pageData.value = await queryWorkflowDefinitions({
      status: query.status || undefined,
      page: query.page,
      size: query.size,
    });
  } finally {
    loading.value = false;
  }
}

function applySearch() {
  query.page = 1;
  void loadDefinitions();
}

function resetSearch() {
  query.status = '';
  query.page = 1;
  void loadDefinitions();
}

async function handlePageChange(nextPage: number) {
  query.page = nextPage;
  await loadDefinitions();
}

async function handleSizeChange(nextSize: number) {
  query.size = nextSize;
  query.page = 1;
  await loadDefinitions();
}

function openCreateDialog() {
  form.definitionKey = '';
  form.definitionName = '';
  form.stepsText = defaultStepsText();
  form.remark = '';
  createVisible.value = true;
}

function openDesigner() {
  void router.push({ name: 'workflow-designer' });
}

function openDetail(row: WorkflowDefinitionView) {
  detailItem.value = row;
  detailVisible.value = true;
}

async function submitDefinition() {
  await formRef.value?.validate();
  let steps: WorkflowStepInput[];
  try {
    steps = parseStepsText(form.stepsText);
  } catch (error) {
    ElMessage.error(
      error instanceof Error ? error.message : '审批步骤格式不正确',
    );
    return;
  }

  const payload: WorkflowDefinitionRequest = {
    definitionKey: form.definitionKey.trim(),
    definitionName: form.definitionName.trim(),
    steps,
    remark: form.remark.trim() || undefined,
  };

  submitting.value = true;
  try {
    await createWorkflowDefinition(payload);
    ElMessage.success('流程定义草稿已创建');
    createVisible.value = false;
    query.page = 1;
    await loadDefinitions();
  } finally {
    submitting.value = false;
  }
}

async function deployDefinition(row: WorkflowDefinitionView) {
  await ElMessageBox.confirm(
    `确认部署「${row.definitionName}」？部署后可发起流程。`,
    '部署确认',
    { type: 'warning' },
  );
  await deployWorkflowDefinition(row.id);
  ElMessage.success('流程定义已部署');
  await loadDefinitions();
}

async function disableDefinition(row: WorkflowDefinitionView) {
  await ElMessageBox.confirm(
    `确认停用「${row.definitionName}」？停用后不能继续发起。`,
    '停用确认',
    { type: 'warning' },
  );
  await disableWorkflowDefinition(row.id);
  ElMessage.success('流程定义已停用');
  await loadDefinitions();
}

function parseStepsText(value: string): WorkflowStepInput[] {
  const parsed = JSON.parse(value) as unknown;
  if (!Array.isArray(parsed) || parsed.length === 0) {
    throw new Error('审批步骤必须是非空数组');
  }
  return parsed.map((item, index) => {
    if (!isRecord(item) || typeof item.name !== 'string' || !item.name.trim()) {
      throw new Error(`第 ${index + 1} 个审批步骤缺少名称`);
    }
    const candidateUserIds = normalizeNumberArray(item.candidateUserIds);
    const candidateGroupCodes = normalizeStringArray(item.candidateGroupCodes);
    if (candidateUserIds.length === 0 && candidateGroupCodes.length === 0) {
      throw new Error(`第 ${index + 1} 个审批步骤至少需要候选人或候选组`);
    }
    return {
      name: item.name.trim(),
      candidateUserIds,
      candidateGroupCodes,
      rejectStrategy: normalizeRejectStrategy(item.rejectStrategy),
      rejectTarget: normalizeRejectTarget(
        item.rejectStrategy,
        item.rejectTarget,
        index,
        parsed.length,
      ),
    };
  });
}

function normalizeRejectStrategy(
  value: unknown,
): 'END' | 'PREVIOUS' | 'RESTART' | 'TO_STARTER' | 'TO_STEP' {
  if (
    value === 'PREVIOUS' ||
    value === 'RESTART' ||
    value === 'TO_STEP' ||
    value === 'TO_STARTER' ||
    value === 'END'
  ) {
    return value;
  }
  return 'END';
}

function normalizeRejectTarget(
  strategy: unknown,
  value: unknown,
  index: number,
  totalSteps: number,
) {
  if (strategy !== 'TO_STEP') {
    return null;
  }
  const target = Number(value);
  if (!Number.isInteger(target)) {
    throw new TypeError(`第 ${index + 1} 个审批步骤需要配置指定驳回目标`);
  }
  if (target < 0 || target >= totalSteps || target >= index) {
    throw new Error(
      `第 ${index + 1} 个审批步骤的指定驳回目标必须在当前节点之前`,
    );
  }
  return target;
}

function normalizeNumberArray(value: unknown) {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.map(Number).filter((item) => Number.isInteger(item) && item > 0);
}

function normalizeStringArray(value: unknown) {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.map((item) => String(item).trim()).filter(Boolean);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function defaultStepsText() {
  return JSON.stringify(
    [
      {
        name: '直属主管审批',
        candidateUserIds: [1],
        candidateGroupCodes: [],
        rejectStrategy: 'END',
      },
      {
        name: '平台管理员复核',
        candidateUserIds: [],
        candidateGroupCodes: ['ADMIN'],
        rejectStrategy: 'TO_STEP',
        rejectTarget: 0,
      },
    ],
    null,
    2,
  );
}

function formatSteps(row: WorkflowDefinitionView) {
  return row.steps
    .map((step) => `${step.stepIndex + 1}. ${step.name}`)
    .join(' / ');
}

function formatRejectStrategy(
  step: WorkflowStepView,
  steps: WorkflowStepView[] = [],
) {
  if (step.rejectStrategy === 'PREVIOUS') {
    return '驳回上一节点';
  }
  if (step.rejectStrategy === 'RESTART') {
    return '驳回首节点';
  }
  if (step.rejectStrategy === 'TO_STARTER') {
    return '驳回发起人重提';
  }
  if (step.rejectStrategy === 'TO_STEP') {
    const target =
      step.rejectTarget === null || step.rejectTarget === undefined
        ? null
        : steps.find((item) => item.stepIndex === step.rejectTarget);
    return target
      ? `驳回指定节点：${target.stepIndex + 1}. ${target.name}`
      : '驳回指定节点：目标未配置';
  }
  return '驳回结束';
}

function formatCandidates(step: WorkflowStepView) {
  const users =
    step.candidateUserIds.length > 0
      ? `候选人：${step.candidateUserIds.join(', ')}`
      : '';
  const groups =
    step.candidateGroupCodes.length > 0
      ? `候选组：${step.candidateGroupCodes.join(', ')}`
      : '';
  return [users, groups].filter(Boolean).join('；') || '未配置候选范围';
}

function formatDateTime(value?: null | string) {
  return formatInstantDateTime(value);
}

function definitionStatusText(status: string) {
  return (
    (
      { DRAFT: '草稿', DEPLOYED: '已部署', DISABLED: '已停用' } as Record<
        string,
        string
      >
    )[status] ?? status
  );
}

function definitionStatusTag(status: string): TagProps['type'] {
  if (status === 'DEPLOYED') {
    return 'success';
  }
  if (status === 'DISABLED') {
    return 'danger';
  }
  return 'info';
}
</script>

<template>
  <div class="panel-stack workflow-page">
    <section class="dashboard-grid">
      <article class="stat-card workflow-stat workflow-stat--primary">
        <span class="eyebrow">Definitions</span>
        <strong>{{ pageData.total }}</strong>
        <span>流程定义总数</span>
      </article>
      <article class="stat-card workflow-stat">
        <span class="eyebrow">Deployed</span>
        <strong>{{ deployedCount }}</strong>
        <span>当前页已部署</span>
      </article>
      <article class="stat-card workflow-stat">
        <span class="eyebrow">Steps</span>
        <strong>{{ stepCount }}</strong>
        <span>当前页审批节点</span>
      </article>
    </section>

    <section class="dashboard-panel workflow-console">
      <div class="panel-head">
        <div>
          <span class="eyebrow">工作流</span>
          <h3>流程定义</h3>
          <p class="muted-line">
            统一维护流程标识、审批节点、候选范围和驳回策略，部署后即可用于发起审批。
          </p>
        </div>
        <div class="panel-actions">
          <ElButton @click="openCreateDialog">JSON 新增</ElButton>
          <ElButton type="primary" @click="openDesigner">打开设计器</ElButton>
        </div>
      </div>

      <ElForm :inline="true" :model="query" class="workflow-search">
        <ElFormItem label="状态">
          <ElSelect
            v-model="query.status"
            clearable
            placeholder="全部"
            style="width: 160px"
          >
            <ElOption label="草稿" value="DRAFT" />
            <ElOption label="已部署" value="DEPLOYED" />
            <ElOption label="已停用" value="DISABLED" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="applySearch">搜索</ElButton>
          <ElButton @click="resetSearch">重置</ElButton>
        </ElFormItem>
      </ElForm>

      <div class="table-tools">
        <ElButton size="small" :loading="loading" @click="loadDefinitions">
          刷新
        </ElButton>
      </div>

      <ElTable v-loading="loading" :data="pageData.records" stripe>
        <ElTableColumn
          prop="definitionName"
          label="流程名称"
          min-width="180"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <div class="workflow-name-cell">
              <strong>{{ row.definitionName }}</strong>
              <small>{{ row.definitionKey }} · v{{ row.version }}</small>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="110">
          <template #default="{ row }">
            <ElTag :type="definitionStatusTag(row.status)" effect="plain">
              {{ definitionStatusText(row.status) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="审批步骤" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatSteps(asWorkflowDefinition(row)) }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="tenantId" label="租户" width="130" />
        <ElTableColumn label="更新时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.updatedAt) }}
          </template>
        </ElTableColumn>
        <ElTableColumn fixed="right" label="操作" width="230">
          <template #default="{ row }">
            <ElButton
              link
              type="primary"
              @click="openDetail(asWorkflowDefinition(row))"
            >
              详情
            </ElButton>
            <ElButton
              v-if="row.status === 'DRAFT'"
              link
              type="primary"
              @click="deployDefinition(asWorkflowDefinition(row))"
            >
              部署
            </ElButton>
            <ElButton
              v-if="row.status === 'DEPLOYED'"
              link
              type="danger"
              @click="disableDefinition(asWorkflowDefinition(row))"
            >
              停用
            </ElButton>
          </template>
        </ElTableColumn>
        <template #empty>
          <ElEmpty description="暂无流程定义" />
        </template>
      </ElTable>

      <div class="footer-bar">
        <span>共 {{ pageData.total }} 条定义</span>
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

    <ElDialog v-model="createVisible" title="新增流程定义" width="680px">
      <ElForm ref="formRef" label-position="top" :model="form" :rules="rules">
        <ElFormItem label="定义标识" prop="definitionKey">
          <ElInput
            v-model="form.definitionKey"
            placeholder="例如 leave-approval"
          />
        </ElFormItem>
        <ElFormItem label="定义名称" prop="definitionName">
          <ElInput v-model="form.definitionName" placeholder="例如 请假审批" />
        </ElFormItem>
        <ElFormItem label="审批步骤 JSON" prop="stepsText">
          <ElInput v-model="form.stepsText" type="textarea" :rows="8" />
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput
            v-model="form.remark"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="createVisible = false">取消</ElButton>
        <ElButton
          type="primary"
          :loading="submitting"
          @click="submitDefinition"
        >
          保存草稿
        </ElButton>
      </template>
    </ElDialog>

    <ElDrawer v-model="detailVisible" title="流程定义详情" size="620px">
      <template v-if="detailItem">
        <ElDescriptions :column="2" border class="drawer-section">
          <ElDescriptionsItem label="流程名称">
            {{ detailItem.definitionName }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="定义标识">
            {{ detailItem.definitionKey }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="版本">
            v{{ detailItem.version }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="状态">
            {{ definitionStatusText(detailItem.status) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="租户">
            {{ detailItem.tenantId }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="更新时间">
            {{ formatDateTime(detailItem.updatedAt) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="备注" :span="2">
            {{ detailItem.remark || '-' }}
          </ElDescriptionsItem>
        </ElDescriptions>
        <div class="step-timeline">
          <article
            v-for="step in detailItem.steps"
            :key="step.stepIndex"
            class="step-node"
          >
            <span>{{ step.stepIndex + 1 }}</span>
            <div>
              <strong>{{ step.name }}</strong>
              <small>{{ formatCandidates(step) }}</small>
              <small class="step-node-reject"
                >驳回策略：{{
                  formatRejectStrategy(step, detailItem.steps)
                }}</small
              >
            </div>
          </article>
        </div>
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
  max-width: 760px;
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

.step-timeline {
  display: grid;
  gap: 12px;
  margin-top: 18px;
}

.step-node {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 14px;
  background: var(--bg-card-muted);
  border: 1px solid var(--line);
  border-radius: 14px;

  > span {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 30px;
    height: 30px;
    font-weight: 800;
    color: var(--accent);
    background: var(--accent-soft);
    border-radius: 999px;
  }

  strong,
  small {
    display: block;
  }

  small {
    margin-top: 4px;
    color: var(--text-soft);
  }
}

.step-node-reject {
  font-weight: 600;
  color: var(--accent) !important;
}

.workflow-search {
  margin-bottom: 12px;
}

.panel-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
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
