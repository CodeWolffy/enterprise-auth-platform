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
          <p class="muted-line">发起时保存变量快照，审批过程只读取快照，不反向改写业务变量。</p>
        </div>
        <el-button v-permission="'upms:workflowinstance:add'" type="primary" @click="openStartDialog">发起流程</el-button>
      </div>

      <AdvancedSearch @search="applySearch" @reset="resetSearch">
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 180px">
            <el-option label="全部" value="" />
            <el-option label="进行中" value="RUNNING" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
            <el-option label="已撤回" value="WITHDRAWN" />
            <el-option label="已终止" value="TERMINATED" />
          </el-select>
        </el-form-item>
      </AdvancedSearch>

      <div class="table-tools">
        <el-button size="small" :loading="loading" @click="loadInstances">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="pageData.records" stripe>
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="workflow-name-cell">
              <strong>{{ row.title }}</strong>
              <small>{{ row.businessKey }} · {{ row.definitionKey }} v{{ row.definitionVersion }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="instanceStatusTag(row.status)" effect="plain">{{ instanceStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currentStepIndex" label="当前步骤" width="110" />
        <el-table-column prop="tenantId" label="租户" width="130" />
        <el-table-column label="发起时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" width="240">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'RUNNING'" v-permission="'upms:workflowinstance:edit'" :loading="withdrawingId === row.id" link type="danger" @click="withdrawInstance(row)">撤回</el-button>
            <el-button
              v-if="row.status === 'RUNNING'"
              v-permission="'upms:workflowinstance:del'"
              :loading="terminatingId === row.id"
              link
              type="danger"
              @click="terminateInstance(row)"
            >终止</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无流程实例" />
        </template>
      </el-table>

      <div class="footer-bar">
        <span>共 {{ pageData.total }} 条实例</span>
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

    <el-dialog v-model="startVisible" title="发起流程" width="640px">
      <el-form ref="formRef" label-position="top" :model="form" :rules="rules">
        <el-form-item label="流程定义" prop="definitionKey">
          <el-select
            v-model="form.definitionKey"
            filterable
            allow-create
            default-first-option
            :loading="definitionLoading"
            placeholder="选择已部署定义，或手动输入 definitionKey"
            style="width: 100%"
          >
            <el-option
              v-for="definition in deployedDefinitions"
              :key="definition.id"
              :label="`${definition.definitionName} · ${definition.definitionKey} v${definition.version}`"
              :value="definition.definitionKey"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="业务键" prop="businessKey">
          <el-input v-model="form.businessKey" placeholder="例如 leave-20260604-001" />
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="例如 2026 年端午请假申请" />
        </el-form-item>
        <el-form-item label="变量 JSON">
          <el-input v-model="form.variablesText" type="textarea" :rows="7" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="startVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitStart">发起</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="流程实例详情" size="620px">
      <template v-if="detailItem">
        <el-descriptions :column="2" border class="drawer-section">
          <el-descriptions-item label="标题" :span="2">{{ detailItem.title }}</el-descriptions-item>
          <el-descriptions-item label="业务键">{{ detailItem.businessKey }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ instanceStatusText(detailItem.status) }}</el-descriptions-item>
          <el-descriptions-item label="定义">{{ detailItem.definitionKey }} v{{ detailItem.definitionVersion }}</el-descriptions-item>
          <el-descriptions-item label="当前步骤">{{ detailItem.currentStepIndex }}</el-descriptions-item>
          <el-descriptions-item label="发起人">{{ detailItem.starterUsername }}</el-descriptions-item>
          <el-descriptions-item label="发起时间">{{ formatDateTime(detailItem.startedAt) }}</el-descriptions-item>
        </el-descriptions>
        <section class="snapshot-card">
          <span class="eyebrow">Variables Snapshot</span>
          <pre>{{ formatSnapshot(detailItem.variablesSnapshot) }}</pre>
        </section>
        <section class="snapshot-card">
          <div class="urge-card-head">
            <span class="eyebrow">Urge Records</span>
            <el-button size="small" text :loading="urgeLoading" @click="loadInstanceUrges(detailItem.id)">刷新</el-button>
          </div>
          <el-table v-loading="urgeLoading" :data="urgePage.records" size="small" stripe>
            <el-table-column label="催办人" prop="urgedByUsername" width="120" />
            <el-table-column label="接收范围" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.targetUsernames.join('、') || '-' }}</template>
            </el-table-column>
            <el-table-column label="说明" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.comment || '-' }}</template>
            </el-table-column>
            <el-table-column label="时间" width="160">
              <template #default="{ row }">{{ formatDateTime(row.urgedAt) }}</template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无催办记录" />
            </template>
          </el-table>
        </section>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type { FormInstance, FormRules, TagProps } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdvancedSearch from '@/components/common/AdvancedSearch.vue'
import {
  listWorkflowInstanceUrges,
  queryMyWorkflowInstances,
  queryWorkflowDefinitions,
  startWorkflowInstance,
  terminateWorkflowInstance,
  withdrawWorkflowInstance,
} from '@/api/modules'
import type { PageResult } from '@/types/api'
import type { WorkflowDefinitionView, WorkflowInstanceView, WorkflowStartRequest, WorkflowTaskUrgeView } from '@/types/workflow'
import { formatDateTime } from '@/utils/datetime'

const loading = ref(false)
const definitionLoading = ref(false)
const submitting = ref(false)
const startVisible = ref(false)
const detailVisible = ref(false)
const withdrawingId = ref<number | null>(null)
const terminatingId = ref<number | null>(null)
const detailItem = ref<WorkflowInstanceView | null>(null)
const formRef = ref<FormInstance>()

