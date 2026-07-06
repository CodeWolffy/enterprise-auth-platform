<script setup lang="ts">
import type {
  WorkflowDefinitionRequest,
  WorkflowStepInput,
} from '#/types/workflow';

import { computed, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import {
  ElAlert,
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTag,
} from 'element-plus';

import {
  createWorkflowDefinition,
  deployWorkflowDefinition,
} from '#/api/workflow';

type DesignerRejectStrategy =
  | 'END'
  | 'PREVIOUS'
  | 'RESTART'
  | 'TO_STARTER'
  | 'TO_STEP';

interface DesignerStep {
  localId: string;
  name: string;
  candidateUserIdsText: string;
  candidateGroupCodesText: string;
  rejectStrategy: DesignerRejectStrategy;
  rejectTarget: null | number;
}

const REJECT_STRATEGY_OPTIONS: Array<{
  description: string;
  label: string;
  value: DesignerRejectStrategy;
}> = [
  { value: 'END', label: '驳回结束', description: '驳回后结束流程（默认）' },
  {
    value: 'PREVIOUS',
    label: '驳回上一节点',
    description: '驳回后回到上一节点；首节点驳回等同于结束',
  },
  {
    value: 'RESTART',
    label: '驳回首节点',
    description: '驳回后重新从第一个审批节点开始',
  },
  {
    value: 'TO_STEP',
    label: '驳回指定节点',
    description: '驳回到当前节点之前的指定审批节点',
  },
  {
    value: 'TO_STARTER',
    label: '驳回发起人重提',
    description: '交回发起人修改后重新提交审批',
  },
];

let stepSerial = 0;

const router = useRouter();
const submitting = ref(false);

const definitionForm = reactive({
  definitionKey: '',
  definitionName: '',
  remark: '',
});

const steps = ref<DesignerStep[]>(defaultSteps());

const candidateUserCount = computed(
  () => new Set(steps.value.flatMap((step) => previewUserIds(step))).size,
);
const candidateGroupCount = computed(
  () => new Set(steps.value.flatMap((step) => previewGroupCodes(step))).size,
);
const previewJson = computed(() =>
  JSON.stringify(
    steps.value.map((step) => toPreviewStep(step)),
    null,
    2,
  ),
);
const validationIssue = computed(() => {
  try {
    buildPayload();
    return '';
  } catch (error) {
    return error instanceof Error ? error.message : '流程设计不完整';
  }
});

function defaultSteps() {
  return [
    createStep('直属主管审批', '1', '', 'END'),
    createStep('平台管理员复核', '', 'ADMIN', 'END'),
  ];
}

function createStep(
  name = '',
  candidateUserIdsText = '',
  candidateGroupCodesText = '',
  rejectStrategy: DesignerRejectStrategy = 'END',
  rejectTarget: null | number = null,
): DesignerStep {
  stepSerial += 1;
  return {
    localId: `step-${Date.now()}-${stepSerial}`,
    name,
    candidateUserIdsText,
    candidateGroupCodesText,
    rejectStrategy,
    rejectTarget,
  };
}

function addStep() {
  steps.value.push(createStep(`审批节点 ${steps.value.length + 1}`));
}

function duplicateStep(index: number) {
  const source = steps.value[index];
  if (!source) {
    return;
  }
  steps.value.splice(
    index + 1,
    0,
    createStep(
      `${source.name || `审批节点 ${index + 1}`} 副本`,
      source.candidateUserIdsText,
      source.candidateGroupCodesText,
      source.rejectStrategy,
      source.rejectTarget,
    ),
  );
}

function removeStep(index: number) {
  if (steps.value.length <= 1) {
    return;
  }
  steps.value.splice(index, 1);
}

function moveStep(index: number, offset: -1 | 1) {
  const nextIndex = index + offset;
  if (nextIndex < 0 || nextIndex >= steps.value.length) {
    return;
  }
  const current = steps.value[index];
  const target = steps.value[nextIndex];
  if (!current || !target) {
    return;
  }
  steps.value[index] = target;
  steps.value[nextIndex] = current;
}

function resetDesigner() {
  definitionForm.definitionKey = '';
  definitionForm.definitionName = '';
  definitionForm.remark = '';
  steps.value = defaultSteps();
}

async function saveDefinition(deployAfterCreate: boolean) {
  let payload: WorkflowDefinitionRequest;
  try {
    payload = buildPayload();
  } catch (error) {
    ElMessage.warning(
      error instanceof Error ? error.message : '流程设计不完整',
    );
    return;
  }

  submitting.value = true;
  try {
    const definition = await createWorkflowDefinition(payload);
    if (deployAfterCreate) {
      await deployWorkflowDefinition(definition.id);
      ElMessage.success('流程定义已保存并部署');
    } else {
      ElMessage.success('流程定义草稿已保存');
    }
    void router.push({ name: 'workflow-definitions' });
  } finally {
    submitting.value = false;
  }
}

function buildPayload(): WorkflowDefinitionRequest {
  const definitionKey = definitionForm.definitionKey.trim();
  const definitionName = definitionForm.definitionName.trim();
  if (!definitionKey) {
    throw new Error('请输入定义标识');
  }
  if (!definitionName) {
    throw new Error('请输入定义名称');
  }
  if (steps.value.length === 0) {
    throw new Error('至少需要一个审批节点');
  }
  return {
    definitionKey,
    definitionName,
    steps: steps.value.map((step, index) => toWorkflowStep(step, index)),
    remark: definitionForm.remark.trim() || undefined,
  };
}

function toWorkflowStep(step: DesignerStep, index: number): WorkflowStepInput {
  const name = step.name.trim();
  if (!name) {
    throw new Error(`第 ${index + 1} 个审批节点缺少名称`);
  }
  const candidateUserIds = parseUserIds(step.candidateUserIdsText, index);
  const candidateGroupCodes = normalizeGroupCodes(step.candidateGroupCodesText);
  if (candidateUserIds.length === 0 && candidateGroupCodes.length === 0) {
    throw new Error(`第 ${index + 1} 个审批节点至少需要候选人或候选组`);
  }
  const rejectTarget = resolveRejectTarget(step, index);
  return {
    name,
    candidateUserIds,
    candidateGroupCodes,
    rejectStrategy: step.rejectStrategy,
    rejectTarget,
  };
}

function toPreviewStep(step: DesignerStep) {
  return {
    name: step.name.trim(),
    candidateUserIds: previewUserIds(step),
    candidateGroupCodes: previewGroupCodes(step),
    rejectStrategy: step.rejectStrategy,
    rejectTarget: step.rejectStrategy === 'TO_STEP' ? step.rejectTarget : null,
  };
}

function previewUserIds(step: DesignerStep) {
  return uniqueNumbers(
    splitTokens(step.candidateUserIdsText)
      .map(Number)
      .filter((item) => Number.isInteger(item) && item > 0),
  );
}

function previewGroupCodes(step: DesignerStep) {
  return normalizeGroupCodes(step.candidateGroupCodesText);
}

function handleRejectStrategyChange(step: DesignerStep) {
  if (step.rejectStrategy !== 'TO_STEP') {
    step.rejectTarget = null;
  }
}

function rejectTargetOptions(currentIndex: number) {
  return steps.value.slice(0, Math.max(currentIndex, 0)).map((step, index) => ({
    value: index,
    label: `${index + 1}. ${step.name.trim() || `审批节点 ${index + 1}`}`,
  }));
}

function resolveRejectTarget(step: DesignerStep, index: number) {
  if (step.rejectStrategy !== 'TO_STEP') {
    return null;
  }
  if (index === 0) {
    throw new Error('首个审批节点不能驳回到指定节点');
  }
  if (step.rejectTarget === null || step.rejectTarget === undefined) {
    throw new Error(`第 ${index + 1} 个审批节点需要选择驳回目标节点`);
  }
  if (step.rejectTarget < 0 || step.rejectTarget >= index) {
    throw new Error(`第 ${index + 1} 个审批节点的驳回目标必须在当前节点之前`);
  }
  return step.rejectTarget;
}

function rejectTargetLabel(target: null | number) {
  if (target === null || target === undefined) {
    return '未选择目标节点';
  }
  const targetStep = steps.value[target];
  return targetStep
    ? `${target + 1}. ${targetStep.name.trim() || `审批节点 ${target + 1}`}`
    : '目标节点不存在';
}

function formatRejectStrategy(step: DesignerStep) {
  const label = rejectStrategyLabel(step.rejectStrategy);
  if (step.rejectStrategy !== 'TO_STEP') {
    return label;
  }
  return `${label}：${rejectTargetLabel(step.rejectTarget)}`;
}

function parseUserIds(value: string, index: number) {
  const tokens = splitTokens(value);
  const invalidTokens = tokens.filter(
    (token) => !Number.isInteger(Number(token)) || Number(token) <= 0,
  );
  if (invalidTokens.length > 0) {
    throw new Error(
      `第 ${index + 1} 个审批节点存在无效候选人 ID：${invalidTokens.join(', ')}`,
    );
  }
  return uniqueNumbers(tokens.map(Number));
}

function normalizeGroupCodes(value: string) {
  return uniqueStrings(splitTokens(value));
}

function splitTokens(value: string) {
  return value
    .split(/[\s,，、;；]+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function uniqueNumbers(values: number[]) {
  return [...new Set(values)];
}

function uniqueStrings(values: string[]) {
  return [...new Set(values)];
}

function formatPreviewCandidates(step: DesignerStep) {
  const users = previewUserIds(step);
  const groups = previewGroupCodes(step);
  return (
    [
      users.length > 0 ? `候选人：${users.join(', ')}` : '',
      groups.length > 0 ? `候选组：${groups.join(', ')}` : '',
    ]
      .filter(Boolean)
      .join('；') || '未配置候选范围'
  );
}

async function copyPreview() {
  await navigator.clipboard.writeText(previewJson.value);
  ElMessage.success('步骤 JSON 已复制');
}

function rejectStrategyLabel(value: DesignerStep['rejectStrategy']) {
  return (
    REJECT_STRATEGY_OPTIONS.find((option) => option.value === value)?.label ??
    '结束流程'
  );
}
</script>

<template>
  <div class="panel-stack workflow-designer-page">
    <section class="designer-hero dashboard-panel">
      <div>
        <span class="eyebrow">Workflow Designer</span>
        <h3>流程设计器</h3>
        <p class="muted-line">
          用可视化表单编排顺序审批、候选范围和驳回策略，保存后生成可部署的流程定义。
        </p>
      </div>
      <div class="designer-hero__actions">
        <ElButton @click="resetDesigner">重置</ElButton>
        <ElButton
          type="primary"
          plain
          :disabled="Boolean(validationIssue)"
          :loading="submitting"
          @click="saveDefinition(false)"
        >
          保存草稿
        </ElButton>
        <ElButton
          type="primary"
          :disabled="Boolean(validationIssue)"
          :loading="submitting"
          @click="saveDefinition(true)"
        >
          保存并部署
        </ElButton>
      </div>
    </section>

    <section class="dashboard-grid designer-stats">
      <article class="stat-card designer-stat designer-stat--primary">
        <span class="eyebrow">Steps</span>
        <strong>{{ steps.length }}</strong>
        <span>顺序审批节点</span>
      </article>
      <article class="stat-card designer-stat">
        <span class="eyebrow">Users</span>
        <strong>{{ candidateUserCount }}</strong>
        <span>候选人 ID 数</span>
      </article>
      <article class="stat-card designer-stat">
        <span class="eyebrow">Groups</span>
        <strong>{{ candidateGroupCount }}</strong>
        <span>候选组编码数</span>
      </article>
    </section>

    <section class="designer-workbench">
      <aside class="dashboard-panel designer-config-card">
        <div class="panel-head designer-card-head">
          <div>
            <span class="eyebrow">Definition</span>
            <h3>流程定义</h3>
          </div>
        </div>
        <ElForm label-position="top" :model="definitionForm">
          <ElFormItem label="定义标识">
            <ElInput
              v-model="definitionForm.definitionKey"
              placeholder="例如 leave-approval"
            />
          </ElFormItem>
          <ElFormItem label="定义名称">
            <ElInput
              v-model="definitionForm.definitionName"
              placeholder="例如 请假审批"
            />
          </ElFormItem>
          <ElFormItem label="备注">
            <ElInput
              v-model="definitionForm.remark"
              type="textarea"
              :rows="4"
              maxlength="255"
              show-word-limit
            />
          </ElFormItem>
        </ElForm>
        <ElAlert
          class="designer-tip"
          title="设计器会生成标准流程步骤数据，审批执行由后端状态机统一处理。"
          type="info"
          :closable="false"
          show-icon
        />
      </aside>

      <main class="dashboard-panel designer-canvas-card">
        <div class="panel-head designer-card-head">
          <div>
            <span class="eyebrow">Sequential Flow</span>
            <h3>审批节点</h3>
            <p class="muted-line">
              节点会按从上到下的顺序执行。每个节点至少配置一个候选人 ID
              或候选组编码。
            </p>
          </div>
          <ElButton type="primary" plain @click="addStep">新增节点</ElButton>
        </div>

        <div class="designer-lane">
          <article
            v-for="(step, index) in steps"
            :key="step.localId"
            class="designer-step-card"
          >
            <div class="step-index-block">
              <span>{{ index + 1 }}</span>
              <small>{{ index === 0 ? 'START' : 'STEP' }}</small>
            </div>
            <div class="step-body">
              <div class="step-title-row">
                <ElInput
                  v-model="step.name"
                  class="step-name-input"
                  placeholder="节点名称，例如 直属主管审批"
                />
                <div class="step-actions">
                  <ElButton
                    size="small"
                    :disabled="index === 0"
                    @click="moveStep(index, -1)"
                  >
                    上移
                  </ElButton>
                  <ElButton
                    size="small"
                    :disabled="index === steps.length - 1"
                    @click="moveStep(index, 1)"
                  >
                    下移
                  </ElButton>
                  <ElButton size="small" @click="duplicateStep(index)">
                    复制
                  </ElButton>
                  <ElButton
                    size="small"
                    type="danger"
                    plain
                    :disabled="steps.length === 1"
                    @click="removeStep(index)"
                  >
                    删除
                  </ElButton>
                </div>
              </div>
              <div class="step-candidate-grid">
                <ElFormItem label="候选人 ID">
                  <ElInput
                    v-model="step.candidateUserIdsText"
                    placeholder="用逗号分隔，例如 1, 2, 3"
                  />
                </ElFormItem>
                <ElFormItem label="候选组编码">
                  <ElInput
                    v-model="step.candidateGroupCodesText"
                    placeholder="用逗号分隔，例如 ADMIN, FINANCE"
                  />
                </ElFormItem>
              </div>
              <div class="step-reject-row">
                <ElFormItem label="驳回策略">
                  <ElSelect
                    v-model="step.rejectStrategy"
                    class="step-reject-select"
                    placeholder="选择驳回策略"
                    @change="handleRejectStrategyChange(step)"
                  >
                    <ElOption
                      v-for="option in REJECT_STRATEGY_OPTIONS"
                      :key="option.value"
                      :value="option.value"
                      :label="option.label"
                    >
                      <div class="reject-option">
                        <strong>{{ option.label }}</strong>
                        <small>{{ option.description }}</small>
                      </div>
                    </ElOption>
                  </ElSelect>
                </ElFormItem>
                <ElFormItem
                  v-if="step.rejectStrategy === 'TO_STEP'"
                  label="目标节点"
                >
                  <ElSelect
                    v-model="step.rejectTarget"
                    class="step-reject-select"
                    placeholder="选择当前节点之前的节点"
                    :disabled="index === 0"
                  >
                    <ElOption
                      v-for="option in rejectTargetOptions(index)"
                      :key="option.value"
                      :value="option.value"
                      :label="option.label"
                    />
                  </ElSelect>
                </ElFormItem>
              </div>
              <div class="candidate-preview-row">
                <ElTag
                  v-for="userId in previewUserIds(step)"
                  :key="`${step.localId}-user-${userId}`"
                  effect="plain"
                >
                  用户 {{ userId }}
                </ElTag>
                <ElTag
                  v-for="groupCode in previewGroupCodes(step)"
                  :key="`${step.localId}-group-${groupCode}`"
                  type="success"
                  effect="plain"
                >
                  {{ groupCode }}
                </ElTag>
                <span
                  v-if="
                    previewUserIds(step).length === 0 &&
                    previewGroupCodes(step).length === 0
                  "
                  class="muted-inline"
                  >未配置候选范围</span
                >
              </div>
            </div>
          </article>
        </div>
      </main>

      <aside class="dashboard-panel designer-preview-card">
        <div class="panel-head designer-card-head">
          <div>
            <span class="eyebrow">Output</span>
            <h3>执行产物</h3>
          </div>
          <ElTag :type="validationIssue ? 'warning' : 'success'" effect="plain">
            {{ validationIssue ? '待补齐' : '可保存' }}
          </ElTag>
        </div>

        <ElAlert
          v-if="validationIssue"
          class="designer-tip"
          :title="validationIssue"
          type="warning"
          :closable="false"
          show-icon
        />

        <div class="flow-preview">
          <div
            v-for="(step, index) in steps"
            :key="`preview-${step.localId}`"
            class="flow-preview-node"
          >
            <span>{{ index + 1 }}</span>
            <div>
              <strong>{{ step.name || `审批节点 ${index + 1}` }}</strong>
              <small>{{ formatPreviewCandidates(step) }}</small>
              <small class="flow-preview-reject"
                >驳回策略：{{ formatRejectStrategy(step) }}</small
              >
            </div>
          </div>
        </div>

        <div class="json-preview">
          <div class="json-preview__head">
            <span>steps JSON</span>
            <ElButton size="small" text @click="copyPreview">复制</ElButton>
          </div>
          <pre>{{ previewJson }}</pre>
        </div>
      </aside>
    </section>
  </div>
</template>

<style scoped lang="scss">
.workflow-designer-page {
  position: relative;
}

.designer-hero {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 24px;
  background:
    linear-gradient(135deg, rgb(22 119 255 / 13%), rgb(20 184 166 / 9%)),
    var(--bg-card);

  h3 {
    margin: 8px 0 0;
    font-size: 26px;
  }

  &__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    justify-content: flex-end;
  }
}

.designer-stats {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.designer-stat--primary {
  background:
    linear-gradient(135deg, rgb(22 119 255 / 16%), rgb(20 184 166 / 10%)),
    var(--bg-card);
}

.designer-workbench {
  display: grid;
  grid-template-columns: minmax(260px, 0.78fr) minmax(420px, 1.35fr) minmax(
      300px,
      0.9fr
    );
  gap: 18px;
  align-items: start;
}

.designer-config-card,
.designer-canvas-card,
.designer-preview-card {
  padding: 22px;
}

.designer-card-head {
  margin-bottom: 18px;
}

.designer-tip {
  margin-top: 16px;
}

.designer-lane {
  display: grid;
  gap: 14px;
}

.designer-step-card {
  position: relative;
  display: grid;
  grid-template-columns: 74px 1fr;
  gap: 16px;
  padding: 16px;
  background:
    linear-gradient(180deg, rgb(255 255 255 / 96%), rgb(248 250 252 / 94%)),
    var(--bg-card-muted);
  border: 1px solid rgb(22 119 255 / 13%);
  border-radius: 18px;
  box-shadow: 0 12px 28px rgb(15 23 42 / 5%);

  &::after {
    position: absolute;
    bottom: -15px;
    left: 52px;
    width: 2px;
    height: 15px;
    content: '';
    background: linear-gradient(180deg, rgb(22 119 255 / 36%), transparent);
  }

  &:last-child::after {
    display: none;
  }
}

.step-index-block {
  display: grid;
  gap: 8px;
  place-items: center;
  align-content: center;
  min-height: 112px;
  color: var(--accent);
  background: rgb(22 119 255 / 8%);
  border-radius: 16px;

  span {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 38px;
    height: 38px;
    font-weight: 800;
    color: #fff;
    background: var(--accent);
    border-radius: 999px;
    box-shadow: 0 10px 22px rgb(22 119 255 / 24%);
  }

  small {
    font-size: 11px;
    font-weight: 800;
  }
}

.step-body {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.step-title-row {
  display: flex;
  gap: 12px;
  align-items: center;
}

.step-name-input {
  flex: 1;
  min-width: 180px;
}

.step-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
}

.step-candidate-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;

  :deep(.el-form-item) {
    margin-bottom: 0;
  }
}

.candidate-preview-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  min-height: 26px;
}

.step-reject-row {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;

  :deep(.el-form-item) {
    margin-bottom: 0;
  }
}

.step-reject-select {
  width: 100%;
}

.reject-option {
  display: grid;
  gap: 2px;

  small {
    color: var(--text-soft);
  }
}

.muted-inline {
  font-size: 13px;
  color: var(--text-soft);
}

.flow-preview {
  display: grid;
  gap: 12px;
  margin-bottom: 18px;
}

.flow-preview-node {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 12px;
  background: var(--bg-card-muted);
  border: 1px solid var(--line);
  border-radius: 14px;

  > span {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
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
    line-height: 1.5;
    color: var(--text-soft);
  }
}

.flow-preview-reject {
  margin-top: 2px;
  font-weight: 600;
  color: var(--accent);
}

.json-preview {
  overflow: hidden;
  color: #dbeafe;
  background: #0f172a;
  border: 1px solid var(--line);
  border-radius: 16px;

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 12px;
    font-size: 12px;
    font-weight: 800;
    color: #93c5fd;
    text-transform: uppercase;
    letter-spacing: 0.08em;
    border-bottom: 1px solid rgb(255 255 255 / 8%);
  }

  pre {
    max-height: 360px;
    padding: 14px;
    margin: 0;
    overflow: auto;
    font-size: 12px;
    line-height: 1.7;
    white-space: pre-wrap;
  }
}

@media (max-width: 1320px) {
  .designer-workbench {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 860px) {
  .designer-hero,
  .step-title-row {
    display: grid;
  }

  .designer-stats,
  .step-candidate-grid {
    grid-template-columns: 1fr;
  }

  .designer-step-card {
    grid-template-columns: 1fr;
  }
}
</style>
