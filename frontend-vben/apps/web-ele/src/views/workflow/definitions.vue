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
          <p class="muted-line">定义、部署、停用和详情都已接入，后续补删除和版本管理也有位置放。</p>
        </div>
        <div class="panel-actions">
          <el-button @click="openCreateDialog">JSON 新增</el-button>
          <el-button type="primary" @click="openDesigner">打开设计器</el-button>
        </div>
      </div>

      <el-form :inline="true" :model="query" class="workflow-search">
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 160px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已部署" value="DEPLOYED" />
            <el-option label="已停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="applySearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-tools">
        <el-button size="small" :loading="loading" @click="loadDefinitions">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="pageData.records" stripe>
        <el-table-column prop="definitionName" label="流程名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="workflow-name-cell">
              <strong>{{ row.definitionName }}</strong>
              <small>{{ row.definitionKey }} · v{{ row.version }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="definitionStatusTag(row.status)" effect="plain">{{ definitionStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审批步骤" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ formatSteps(asWorkflowDefinition(row)) }}</template>
        </el-table-column>
        <el-table-column prop="tenantId" label="租户" width="130" />
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" width="230">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(asWorkflowDefinition(row))">详情</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="primary" @click="deployDefinition(asWorkflowDefinition(row))">部署</el-button>
            <el-button v-if="row.status === 'DEPLOYED'" link type="danger" @click="disableDefinition(asWorkflowDefinition(row))">停用</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无流程定义" />
        </template>
      </el-table>

      <div class="footer-bar">
        <span>共 {{ pageData.total }} 条定义</span>
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

    <el-dialog v-model="createVisible" title="新增流程定义" width="680px">
      <el-form ref="formRef" label-position="top" :model="form" :rules="rules">
        <el-form-item label="定义标识" prop="definitionKey">
          <el-input v-model="form.definitionKey" placeholder="例如 leave-approval" />
        </el-form-item>
        <el-form-item label="定义名称" prop="definitionName">
          <el-input v-model="form.definitionName" placeholder="例如 请假审批" />
        </el-form-item>
        <el-form-item label="审批步骤 JSON" prop="stepsText">
          <el-input v-model="form.stepsText" type="textarea" :rows="8" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitDefinition">保存草稿</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="流程定义详情" size="620px">
      <template v-if="detailItem">
        <el-descriptions :column="2" border class="drawer-section">
          <el-descriptions-item label="流程名称">{{ detailItem.definitionName }}</el-descriptions-item>
          <el-descriptions-item label="定义标识">{{ detailItem.definitionKey }}</el-descriptions-item>
          <el-descriptions-item label="版本">v{{ detailItem.version }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ definitionStatusText(detailItem.status) }}</el-descriptions-item>
          <el-descriptions-item label="租户">{{ detailItem.tenantId }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatDateTime(detailItem.updatedAt) }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detailItem.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="step-timeline">
          <article v-for="step in detailItem.steps" :key="step.stepIndex" class="step-node">
            <span>{{ step.stepIndex + 1 }}</span>
            <div>
              <strong>{{ step.name }}</strong>
              <small>{{ formatCandidates(step) }}</small>
              <small class="step-node-reject">驳回策略：{{ formatRejectStrategy(step, detailItem.steps) }}</small>
            </div>
          </article>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import type { FormInstance, FormRules, TagProps } from 'element-plus';
import { ElButton, ElDescriptions, ElDescriptionsItem, ElDialog, ElDrawer, ElEmpty, ElForm, ElFormItem, ElInput, ElMessage, ElMessageBox, ElOption, ElPagination, ElSelect, ElTable, ElTableColumn, ElTag } from 'element-plus';
import dayjs from 'dayjs';
import { createWorkflowDefinition, deployWorkflowDefinition, disableWorkflowDefinition, queryWorkflowDefinitions } from '#/api/modules';
import type { PageResult } from '#/types/api';
import type { WorkflowDefinitionRequest, WorkflowDefinitionView, WorkflowStepInput, WorkflowStepView } from '#/types/workflow';

const router = useRouter();
const loading = ref(false);
const submitting = ref(false);
const createVisible = ref(false);
const detailVisible = ref(false);
const detailItem = ref<WorkflowDefinitionView | null>(null);
const formRef = ref<FormInstance>();

const query = reactive({
  status: '',
  page: 1,
  size: 20,
});

const pageData = ref<PageResult<WorkflowDefinitionView>>({ total: 0, page: 1, size: 20, records: [] });

const form = reactive({
  definitionKey: '',
  definitionName: '',
  stepsText: defaultStepsText(),
  remark: '',
});

const rules = reactive<FormRules>({
  definitionKey: [{ required: true, message: '请输入定义标识', trigger: 'blur' }],
  definitionName: [{ required: true, message: '请输入定义名称', trigger: 'blur' }],
  stepsText: [{ required: true, message: '请输入审批步骤 JSON', trigger: 'blur' }],
});

const deployedCount = computed(() => pageData.value.records.filter((item) => item.status === 'DEPLOYED').length);
const stepCount = computed(() => pageData.value.records.reduce((total, item) => total + item.steps.length, 0));
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
    ElMessage.error(error instanceof Error ? error.message : '审批步骤格式不正确');
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
  await ElMessageBox.confirm(`确认部署「${row.definitionName}」？部署后可发起流程。`, '部署确认', { type: 'warning' });
  await deployWorkflowDefinition(row.id);
  ElMessage.success('流程定义已部署');
  await loadDefinitions();
}

async function disableDefinition(row: WorkflowDefinitionView) {
  await ElMessageBox.confirm(`确认停用「${row.definitionName}」？停用后不能继续发起。`, '停用确认', { type: 'warning' });
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
      rejectTarget: normalizeRejectTarget(item.rejectStrategy, item.rejectTarget, index, parsed.length),
    };
  });
}