const query = reactive({
  status: '',
  page: 1,
  size: 20,
})

const pageData = ref<PageResult<WorkflowInstanceView>>({ total: 0, page: 1, size: 20, records: [] })
const urgeLoading = ref(false)
const urgePage = ref<PageResult<WorkflowTaskUrgeView>>({ total: 0, page: 1, size: 20, records: [] })
const deployedDefinitions = ref<WorkflowDefinitionView[]>([])

const form = reactive({
  definitionKey: '',
  businessKey: '',
  title: '',
  variablesText: defaultVariablesText(),
})

const rules = reactive<FormRules>({
  definitionKey: [{ required: true, message: '请输入流程定义标识', trigger: 'blur' }],
  businessKey: [{ required: true, message: '请输入业务键', trigger: 'blur' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
})

const runningCount = computed(() => pageData.value.records.filter((item) => item.status === 'RUNNING').length)
const closedCount = computed(() => pageData.value.records.filter((item) => item.status !== 'RUNNING').length)

void loadInstances()
void loadDeployedDefinitions()

async function loadInstances() {
  loading.value = true
  try {
    pageData.value = await queryMyWorkflowInstances({
      status: query.status || undefined,
      page: query.page,
      size: query.size,
    })
  } finally {
    loading.value = false
  }
}

async function loadDeployedDefinitions() {
  definitionLoading.value = true
  try {
    const page = await queryWorkflowDefinitions({ status: 'DEPLOYED', page: 1, size: 100 })
    deployedDefinitions.value = page.records
  } catch {
    deployedDefinitions.value = []
  } finally {
    definitionLoading.value = false
  }
}

async function loadInstanceUrges(instanceId: number) {
  urgeLoading.value = true
  try {
    urgePage.value = await listWorkflowInstanceUrges(instanceId, 1, 20)
  } finally {
    urgeLoading.value = false
  }
}

function applySearch() {
  query.page = 1
  void loadInstances()
}

function resetSearch() {
  query.status = ''
  query.page = 1
  void loadInstances()
}

async function handlePageChange(nextPage: number) {
  query.page = nextPage
  await loadInstances()
}

async function handleSizeChange(nextSize: number) {
  query.size = nextSize
  query.page = 1
  await loadInstances()
}

function openStartDialog() {
  form.definitionKey = deployedDefinitions.value[0]?.definitionKey ?? ''
  form.businessKey = ''
  form.title = ''
  form.variablesText = defaultVariablesText()
  startVisible.value = true
  if (!deployedDefinitions.value.length) {
    void loadDeployedDefinitions()
  }
}

function openDetail(row: WorkflowInstanceView) {
  detailItem.value = row
  detailVisible.value = true
  void loadInstanceUrges(row.id)
}

async function submitStart() {
  await formRef.value?.validate()
  let variables: Record<string, unknown>
  try {
    variables = parseVariables(form.variablesText)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '变量格式不正确')
    return
  }

  const payload: WorkflowStartRequest = {
    definitionKey: form.definitionKey.trim(),
    businessKey: form.businessKey.trim(),
    title: form.title.trim(),
    variables,
  }

  submitting.value = true
  try {
    await startWorkflowInstance(payload)
    ElMessage.success('流程已发起')
    startVisible.value = false
    query.page = 1
    await loadInstances()
  } finally {
    submitting.value = false
  }
}

async function withdrawInstance(row: WorkflowInstanceView) {
  await ElMessageBox.confirm(`确认撤回「${row.title}」？`, '撤回确认', { type: 'warning' })
  withdrawingId.value = row.id
  try {
    await withdrawWorkflowInstance(row.id)
    ElMessage.success('流程已撤回')
    await loadInstances()
  } finally {
    withdrawingId.value = null
  }
}

async function terminateInstance(row: WorkflowInstanceView) {
  const result = await ElMessageBox.prompt(`确认终止「${row.title}」？`, '终止确认', {
    type: 'warning',
    inputPlaceholder: '请输入终止原因（可选）',
    inputType: 'textarea',
  })
  terminatingId.value = row.id
  try {
    await terminateWorkflowInstance(row.id, result.value?.trim() || undefined)
    ElMessage.success('流程已终止')
    await loadInstances()
  } finally {
    terminatingId.value = null
  }
}

function parseVariables(value: string) {
  if (!value.trim()) {
    return {}
  }
  const parsed = JSON.parse(value) as unknown
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    throw new Error('变量必须是 JSON 对象')
  }
  return parsed as Record<string, unknown>
}

function defaultVariablesText() {
  return JSON.stringify({ amount: 1000, reason: 'PoC 验证' }, null, 2)
}

function formatSnapshot(value: Record<string, unknown>) {
  return JSON.stringify(value ?? {}, null, 2)
}

function instanceStatusText(status: string) {
  return ({ RUNNING: '进行中', APPROVED: '已通过', REJECTED: '已驳回', WITHDRAWN: '已撤回', TERMINATED: '已终止' } as Record<string, string>)[status] ?? status
}

function instanceStatusTag(status: string): TagProps['type'] {
  if (status === 'APPROVED') {
    return 'success'
  }
  if (status === 'RUNNING') {
    return 'warning'
  }
  if (status === 'REJECTED' || status === 'TERMINATED') {
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

.snapshot-card {
  margin-top: 18px;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--bg-card-muted);

  pre {
    margin: 12px 0 0;
    white-space: pre-wrap;
    word-break: break-word;
    color: var(--text-main);
  }
}

.urge-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
</style>