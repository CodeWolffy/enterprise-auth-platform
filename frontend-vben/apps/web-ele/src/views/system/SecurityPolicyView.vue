<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus';

import type { SecurityPolicy } from '#/api/system';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';

import { Check, Close, Edit, Refresh } from '@element-plus/icons-vue';
import {
  ElAlert,
  ElButton,
  ElCard,
  ElCol,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInputNumber,
  ElMessage,
  ElProgress,
  ElRow,
  ElSwitch,
  ElTabPane,
  ElTabs,
  ElTag,
} from 'element-plus';

import {
  queryPlatformSecurityPolicy,
  querySecurityPolicy,
  updatePlatformSecurityPolicy,
  updateSecurityPolicy,
} from '#/api/system';
import { PERMS } from '#/constants/permissions';
import { useAuthStore } from '#/store/auth';

type PolicyScope = 'platform' | 'tenant';

type PolicyItem = {
  desc: string;
  group: 'login' | 'password';
  key: keyof SecurityPolicy;
  label: string;
  suffix?: string;
  type?: 'boolean';
};

type RiskItem = {
  level: 'danger' | 'info' | 'warning';
  text: string;
};

const authStore = useAuthStore();

const defaultPolicy: SecurityPolicy = {
  captchaEnabled: true,
  loginFailureLockMinutes: 15,
  loginFailureMaxAttempts: 5,
  loginFailureWindowMinutes: 15,
  passwordExpireDays: 90,
  passwordHistoryCount: 0,
  passwordMaxLength: 64,
  passwordMinLength: 8,
  passwordRequireLetter: true,
  passwordRequireNumber: true,
  passwordRequireSpecial: false,
};

const policyItems: PolicyItem[] = [
  {
    desc: '新密码允许的最短字符数',
    group: 'password',
    key: 'passwordMinLength',
    label: '密码最小长度',
    suffix: '位',
  },
  {
    desc: '新密码允许的最长字符数',
    group: 'password',
    key: 'passwordMaxLength',
    label: '密码最大长度',
    suffix: '位',
  },
  {
    desc: '密码必须包含英文大小写字母之一',
    group: 'password',
    key: 'passwordRequireLetter',
    label: '要求包含字母',
    type: 'boolean',
  },
  {
    desc: '密码必须包含至少一个数字',
    group: 'password',
    key: 'passwordRequireNumber',
    label: '要求包含数字',
    type: 'boolean',
  },
  {
    desc: '密码必须包含符号字符',
    group: 'password',
    key: 'passwordRequireSpecial',
    label: '要求特殊字符',
    type: 'boolean',
  },
  {
    desc: '禁止复用最近使用过的密码数量',
    group: 'password',
    key: 'passwordHistoryCount',
    label: '历史密码限制',
    suffix: '次',
  },
  {
    desc: '超过天数后要求用户更换密码',
    group: 'password',
    key: 'passwordExpireDays',
    label: '密码有效期',
    suffix: '天',
  },
  {
    desc: '统计窗口内允许连续失败次数',
    group: 'login',
    key: 'loginFailureMaxAttempts',
    label: '登录失败阈值',
    suffix: '次',
  },
  {
    desc: '达到阈值后账号临时锁定时长',
    group: 'login',
    key: 'loginFailureLockMinutes',
    label: '失败锁定时长',
    suffix: '分钟',
  },
  {
    desc: '连续失败次数的滚动统计周期',
    group: 'login',
    key: 'loginFailureWindowMinutes',
    label: '失败统计窗口',
    suffix: '分钟',
  },
  {
    desc: '登录流程启用验证码校验',
    group: 'login',
    key: 'captchaEnabled',
    label: '登录验证码',
    type: 'boolean',
  },
];

const policies = reactive<Record<PolicyScope, SecurityPolicy>>({
  platform: { ...defaultPolicy },
  tenant: { ...defaultPolicy },
});
const editForm = reactive<SecurityPolicy>({ ...defaultPolicy });

