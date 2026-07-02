<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { Delete } from '@element-plus/icons-vue';
import {
  ElButton,
  ElCheckTag,
  ElDivider,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElResult,
  ElRadioButton,
  ElRadioGroup,
  ElSwitch,
  type FormInstance,
  type FormRules,
} from 'element-plus';
import { delObj, getList, getPresets, testSend } from '#/api/upms/mail-channel';
import type { MailChannel, MailChannelPreset } from '#/types/system';

const loading = ref(false);
const saving = ref(false);
const deleting = ref(false);
const testing = ref(false);
const formRef = ref<FormInstance>();
const presets = ref<MailChannelPreset[]>([]);
const mailChannel = ref<MailChannel | null>(null);
const formError = ref('');
const testEmail = ref('');
const testResult = ref<{ success: boolean; message: string } | null>(null);

const form = reactive({
  provider: 'QQ',
  mailHost: 'smtp.qq.com',
  mailPort: 587,
  mailUsername: '',
  mailPassword: '',
  mailFrom: '',
  mailProtocol: 'smtp',
  useSsl: false,
  useStartTls: true,
  enabled: true,
});

const hasOwnChannel = computed(() => Boolean(mailChannel.value && !mailChannel.value.inherited));
const needsPassword = computed(() => !hasOwnChannel.value || !mailChannel.value?.passwordConfigured);
const channelTitle = computed(() => {
  if (!mailChannel.value) return '未配置';
  return mailChannel.value.inherited ? '继承平台默认' : '当前租户已配置';
});
const channelSubtitle = computed(() => {
  if (!mailChannel.value) return '尚未配置邮件发送渠道';
  return `${presetLabel(mailChannel.value.provider)} · ${mailChannel.value.mailHost}${mailChannel.value.mailPort ? ':' + mailChannel.value.mailPort : ''}`;
});
const securityModeLabel = computed(() => {
  if (!mailChannel.value) return '-';
  if (mailChannel.value.useSsl) return 'SSL';
  if (mailChannel.value.useStartTls) return 'STARTTLS';
  return '明文（不建议）';
});
const securityModeHint = computed(() => {
  if (!mailChannel.value) return '配置后将显示安全连接方式';
  if (mailChannel.value.useSsl) return 'SMTP over SSL（端口推荐 465）';
  if (mailChannel.value.useStartTls) return 'SMTP with STARTTLS（端口推荐 587）';
  return '当前未启用加密，请谨慎使用';
});
const passwordPlaceholder = computed(() => (needsPassword.value ? '首次保存必须填写授权码/密码' : '留空则不修改现有密码'));
const passwordHint = computed(() => (needsPassword.value ? '当前没有本租户可复用的已保存凭据。' : '为安全起见，已保存凭据不会回显。'));
const saveButtonText = computed(() => (hasOwnChannel.value ? '保存配置' : '创建当前租户配置'));