function normalizeRejectStrategy(value: unknown): 'END' | 'PREVIOUS' | 'RESTART' | 'TO_STEP' | 'TO_STARTER' {
  if (value === 'PREVIOUS' || value === 'RESTART' || value === 'TO_STEP' || value === 'TO_STARTER' || value === 'END') {
    return value;
  }
  return 'END';
}

function normalizeRejectTarget(strategy: unknown, value: unknown, index: number, totalSteps: number) {
  if (strategy !== 'TO_STEP') {
    return null;
  }
  const target = Number(value);
  if (!Number.isInteger(target)) {
    throw new Error(`第 ${index + 1} 个审批步骤需要配置指定驳回目标`);
  }
  if (target < 0 || target >= totalSteps || target >= index) {
    throw new Error(`第 ${index + 1} 个审批步骤的指定驳回目标必须在当前节点之前`);
  }
  return target;
}

function normalizeNumberArray(value: unknown) {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.map((item) => Number(item)).filter((item) => Number.isInteger(item) && item > 0);
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
      { name: '直属主管审批', candidateUserIds: [1], candidateGroupCodes: [], rejectStrategy: 'END' },
      { name: '平台管理员复核', candidateUserIds: [], candidateGroupCodes: ['ADMIN'], rejectStrategy: 'TO_STEP', rejectTarget: 0 },
    ],
    null,
    2,
  );
}

function formatSteps(row: WorkflowDefinitionView) {
  return row.steps.map((step) => `${step.stepIndex + 1}. ${step.name}`).join(' / ');
}

function formatRejectStrategy(step: WorkflowStepView, steps: WorkflowStepView[] = []) {
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
    const target = step.rejectTarget === null || step.rejectTarget === undefined ? null : steps.find((item) => item.stepIndex === step.rejectTarget);
    return target ? `驳回指定节点：${target.stepIndex + 1}. ${target.name}` : '驳回指定节点：目标未配置';
  }
  return '驳回结束';
}

function formatCandidates(step: WorkflowStepView) {
  const users = step.candidateUserIds.length ? `候选人：${step.candidateUserIds.join(', ')}` : '';
  const groups = step.candidateGroupCodes.length ? `候选组：${step.candidateGroupCodes.join(', ')}` : '';
  return [users, groups].filter(Boolean).join('；') || '未配置候选范围';
}

function formatDateTime(value?: number | null) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-';
}

function definitionStatusText(status: string) {
  return ({ DRAFT: '草稿', DEPLOYED: '已部署', DISABLED: '已停用' } as Record<string, string>)[status] ?? status;
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
  max-width: 760px;
}

.workflow-name-cell {
  display: grid;
  gap: 4px;

  small {
    color: var(--text-soft);
    font-size: 12px;
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
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--bg-card-muted);

  > span {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 30px;
    height: 30px;
    border-radius: 999px;
    background: var(--accent-soft);
    color: var(--accent);
    font-weight: 800;
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
  color: var(--accent) !important;
  font-weight: 600;
}

.workflow-search {
  margin-bottom: 12px;
}

.panel-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.footer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
}

@media (max-width: 860px) {
  .footer-bar {
    display: grid;
  }
}
</style>