const activeScope = ref<PolicyScope>('tenant');
const editingScope = ref<PolicyScope>('tenant');
const formRef = ref<FormInstance>();
const loading = ref(false);
const saving = ref(false);
const platformLoaded = ref(false);
const drawerVisible = ref(false);

const canManagePlatformPolicy = computed(() => authStore.isPlatformSuperAdmin);
const activePolicy = computed(() => policies[activeScope.value]);
const activeScopeTitle = computed(() =>
  activeScope.value === 'platform' ? '平台默认策略' : '当前租户策略',
);
const activeScopeHint = computed(() =>
  activeScope.value === 'platform'
    ? '平台默认策略会作为租户策略的基础值。'
    : `当前租户：${authStore.tenantId || '-'}`,
);

const passwordItems = computed(() =>
  policyItems.filter((item) => item.group === 'password'),
);
const loginItems = computed(() =>
  policyItems.filter((item) => item.group === 'login'),
);

const policyScore = computed(() => getPolicyScore(activePolicy.value));
const policyScoreStatus = computed(() => {
  if (policyScore.value >= 80) return 'success';
  if (policyScore.value >= 60) return undefined;
  return 'exception';
});

const passwordComplexityText = computed(() =>
  getPasswordComplexityText(activePolicy.value),
);

const riskItems = computed(() => buildRiskItems(activePolicy.value));
const riskAlertType = computed(() => {
  if (riskItems.value.some((item) => item.level === 'danger')) return 'error';
  if (riskItems.value.some((item) => item.level === 'warning'))
    return 'warning';
  return 'success';
});

const summaryCards = computed(() => [
  {
    label: '策略强度',
    value: policyScore.value,
    suffix: '分',
    hint: passwordComplexityText.value,
  },
  {
    label: '密码长度',
    value: `${activePolicy.value.passwordMinLength}-${activePolicy.value.passwordMaxLength}`,
    suffix: '位',
    hint: '允许的密码长度范围',
  },
  {
    label: '密码有效期',
    value: activePolicy.value.passwordExpireDays || '不过期',
    suffix: activePolicy.value.passwordExpireDays ? '天' : '',
    hint: `${activePolicy.value.passwordHistoryCount || '不限制'}${activePolicy.value.passwordHistoryCount ? '次' : ''}历史密码`,
  },
  {
    label: '失败锁定',
    value: activePolicy.value.loginFailureMaxAttempts,
    suffix: '次',
    hint: `${activePolicy.value.loginFailureWindowMinutes} 分钟内触发，锁定 ${activePolicy.value.loginFailureLockMinutes} 分钟`,
  },
]);

const changedItems = computed(() => {
  const source = policies[editingScope.value];
  return policyItems
    .filter((item) => source[item.key] !== editForm[item.key])
    .map((item) => ({
      label: item.label,
      nextValue: formatPolicyValue(editForm, item),
      previousValue: formatPolicyValue(source, item),
    }));
});

const drawerTitle = computed(() =>
  editingScope.value === 'platform' ? '编辑平台默认策略' : '编辑当前租户策略',
);