const formRules: FormRules = {
  mailHost: [{ required: true, message: '请输入 SMTP 服务器地址', trigger: 'blur' }],
  mailPort: [{ required: true, message: '请输入端口', trigger: 'blur' }],
  mailUsername: [
    { required: true, message: '请输入邮箱账号', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: ['blur', 'change'] },
  ],
  mailFrom: [
    { required: true, message: '请输入发件人地址', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' },
  ],
};

const presetLabelMap: Record<string, string> = {
  QQ: 'QQ 邮箱',
  NETEASE: '163 邮箱',
  GMAIL: 'Gmail',
  OUTLOOK: 'Outlook',
  CUSTOM: '自定义',
};

function presetLabel(code: string) {
  return presetLabelMap[code] || code;
}

function onPresetChange(code: string | number | boolean | undefined) {
  const preset = presets.value.find((p) => p.code === code);
  if (!preset) return;
  form.mailHost = preset.host;
  form.mailPort = preset.port;
  form.mailProtocol = preset.protocol;
  form.useSsl = preset.useSsl;
  form.useStartTls = preset.useStartTls;
}

function onSslChange(value: string | number | boolean) {
  if (Boolean(value)) {
    form.useStartTls = false;
  }
}

function onStartTlsChange(value: string | number | boolean) {
  if (Boolean(value)) {
    form.useSsl = false;
  }
}

function applyPort(port: number) {
  form.mailPort = port;
  if (port === 465) {
    form.useSsl = true;
    form.useStartTls = false;
  } else if (port === 587) {
    form.useSsl = false;
    form.useStartTls = true;
  } else if (port === 25) {
    form.useSsl = false;
    form.useStartTls = false;
  }
}

async function loadPresets() {
  try {
    presets.value = await getPresets();
  } catch {
    // 预设加载失败不影响自定义配置
  }
}

async function initPage() {
  formError.value = '';
  loading.value = true;
  try {
    const config = await getList();
    mailChannel.value = config;
    if (config) {
      form.provider = config.provider || 'CUSTOM';
      form.mailHost = config.mailHost;
      form.mailPort = config.mailPort;
      form.mailUsername = config.mailUsername;
      form.mailPassword = '';
      form.mailFrom = config.mailFrom;
      form.mailProtocol = config.mailProtocol || 'smtp';
      form.useSsl = config.useSsl;
      form.useStartTls = config.useStartTls;
      form.enabled = config.enabled;
      testEmail.value = '';
      testResult.value = null;
    }
  } catch (e: any) {
    formError.value = e?.message || '加载邮件渠道配置失败';
  } finally {
    loading.value = false;
  }
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;

  if (needsPassword.value && !form.mailPassword) {
    ElMessage.error('首次配置当前租户 SMTP 时必须填写授权码/密码');
    return;
  }

  if (form.useSsl && form.useStartTls) {
    ElMessage.error('SSL 与 STARTTLS 不能同时启用');
    return;
  }

  saving.value = true;
  try {
    const { addObj } = await import('#/api/upms/mail-channel');
    const payload = {
      provider: form.provider,
      mailHost: form.mailHost,
      mailPort: form.mailPort,
      mailUsername: form.mailUsername,
      mailPassword: form.mailPassword || undefined,
      mailFrom: form.mailFrom,
      mailProtocol: form.mailProtocol,
      useSsl: form.useSsl,
      useStartTls: form.useStartTls,
      enabled: form.enabled,
    };
    const saved = await addObj(payload);
    mailChannel.value = saved;
    form.mailPassword = '';
    ElMessage.success('邮件渠道配置已保存');
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败');
  } finally {
    saving.value = false;
  }
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm('确认删除当前租户的邮件渠道配置？删除后可能会继承平台默认邮件通道。', '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }

  deleting.value = true;
  try {
    await delObj();
    await initPage();
    ElMessage.success('邮件渠道配置已删除');
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败');
  } finally {
    deleting.value = false;
  }
}

async function handleTestSend() {
  if (!testEmail.value) return;

  // 必须先有实际生效的邮件渠道才能测试
  if (!mailChannel.value?.enabled) {
    ElMessage.warning('请先保存并启用邮件渠道配置后再发送测试邮件');
    return;
  }

  testing.value = true;
  testResult.value = null;
  try {
    const result = await testSend(testEmail.value);
    const message = (result as any)?.message || '测试邮件发送成功';
    testResult.value = { success: true, message };
    ElMessage.success(message);
  } catch (e: any) {
    testResult.value = { success: false, message: e?.message || '发送失败' };
  } finally {
    testing.value = false;
  }
}

onMounted(async () => {
  loading.value = true;
  try {
    await Promise.all([loadPresets(), initPage()]);
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="hx-layout-container" v-loading="loading">
    <div class="hx-layout-container-auto hx-layout-container-view mail-channel-view">
      <!-- 状态卡片 -->
      <div class="stat-card-grid">
        <div class="stat-card">
          <div class="stat-card-label">邮件渠道</div>
          <div class="stat-card-value">{{ channelTitle }}</div>
          <div class="stat-card-desc">{{ channelSubtitle }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-card-label">状态</div>
          <div class="stat-card-value">{{ mailChannel?.enabled ? '已启用' : '未启用' }}</div>
          <div class="stat-card-desc">{{ mailChannel?.enabled ? '密码重置邮件将通过 SMTP 发送' : '密码重置邮件不会通过该通道发送' }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-card-label">凭据</div>
          <div class="stat-card-value">{{ mailChannel?.passwordConfigured ? '已保存' : '未保存' }}</div>
          <div class="stat-card-desc">{{ mailChannel?.passwordConfigured ? '授权码/密码不会在页面回显' : '首次保存必须填写授权码/密码' }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-card-label">安全模式</div>
          <div class="stat-card-value">{{ securityModeLabel }}</div>
          <div class="stat-card-desc">{{ securityModeHint }}</div>
        </div>
      </div>

      <!-- 错误面板 -->
      <div v-if="formError" class="error-panel">
        <ElResult icon="error" :title="formError" sub-title="">
          <template #extra>
            <ElButton type="primary" @click="initPage">重试加载</ElButton>
          </template>
        </ElResult>
      </div>

      <!-- 配置表单 -->
      <div v-else class="config-panel">
        <div class="panel-header">
          <div>
            <div class="panel-header-label">系统设置</div>
            <h3 class="panel-header-title">邮件渠道配置</h3>
            <p v-if="mailChannel?.inherited" class="panel-header-hint">
              当前租户正在继承平台默认邮件通道。保存后会创建当前租户自己的配置，需重新填写授权码/密码。
            </p>
          </div>
          <div class="panel-header-actions">
            <ElButton v-if="hasOwnChannel" v-access:code="'upms:sysmail:del'" type="danger" plain :icon="Delete" :loading="deleting" @click="handleDelete">删除配置</ElButton>
          </div>
        </div>

        <ElForm ref="formRef" :model="form" :rules="formRules" label-width="140px" class="mail-channel-form">
          <ElFormItem label="渠道预设">
            <ElRadioGroup v-model="form.provider" @change="onPresetChange">
              <ElRadioButton v-for="preset in presets" :key="preset.code" :value="preset.code">{{ presetLabel(preset.code) }}</ElRadioButton>
            </ElRadioGroup>
          </ElFormItem>

          <ElDivider />

          <ElFormItem label="SMTP 服务器" prop="mailHost">
            <ElInput v-model="form.mailHost" placeholder="例如 smtp.qq.com" />
          </ElFormItem>

          <ElFormItem label="端口" prop="mailPort">
            <ElInputNumber v-model="form.mailPort" :min="1" :max="65535" class="port-input" />
            <div class="port-quick-tags">
              <ElCheckTag :checked="form.mailPort === 25" @change="applyPort(25)">25（明文）</ElCheckTag>
              <ElCheckTag :checked="form.mailPort === 465" @change="applyPort(465)">465（SSL）</ElCheckTag>
              <ElCheckTag :checked="form.mailPort === 587" @change="applyPort(587)">587（STARTTLS）</ElCheckTag>
            </div>
          </ElFormItem>

          <ElFormItem label="邮箱账号" prop="mailUsername">
            <ElInput v-model="form.mailUsername" placeholder="例如 name@example.com" />
          </ElFormItem>

          <ElFormItem label="授权码/密码" prop="mailPassword">
            <ElInput v-model="form.mailPassword" type="password" show-password :placeholder="passwordPlaceholder" />
            <div class="field-hint">{{ passwordHint }}</div>
          </ElFormItem>

          <ElFormItem label="发件人地址" prop="mailFrom">
            <ElInput v-model="form.mailFrom" placeholder="通常与邮箱账号相同" />
          </ElFormItem>

          <ElDivider />

          <ElFormItem label="连接方式">
            <div class="security-switches">
              <ElSwitch v-model="form.useSsl" active-text="使用 SSL（常见端口 465）" @change="onSslChange" />
              <ElSwitch v-model="form.useStartTls" active-text="使用 STARTTLS（常见端口 587）" @change="onStartTlsChange" />
            </div>
          </ElFormItem>

          <ElFormItem label="启用状态">
            <ElSwitch v-model="form.enabled" active-text="启用" inactive-text="停用" />
          </ElFormItem>

          <ElDivider />

          <ElFormItem>
            <ElButton v-access:code="'upms:sysmail:edit'" type="primary" @click="submitForm" :loading="saving">{{ saveButtonText }}</ElButton>
            <ElButton @click="initPage">重置</ElButton>
          </ElFormItem>
        </ElForm>
      </div>

      <!-- 测试邮件 -->
      <div v-if="form.enabled" class="test-panel">
        <div class="panel-header-test">
          <div class="panel-header-label">测试</div>
          <h3 class="panel-header-title">发送测试邮件</h3>
          <p class="panel-header-hint">
            测试会使用当前实际生效的通道{{ mailChannel?.inherited ? `（来自 ${mailChannel.sourceTenantId}）` : '' }}。若未保存，请先保存配置。
          </p>
        </div>

        <ElForm label-width="120px" label-position="left" class="mail-channel-form" @submit.prevent
        >
          <ElFormItem label="接收邮箱">
            <ElInput v-model="testEmail" :placeholder="mailChannel?.mailUsername || form.mailUsername || '请输入接收测试邮件的邮箱'" class="test-email-input">
              <template #append>
                <ElButton :loading="testing" :disabled="!testEmail" @click="handleTestSend">发送测试</ElButton>
              </template>
            </ElInput>
          </ElFormItem>
          <div v-if="testResult" class="test-result" :class="testResult.success ? 'success' : 'fail'">
            {{ testResult.message }}
          </div>
        </ElForm>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mail-channel-view {
  --mail-card-bg: #fff;
  --mail-card-border: #e5e7eb;
  --mail-text-secondary: #6b7280;
  --mail-text-hint: #64748b;
  --mail-success-bg: #f0fdf4;
  --mail-success-text: #166534;
  --mail-success-border: #bbf7d0;
  --mail-fail-bg: #fef2f2;
  --mail-fail-text: #991b1b;
  --mail-fail-border: #fecaca;
}

.stat-card-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  background: var(--mail-card-bg);
  border: 1px solid var(--mail-card-border);
  border-radius: 8px;
  padding: 16px;
  min-width: 0;
}

.stat-card-label {
  font-size: 12px;
  color: var(--mail-text-secondary);
  margin-bottom: 8px;
}

.stat-card-value {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 4px;
  word-break: break-all;
}

.stat-card-desc {
  font-size: 13px;
  color: var(--mail-text-secondary);
  line-height: 1.5;
}

.error-panel {
  background: var(--mail-card-bg);
  border: 1px solid var(--mail-fail-border);
  border-radius: 8px;
  padding: 24px;
  margin-bottom: 24px;
}

.config-panel,
.test-panel {
  background: var(--mail-card-bg);
  border: 1px solid var(--mail-card-border);
  border-radius: 8px;
  padding: 24px;
  margin-bottom: 24px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 24px;
}

.panel-header-test {
  margin-bottom: 24px;
}

.panel-header-label {
  font-size: 12px;
  color: var(--mail-text-secondary);
  margin-bottom: 4px;
}

.panel-header-title {
  margin: 0 0 8px 0;
}

.panel-header-hint {
  margin: 0;
  color: var(--mail-text-hint);
  font-size: 13px;
  line-height: 1.6;
}

.panel-header-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.mail-channel-form {
  max-width: 640px;
}

.port-input {
  width: 100%;
}

.port-quick-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.field-hint {
  margin-top: 4px;
  color: var(--mail-text-hint);
  font-size: 13px;
  line-height: 1.6;
}

.security-switches {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 16px;
}

.test-email-input {
  max-width: 360px;
}

.test-result {
  margin-top: 8px;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 13px;
  max-width: 360px;
}

.test-result.success {
  background: var(--mail-success-bg);
  color: var(--mail-success-text);
  border: 1px solid var(--mail-success-border);
}

.test-result.fail {
  background: var(--mail-fail-bg);
  color: var(--mail-fail-text);
  border: 1px solid var(--mail-fail-border);
}

/* 平板：状态卡片 2 列，表头垂直堆叠 */
@media (max-width: 1024px) {
  .stat-card-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

/* 手机：单列、紧凑间距、表单项顶部对齐 */
@media (max-width: 640px) {
  .stat-card-grid {
    grid-template-columns: 1fr;
  }

  .config-panel,
  .test-panel {
    padding: 16px;
  }

  .panel-header {
    flex-direction: column;
    align-items: stretch;
  }

  .panel-header-actions {
    justify-content: flex-end;
  }

  .mail-channel-form {
    max-width: 100%;
  }

  .mail-channel-form :deep(.el-form-item__label) {
    float: none;
    display: block;
    text-align: left;
    padding: 0 0 6px 0;
    line-height: 1.4;
  }

  .mail-channel-form :deep(.el-form-item__content) {
    margin-left: 0 !important;
  }

  .security-switches {
    flex-direction: column;
    gap: 8px;
  }

  .test-email-input {
    max-width: 100%;
  }

  .test-result {
    max-width: 100%;
  }
}
</style>
