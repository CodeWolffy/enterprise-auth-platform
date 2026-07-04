<script setup lang="ts">
import type { FormInstance, FormRules, TagProps } from 'element-plus';

import type { PageResult } from '#/types/api';
import type {
  WorkflowDefinitionView,
  WorkflowInstanceView,
  WorkflowStartRequest,
  WorkflowTaskUrgeView,
} from '#/types/workflow';

import { computed, reactive, ref } from 'vue';

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
  listWorkflowInstanceUrges,
  queryMyWorkflowInstances,
  queryWorkflowDefinitions,
  startWorkflowInstance,
  terminateWorkflowInstance,
  withdrawWorkflowInstance,
} from '#/api/modules';
import { formatDateTime as formatInstantDateTime } from '#/utils/datetime';

const loading = ref(false);
const definitionLoading = ref(false);
const submitting = ref(false);
const startVisible = ref(false);
const detailVisible = ref(false);
const withdrawingId = ref<null | number>(null);
const terminatingId = ref<null | number>(null);
const detailItem = ref<null | WorkflowInstanceView>(null);
const formRef = ref<FormInstance>();

const query = reactive({
  status: '',
  page: 1,
  size: 20,
});

const pageData = ref<PageResult<WorkflowInstanceView>>({
  total: 0,
  page: 1,
  size: 20,
  records: [],
});
const urgeLoading = ref(false);
const urgePage = ref<PageResult<WorkflowTaskUrgeView>>({
  total: 0,
  page: 1,
  size: 20,
  records: [],
});
const deployedDefinitions = ref<WorkflowDefinitionView[]>([]);

const form = reactive({
  definitionKey: '',
  businessKey: '',
  title: '',
  variablesText: defaultVariablesText(),
});

const rules = reactive<FormRules>({
  definitionKey: [
    { required: true, message: '请输入流程定义标识', trigger: 'blur' },
  ],
  businessKey: [{ required: true, message: '请输入业务键', trigger: 'blur' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
});

const runningCount = computed(
  () =>
    pageData.value.records.filter((item) => item.status === 'RUNNING').length,
);
const closedCount = computed(
  () =>
    pageData.value.records.filter((item) => item.status !== 'RUNNING').length,
);
const asWorkflowInstance = (row: unknown) => row as WorkflowInstanceView;

void loadInstances();
void loadDeployedDefinitions();

async function loadInstances() {
  loading.value = true;
  try {
    pageData.value = await queryMyWorkflowInstances({
      status: query.status || undefined,
      page: query.page,
      size: query.size,
    });
  } finally {
    loading.value = false;
  }
}

async function loadDeployedDefinitions() {
  definitionLoading.value = true;
  try {
    const page = await queryWorkflowDefinitions({
      status: 'DEPLOYED',
      page: 1,
      size: 100,
    });
    deployedDefinitions.value = page.records;
  } catch {
    deployedDefinitions.value = [];
  } finally {
    definitionLoading.value = false;
  }
}

async function loadInstanceUrges(instanceId: number) {
  urgeLoading.value = true;
  try {
    urgePage.value = await listWorkflowInstanceUrges(instanceId, 1, 20);
  } finally {
    urgeLoading.value = false;
  }
}

function applySearch() {
  query.page = 1;
  void loadInstances();
}

function resetSearch() {
  query.status = '';
  query.page = 1;
  void loadInstances();
}

async function handlePageChange(nextPage: number) {
  query.page = nextPage;
  await loadInstances();
}

async function handleSizeChange(nextSize: number) {
  query.size = nextSize;
  query.page = 1;
  await loadInstances();
}

function openStartDialog() {
  form.definitionKey = deployedDefinitions.value[0]?.definitionKey ?? '';
  form.businessKey = '';
  form.title = '';
  form.variablesText = defaultVariablesText();
  startVisible.value = true;
  if (deployedDefinitions.value.length === 0) {
    void loadDeployedDefinitions();
  }
}

function openDetail(row: WorkflowInstanceView) {
  detailItem.value = row;
  detailVisible.value = true;
  void loadInstanceUrges(row.id);
}

async function submitStart() {
  await formRef.value?.validate();
  let variables: Record<string, unknown>;
  try {
    variables = parseVariables(form.variablesText);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '变量格式不正确');
    return;
  }

  const payload: WorkflowStartRequest = {
    definitionKey: form.definitionKey.trim(),
    businessKey: form.businessKey.trim(),
    title: form.title.trim(),
    variables,
  };

  submitting.value = true;
  try {
    await startWorkflowInstance(payload);
    ElMessage.success('流程已发起');
    startVisible.value = false;
    query.page = 1;
    await loadInstances();
  } finally {
    submitting.value = false;
  }
}

async function withdrawInstance(row: WorkflowInstanceView) {
  await ElMessageBox.confirm(`确认撤回「${row.title}」？`, '撤回确认', {
    type: 'warning',
  });
  withdrawingId.value = row.id;
  try {
    await withdrawWorkflowInstance(row.id);
    ElMessage.success('流程已撤回');
    await loadInstances();
  } finally {
    withdrawingId.value = null;
  }
}

async function terminateInstance(row: WorkflowInstanceView) {
  const result = await ElMessageBox.prompt(
    `确认终止「${row.title}」？`,
    '终止确认',
    {
      type: 'warning',
      inputPlaceholder: '请输入终止原因（可选）',
      inputType: 'textarea',
    },
  );
  terminatingId.value = row.id;
  try {
    await terminateWorkflowInstance(row.id, result.value?.trim() || undefined);
    ElMessage.success('流程已终止');
    await loadInstances();
  } finally {
    terminatingId.value = null;
  }
}

function parseVariables(value: string) {
  if (!value.trim()) {
    return {};
  }
  const parsed = JSON.parse(value) as unknown;
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    throw new Error('变量必须是 JSON 对象');
  }
  return parsed as Record<string, unknown>;
}