const formRules: FormRules<SecurityPolicy> = {
  captchaEnabled: [{ required: true, message: '请选择是否启用验证码' }],
  loginFailureLockMinutes: [
    { required: true, message: '请输入锁定时长' },
    { max: 1440, message: '锁定时长不能超过 1440 分钟', type: 'number' },
    { min: 1, message: '锁定时长不能小于 1 分钟', type: 'number' },
  ],
  loginFailureMaxAttempts: [
    { required: true, message: '请输入失败阈值' },
    { max: 20, message: '失败阈值不能超过 20 次', type: 'number' },
    { min: 1, message: '失败阈值不能小于 1 次', type: 'number' },
  ],
  loginFailureWindowMinutes: [
    { required: true, message: '请输入统计窗口' },
    { max: 1440, message: '统计窗口不能超过 1440 分钟', type: 'number' },
    { min: 1, message: '统计窗口不能小于 1 分钟', type: 'number' },
  ],
  passwordExpireDays: [
    { required: true, message: '请输入密码有效期' },
    { max: 3650, message: '密码有效期不能超过 3650 天', type: 'number' },
    { min: 0, message: '密码有效期不能小于 0 天', type: 'number' },
  ],
  passwordHistoryCount: [
    { required: true, message: '请输入历史密码限制' },
    { max: 24, message: '历史密码限制不能超过 24 次', type: 'number' },
    { min: 0, message: '历史密码限制不能小于 0 次', type: 'number' },
  ],
  passwordMaxLength: [
    { required: true, message: '请输入密码最大长度' },
    { max: 128, message: '密码最大长度不能超过 128 位', type: 'number' },
    { min: 6, message: '密码最大长度不能小于 6 位', type: 'number' },
    { validator: validatePasswordRange, trigger: 'change' },
  ],
  passwordMinLength: [
    { required: true, message: '请输入密码最小长度' },
    { max: 128, message: '密码最小长度不能超过 128 位', type: 'number' },
    { min: 6, message: '密码最小长度不能小于 6 位', type: 'number' },
    { validator: validatePasswordRange, trigger: 'change' },
  ],
  passwordRequireLetter: [{ required: true, message: '请选择字母规则' }],
  passwordRequireNumber: [{ required: true, message: '请选择数字规则' }],
  passwordRequireSpecial: [{ required: true, message: '请选择特殊字符规则' }],
};

onMounted(() => {
  void loadPolicies();
});

async function loadPolicies(scope?: PolicyScope) {
  loading.value = true;
  try {
    if (scope === 'platform') {
      await loadPlatformPolicy();
      return;
    }

    const tenantPolicy = await querySecurityPolicy();
    Object.assign(policies.tenant, { ...defaultPolicy, ...tenantPolicy });

    if (canManagePlatformPolicy.value) {
      await loadPlatformPolicy();
    }
  } catch {
    ElMessage.error('安全策略加载失败');
  } finally {
    loading.value = false;
  }
}

async function loadPlatformPolicy() {
  if (!canManagePlatformPolicy.value) return;
  const platformPolicy = await queryPlatformSecurityPolicy();
  Object.assign(policies.platform, { ...defaultPolicy, ...platformPolicy });
  platformLoaded.value = true;
}

async function handleTabChange(name: number | string) {
  const scope = name as PolicyScope;
  activeScope.value = scope;
  if (scope === 'platform' && !platformLoaded.value) {
    await loadPolicies('platform');
  }
}

function openDrawer(scope = activeScope.value) {
  editingScope.value = scope;
  Object.assign(editForm, policies[scope]);
  drawerVisible.value = true;
  void nextTick(() => formRef.value?.clearValidate());
}

function closeDrawer() {
  Object.assign(editForm, policies[editingScope.value]);
  drawerVisible.value = false;
}

async function savePolicy() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;

  saving.value = true;
  try {
    const payload = { ...editForm };
    const saved =
      editingScope.value === 'platform'
        ? await updatePlatformSecurityPolicy(payload)
        : await updateSecurityPolicy(payload);

    Object.assign(policies[editingScope.value], { ...defaultPolicy, ...saved });
    if (editingScope.value === 'platform') {
      await loadPolicies();
    }
    drawerVisible.value = false;
    ElMessage.success('安全策略保存成功');
  } catch {
    ElMessage.error('安全策略保存失败');
  } finally {
    saving.value = false;
  }
}

function validatePasswordRange(_rule: unknown, _value: unknown, callback: any) {
  if (editForm.passwordMinLength > editForm.passwordMaxLength) {
    callback(new Error('密码最小长度不能大于最大长度'));
    return;
  }
  callback();
}

function getPasswordComplexityCount(target: SecurityPolicy) {
  return (
    Number(target.passwordRequireLetter) +
    Number(target.passwordRequireNumber) +
    Number(target.passwordRequireSpecial)
  );
}