function defaultVariablesText() {
  return JSON.stringify({ amount: 1000, reason: 'PoC 验证' }, null, 2);
}

function formatSnapshot(value: Record<string, unknown>) {
  return JSON.stringify(value ?? {}, null, 2);
}

function instanceStatusText(status: string) {
  return (
    (
      {
        RUNNING: '进行中',
        APPROVED: '已通过',
        REJECTED: '已驳回',
        WITHDRAWN: '已撤回',
        TERMINATED: '已终止',
      } as Record<string, string>
    )[status] ?? status
  );
}

function instanceStatusTag(status: string): TagProps['type'] {
  if (status === 'APPROVED') {
    return 'success';
  }
  if (status === 'RUNNING') {
    return 'warning';
  }
  if (status === 'REJECTED' || status === 'TERMINATED') {
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
        <span class="eyebrow">Instances</span>
        <strong>{{ pageData.total }}</strong>
        <span>我的发起总数</span>
      </article>
      <article class="stat-card workflow-stat">
        <span class="eyebrow">Running</span>
        <strong>{{ runningCount }}</strong>
        <span>当前页进行中</span>
      </article>
      <article class="stat-card workflow-stat">
        <span class="eyebrow">Closed</span>
        <strong>{{ closedCount }}</strong>
        <span>当前页已结束</span>
      </article>
    </section>

    <section class="dashboard-panel workflow-console">
      <div class="panel-head">
        <div>
          <span class="eyebrow">我的发起</span>
          <h3>流程实例</h3>
          <p class="muted-line">
            发起时保存变量快照，审批过程只读取快照，不反向改写业务变量。
          </p>
        </div>
        <ElButton type="primary" @click="openStartDialog">发起流程</ElButton>
      </div>

      <ElForm :inline="true" :model="query" class="workflow-search">
        <ElFormItem label="状态">
          <ElSelect
            v-model="query.status"
            placeholder="全部"
            clearable
            style="width: 180px"
          >
            <ElOption label="全部" value="" />
            <ElOption label="进行中" value="RUNNING" />
            <ElOption label="已通过" value="APPROVED" />
            <ElOption label="已驳回" value="REJECTED" />
            <ElOption label="已撤回" value="WITHDRAWN" />
            <ElOption label="已终止" value="TERMINATED" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="applySearch">搜索</ElButton>
          <ElButton @click="resetSearch">重置</ElButton>
        </ElFormItem>
      </ElForm>

      <div class="table-tools">
        <ElButton size="small" :loading="loading" @click="loadInstances">
          刷新
        </ElButton>
      </div>

      <ElTable v-loading="loading" :data="pageData.records" stripe>
        <ElTableColumn
          prop="title"
          label="标题"
          min-width="220"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <div class="workflow-name-cell">
              <strong>{{ row.title }}</strong>
              <small
                >{{ row.businessKey }} · {{ row.definitionKey }} v{{
                  row.definitionVersion
                }}</small
              >
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="110">
          <template #default="{ row }">
            <ElTag :type="instanceStatusTag(row.status)" effect="plain">
              {{ instanceStatusText(row.status) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="currentStepIndex" label="当前步骤" width="110" />
        <ElTableColumn prop="tenantId" label="租户" width="130" />
        <ElTableColumn label="发起时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.startedAt) }}
          </template>
        </ElTableColumn>
        <ElTableColumn fixed="right" label="操作" width="240">
          <template #default="{ row }">
            <ElButton
              link
              type="primary"
              @click="openDetail(asWorkflowInstance(row))"
            >
              详情
            </ElButton>
            <ElButton
              v-if="row.status === 'RUNNING'"
              :loading="withdrawingId === row.id"
              link
              type="danger"
              @click="withdrawInstance(asWorkflowInstance(row))"
            >
              撤回
            </ElButton>
            <ElButton
              v-if="row.status === 'RUNNING'"
              :loading="terminatingId === row.id"
              link
              type="danger"
              @click="terminateInstance(asWorkflowInstance(row))"
            >
              终止
            </ElButton>
          </template>
        </ElTableColumn>
        <template #empty>
          <ElEmpty description="暂无流程实例" />
        </template>
      </ElTable>

      <div class="footer-bar">
        <span>共 {{ pageData.total }} 条实例</span>
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

    <ElDialog v-model="startVisible" title="发起流程" width="640px">
      <ElForm ref="formRef" label-position="top" :model="form" :rules="rules">
        <ElFormItem label="流程定义" prop="definitionKey">
          <ElSelect
            v-model="form.definitionKey"
            filterable
            allow-create
            default-first-option
            :loading="definitionLoading"
            placeholder="选择已部署定义，或手动输入 definitionKey"
            style="width: 100%"
          >
            <ElOption
              v-for="definition in deployedDefinitions"
              :key="definition.id"
              :label="`${definition.definitionName} · ${definition.definitionKey} v${definition.version}`"
              :value="definition.definitionKey"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="业务键" prop="businessKey">
          <ElInput
            v-model="form.businessKey"
            placeholder="例如 leave-20260604-001"
          />
        </ElFormItem>
        <ElFormItem label="标题" prop="title">
          <ElInput
            v-model="form.title"
            placeholder="例如 2026 年端午请假申请"
          />
        </ElFormItem>
        <ElFormItem label="变量 JSON">
          <ElInput v-model="form.variablesText" type="textarea" :rows="7" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="startVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="submitting" @click="submitStart">
          发起
        </ElButton>
      </template>
    </ElDialog>

    <ElDrawer v-model="detailVisible" title="流程实例详情" size="620px">
      <template v-if="detailItem">
        <ElDescriptions :column="2" border class="drawer-section">
          <ElDescriptionsItem label="标题" :span="2">
            {{ detailItem.title }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="业务键">
            {{ detailItem.businessKey }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="状态">
            {{ instanceStatusText(detailItem.status) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="定义">
            {{ detailItem.definitionKey }} v{{ detailItem.definitionVersion }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="当前步骤">
            {{ detailItem.currentStepIndex }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="发起人">
            {{ detailItem.starterUsername }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="发起时间">
            {{ formatDateTime(detailItem.startedAt) }}
          </ElDescriptionsItem>
        </ElDescriptions>
        <section class="snapshot-card">
          <span class="eyebrow">Variables Snapshot</span>
          <pre>{{ formatSnapshot(detailItem.variablesSnapshot) }}</pre>
        </section>
        <section class="snapshot-card">
          <div class="urge-card-head">
            <span class="eyebrow">Urge Records</span>
            <ElButton
              size="small"
              text
              :loading="urgeLoading"
              @click="loadInstanceUrges(detailItem.id)"
            >
              刷新
            </ElButton>
          </div>
          <ElTable
            v-loading="urgeLoading"
            :data="urgePage.records"
            size="small"
            stripe
          >
            <ElTableColumn label="催办人" prop="urgedByUsername" width="120" />
            <ElTableColumn
              label="接收范围"
              min-width="160"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ row.targetUsernames.join('、') || '-' }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="说明" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.comment || '-' }}</template>
            </ElTableColumn>
            <ElTableColumn label="时间" width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.urgedAt) }}
              </template>
            </ElTableColumn>
            <template #empty>
              <ElEmpty description="暂无催办记录" />
            </template>
          </ElTable>
        </section>
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

.snapshot-card {
  padding: 16px;
  margin-top: 18px;
  background: var(--bg-card-muted);
  border: 1px solid var(--line);
  border-radius: 14px;

  pre {
    margin: 12px 0 0;
    color: var(--text-main);
    overflow-wrap: anywhere;
    white-space: pre-wrap;
  }
}

.urge-card-head {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.workflow-search {
  margin-bottom: 12px;
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