function getPasswordComplexityText(target: SecurityPolicy) {
  const rules = [
    target.passwordRequireLetter ? '字母' : '',
    target.passwordRequireNumber ? '数字' : '',
    target.passwordRequireSpecial ? '特殊字符' : '',
  ].filter(Boolean);
  return rules.length > 0 ? rules.join(' + ') : '未启用复杂度';
}

function getPolicyScore(target: SecurityPolicy) {
  let score = 0;
  if (target.passwordMinLength >= 8) score += 20;
  if (target.passwordMinLength >= 12) score += 10;
  score += getPasswordComplexityCount(target) * 10;
  if (target.passwordHistoryCount > 0) score += 10;
  if (target.passwordExpireDays > 0 && target.passwordExpireDays <= 180) {
    score += 10;
  }
  if (target.loginFailureMaxAttempts <= 5) score += 10;
  if (target.captchaEnabled) score += 10;
  return Math.min(score, 100);
}

function buildRiskItems(target: SecurityPolicy): RiskItem[] {
  const risks: RiskItem[] = [];
  if (target.passwordMinLength < 8) {
    risks.push({ level: 'danger', text: '密码最小长度低于 8 位' });
  }
  if (getPasswordComplexityCount(target) < 2) {
    risks.push({ level: 'warning', text: '密码复杂度规则少于 2 项' });
  }
  if (target.passwordHistoryCount === 0) {
    risks.push({ level: 'warning', text: '未限制历史密码复用' });
  }
  if (target.passwordExpireDays === 0) {
    risks.push({ level: 'warning', text: '密码有效期未启用' });
  }
  if (target.loginFailureMaxAttempts > 5) {
    risks.push({ level: 'warning', text: '登录失败阈值高于 5 次' });
  }
  if (!target.captchaEnabled) {
    risks.push({ level: 'danger', text: '登录验证码已关闭' });
  }
  return risks;
}

function formatBoolean(value: boolean) {
  return value ? '已启用' : '已关闭';
}

function formatPolicyValue(target: SecurityPolicy, item: PolicyItem) {
  const value = target[item.key];
  if (item.type === 'boolean') {
    return formatBoolean(Boolean(value));
  }
  return `${value}${item.suffix ?? ''}`;
}
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElRow :gutter="12" class="mb-4">
        <ElCol
          v-for="card in summaryCards"
          :key="card.label"
          :lg="6"
          :md="12"
          :sm="12"
          :xs="24"
        >
          <ElCard shadow="hover">
            <div class="stat-cell">
              <span class="stat-title">{{ card.label }}</span>
              <strong>
                {{ card.value }}<small>{{ card.suffix }}</small>
              </strong>
              <span class="stat-hint">{{ card.hint }}</span>
            </div>
          </ElCard>
        </ElCol>
      </ElRow>

      <ElCard v-loading="loading" shadow="never">
        <div class="mb-4 flex items-start justify-between gap-4">
          <div>
            <span class="eyebrow">安全策略</span>
            <h3>{{ activeScopeTitle }}</h3>
            <p class="card-desc">{{ activeScopeHint }}</p>
          </div>
          <div class="flex items-center gap-2">
            <ElButton :icon="Refresh" @click="loadPolicies(activeScope)">
              刷新
            </ElButton>
            <ElButton
              v-access:code="PERMS.upms.security.edit"
              :icon="Edit"
              type="primary"
              @click="openDrawer()"
            >
              编辑
            </ElButton>
          </div>
        </div>

        <ElTabs :model-value="activeScope" @tab-change="handleTabChange">
          <ElTabPane label="当前租户策略" name="tenant" />
          <ElTabPane
            v-if="canManagePlatformPolicy"
            label="平台默认策略"
            name="platform"
          />
        </ElTabs>

        <ElAlert
          v-if="riskItems.length > 0"
          :closable="false"
          :type="riskAlertType"
          class="mb-4"
          show-icon
        >
          <template #title>
            <div class="risk-line">
              <span>策略关注项</span>
              <ElTag
                v-for="item in riskItems"
                :key="item.text"
                :type="item.level"
                size="small"
              >
                {{ item.text }}
              </ElTag>
            </div>
          </template>
        </ElAlert>

        <div class="score-row mb-4">
          <span>策略强度</span>
          <ElProgress
            :percentage="policyScore"
            :status="policyScoreStatus"
            class="score-progress"
          />
        </div>

        <ElRow :gutter="16">
          <ElCol :lg="12" :md="24" :xs="24">
            <div class="section-title">密码规则</div>
            <ElDescriptions :column="1" border>
              <ElDescriptionsItem
                v-for="item in passwordItems"
                :key="item.key"
                :label="item.label"
              >
                <div class="desc-value">
                  <ElTag
                    v-if="item.type === 'boolean'"
                    :type="activePolicy[item.key] ? 'success' : 'info'"
                    size="small"
                  >
                    {{ formatPolicyValue(activePolicy, item) }}
                  </ElTag>
                  <span v-else>{{
                    formatPolicyValue(activePolicy, item)
                  }}</span>
                  <small>{{ item.desc }}</small>
                </div>
              </ElDescriptionsItem>
            </ElDescriptions>
          </ElCol>

          <ElCol :lg="12" :md="24" :xs="24">
            <div class="section-title">登录保护</div>
            <ElDescriptions :column="1" border>
              <ElDescriptionsItem
                v-for="item in loginItems"
                :key="item.key"
                :label="item.label"
              >
                <div class="desc-value">
                  <ElTag
                    v-if="item.type === 'boolean'"
                    :type="activePolicy[item.key] ? 'success' : 'info'"
                    size="small"
                  >
                    {{ formatPolicyValue(activePolicy, item) }}
                  </ElTag>
                  <span v-else>{{
                    formatPolicyValue(activePolicy, item)
                  }}</span>
                  <small>{{ item.desc }}</small>
                </div>
              </ElDescriptionsItem>
            </ElDescriptions>
          </ElCol>
        </ElRow>
      </ElCard>

      <ElDrawer
        v-model="drawerVisible"
        :close-on-click-modal="!saving"
        :show-close="!saving"
        :title="drawerTitle"
        size="520px"
      >
        <ElForm
          ref="formRef"
          label-position="top"
          :model="editForm"
          :rules="formRules"
          @submit.prevent="savePolicy"
        >
          <div class="drawer-section">
            <div class="section-title">密码规则</div>
            <ElRow :gutter="12">
              <ElCol :span="12">
                <ElFormItem label="密码最小长度" prop="passwordMinLength">
                  <ElInputNumber
                    v-model="editForm.passwordMinLength"
                    :max="128"
                    :min="6"
                    controls-position="right"
                  />
                </ElFormItem>
              </ElCol>
              <ElCol :span="12">
                <ElFormItem label="密码最大长度" prop="passwordMaxLength">
                  <ElInputNumber
                    v-model="editForm.passwordMaxLength"
                    :max="128"
                    :min="6"
                    controls-position="right"
                  />
                </ElFormItem>
              </ElCol>
              <ElCol :span="12">
                <ElFormItem label="要求包含字母" prop="passwordRequireLetter">
                  <ElSwitch v-model="editForm.passwordRequireLetter" />
                </ElFormItem>
              </ElCol>
              <ElCol :span="12">
                <ElFormItem label="要求包含数字" prop="passwordRequireNumber">
                  <ElSwitch v-model="editForm.passwordRequireNumber" />
                </ElFormItem>
              </ElCol>
              <ElCol :span="12">
                <ElFormItem label="要求特殊字符" prop="passwordRequireSpecial">
                  <ElSwitch v-model="editForm.passwordRequireSpecial" />
                </ElFormItem>
              </ElCol>
              <ElCol :span="12">
                <ElFormItem label="历史密码限制" prop="passwordHistoryCount">
                  <ElInputNumber
                    v-model="editForm.passwordHistoryCount"
                    :max="24"
                    :min="0"
                    controls-position="right"
                  />
                </ElFormItem>
              </ElCol>
              <ElCol :span="12">
                <ElFormItem label="密码有效期" prop="passwordExpireDays">
                  <ElInputNumber
                    v-model="editForm.passwordExpireDays"
                    :max="3650"
                    :min="0"
                    controls-position="right"
                  />
                </ElFormItem>
              </ElCol>
            </ElRow>
          </div>

          <div class="drawer-section">
            <div class="section-title">登录保护</div>
            <ElRow :gutter="12">
              <ElCol :span="12">
                <ElFormItem label="登录失败阈值" prop="loginFailureMaxAttempts">
                  <ElInputNumber
                    v-model="editForm.loginFailureMaxAttempts"
                    :max="20"
                    :min="1"
                    controls-position="right"
                  />
                </ElFormItem>
              </ElCol>
              <ElCol :span="12">
                <ElFormItem label="失败锁定时长" prop="loginFailureLockMinutes">
                  <ElInputNumber
                    v-model="editForm.loginFailureLockMinutes"
                    :max="1440"
                    :min="1"
                    controls-position="right"
                  />
                </ElFormItem>
              </ElCol>
              <ElCol :span="12">
                <ElFormItem
                  label="失败统计窗口"
                  prop="loginFailureWindowMinutes"
                >
                  <ElInputNumber
                    v-model="editForm.loginFailureWindowMinutes"
                    :max="1440"
                    :min="1"
                    controls-position="right"
                  />
                </ElFormItem>
              </ElCol>
              <ElCol :span="12">
                <ElFormItem label="登录验证码" prop="captchaEnabled">
                  <ElSwitch v-model="editForm.captchaEnabled" />
                </ElFormItem>
              </ElCol>
            </ElRow>
          </div>
        </ElForm>

        <ElAlert
          v-if="changedItems.length > 0"
          :closable="false"
          class="mb-4"
          type="info"
        >
          <template #title>
            <div class="change-line">
              <span>待保存变更</span>
              <ElTag
                v-for="item in changedItems"
                :key="item.label"
                size="small"
              >
                {{ item.label }}：{{ item.previousValue }} →
                {{ item.nextValue }}
              </ElTag>
            </div>
          </template>
        </ElAlert>

        <template #footer>
          <div class="flex justify-end gap-2">
            <ElButton :disabled="saving" :icon="Close" @click="closeDrawer">
              取消
            </ElButton>
            <ElButton
              v-access:code="PERMS.upms.security.edit"
              :icon="Check"
              :loading="saving"
              type="primary"
              @click="savePolicy"
            >
              保存
            </ElButton>
          </div>
        </template>
      </ElDrawer>
    </div>
  </div>
</template>

<style scoped>
.mb-4 {
  margin-bottom: 16px;
}

.gap-4 {
  gap: 16px;
}

.card-desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.eyebrow {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

h3 {
  margin: 4px 0 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.stat-cell {
  display: grid;
  gap: 8px;
}

.stat-title,
.stat-hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.stat-cell strong {
  font-size: 26px;
  line-height: 1;
  color: var(--el-text-color-primary);
}

.stat-cell small {
  margin-left: 4px;
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
}

.score-row {
  display: flex;
  gap: 16px;
  align-items: center;
  padding: 12px 14px;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
}

.score-row > span {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.score-progress {
  width: 100%;
}

.risk-line,
.change-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.section-title {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.desc-value {
  display: grid;
  gap: 4px;
}

.desc-value small {
  color: var(--el-text-color-secondary);
}

.drawer-section {
  margin-bottom: 12px;
}

.drawer-section :deep(.ElInputNumber) {
  width: 100%;
}

@media (max-width: 768px) {
  .score-row,
  .flex.items-start.justify-between {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
