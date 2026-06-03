<template>
  <div class="panel-stack" v-loading="loading">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">邮件渠道</span>
        <strong>{{ channelTitle }}</strong>
        <span>{{ channelSubtitle }}</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">状态</span>
        <strong>{{ mailChannel?.enabled ? '已启用' : '未启用' }}</strong>
        <span>{{ mailChannel?.enabled ? '密码重置邮件将通过 SMTP 发送' : '密码重置邮件不会通过该通道发送' }}</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">凭据</span>
        <strong>{{ mailChannel?.passwordConfigured ? '已保存' : '未保存' }}</strong>
        <span>{{ mailChannel?.passwordConfigured ? '授权码/密码不会在页面回显' : '首次保存必须填写授权码/密码' }}</span>
      </article>
    </section>

    <section v-if="formError" class="dashboard-panel error-panel">
      <el-result icon="error" :title="formError" sub-title="">
        <template #extra>
          <el-button type="primary" @click="loadConfig">重试加载</el-button>
        </template>
      </el-result>
    </section>

    <section v-else class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">系统设置</span>
          <h3>邮件渠道配置</h3>
          <p v-if="mailChannel?.inherited" class="panel-hint">
            当前租户正在继承平台默认邮件通道。保存后会创建当前租户自己的配置，需重新填写授权码/密码。
          </p>
        </div>
        <div class="panel-head-actions">
          <el-button
            v-if="hasOwnChannel"
            v-permission="'system:write'"
            type="danger"
            plain
            @click="handleDelete"
            :loading="deleting"
          >删除配置</el-button>
        </div>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="120px"
        label-position="left"
        class="mail-channel-form"
        @submit.prevent
      >
        <el-form-item label="渠道预设">
          <el-radio-group v-model="form.provider" @change="onPresetChange">
            <el-radio-button
              v-for="preset in presets"
              :key="preset.code"
              :value="preset.code"
            >{{ presetLabel(preset.code) }}</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-divider />

        <el-form-item label="SMTP 服务器" prop="mailHost">
          <el-input v-model="form.mailHost" placeholder="例如 smtp.qq.com" />
        </el-form-item>

        <el-form-item label="端口" prop="mailPort">
          <el-input-number v-model="form.mailPort" :min="1" :max="65535" />
        </el-form-item>

        <el-form-item label="邮箱账号" prop="mailUsername">
          <el-input v-model="form.mailUsername" placeholder="例如 name@example.com" />
        </el-form-item>

        <el-form-item label="授权码/密码" prop="mailPassword">
          <el-input
            v-model="form.mailPassword"
            type="password"
            show-password
            :placeholder="passwordPlaceholder"
          />
          <div class="field-hint">
            {{ passwordHint }}
          </div>
        </el-form-item>

        <el-form-item label="发件人地址" prop="mailFrom">
          <el-input v-model="form.mailFrom" placeholder="通常与邮箱账号相同" />
        </el-form-item>

        <el-divider />

        <el-form-item label="连接方式">
          <el-checkbox v-model="form.useSsl" @change="onSslChange">使用 SSL（常见端口 465）</el-checkbox>
          <el-checkbox v-model="form.useStartTls" style="margin-left: 16px" @change="onStartTlsChange">使用 STARTTLS（常见端口 587）</el-checkbox>
        </el-form-item>

        <el-form-item label="启用状态">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>

        <el-divider />

        <el-form-item>
          <el-button
            v-permission="'system:write'"
            type="primary"
            :loading="saving"
            @click="handleSave"
          >{{ saveButtonText }}</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section v-if="mailChannel?.enabled" class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">测试</span>
          <h3>发送测试邮件</h3>
          <p class="panel-hint">
            测试会使用当前实际生效的通道{{ mailChannel?.inherited ? `（来自 ${mailChannel.sourceTenantId}）` : '' }}。
          </p>
        </div>
      </div>

      <el-form label-width="120px" label-position="left" class="mail-channel-form" @submit.prevent>
        <el-form-item label="接收邮箱">
          <el-input
            v-model="testEmail"
            :placeholder="mailChannel?.mailUsername || '请输入接收测试邮件的邮箱'"
            style="max-width: 360px"
          >
            <template #append>
              <el-button
                :loading="testing"
                :disabled="!testEmail"
                @click="handleTestSend"
              >发送测试</el-button>
            </template>
          </el-input>
        </el-form-item>
        <div v-if="testResult" class="test-result" :class="testResult.success ? 'success' : 'fail'">
          {{ testResult.message }}
        </div>
      </el-form>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { queryPresets, queryMailChannel, saveMailChannel, deleteMailChannel, testSendMail } from '@/api/modules/mailChannel'
import type { MailChannel, MailChannelPreset } from '@/types/mailChannel'

const presets = ref<MailChannelPreset[]>([])
const mailChannel = ref<MailChannel | null>(null)
const formRef = ref<FormInstance>()
const formError = ref('')
const loading = ref(false)
const saving = ref(false)
const deleting = ref(false)
const testing = ref(false)
const testEmail = ref('')
const testResult = ref<{ success: boolean; message: string } | null>(null)

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
})

const hasOwnChannel = computed(() => Boolean(mailChannel.value && !mailChannel.value.inherited))
const needsPassword = computed(() => !hasOwnChannel.value || !mailChannel.value?.passwordConfigured)
const channelTitle = computed(() => {
  if (!mailChannel.value) return '未配置'
  return mailChannel.value.inherited ? '继承平台默认' : '当前租户已配置'
})
const channelSubtitle = computed(() => {
  if (!mailChannel.value) return '尚未配置邮件发送渠道'
  const source = mailChannel.value.inherited ? ` · 来源 ${mailChannel.value.sourceTenantId}` : ''
  return `${mailChannel.value.provider} · ${mailChannel.value.mailHost}${source}`
})
const passwordPlaceholder = computed(() => needsPassword.value ? '首次保存必须填写授权码/密码' : '留空则不修改现有密码')
const passwordHint = computed(() => needsPassword.value ? '当前没有本租户可复用的已保存凭据。' : '为安全起见，已保存凭据不会回显。')
const saveButtonText = computed(() => hasOwnChannel.value ? '保存配置' : '创建当前租户配置')

const formRules: FormRules = {
  mailHost: [{ required: true, message: '请输入 SMTP 服务器地址', trigger: 'blur' }],
  mailPort: [{ required: true, message: '请输入端口', trigger: 'blur' }],
  mailUsername: [{ required: true, message: '请输入邮箱账号', trigger: 'blur' }],
  mailFrom: [
    { required: true, message: '请输入发件人地址', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' },
  ],
}

const presetLabelMap: Record<string, string> = {
  QQ: 'QQ 邮箱',
  NETEASE: '163 邮箱',
  GMAIL: 'Gmail',
  OUTLOOK: 'Outlook',
  CUSTOM: '自定义',
}

function presetLabel(code: string) {
  return presetLabelMap[code] || code
}

function onPresetChange(code: string) {
  const preset = presets.value.find((p) => p.code === code)
  if (!preset) return
  form.mailHost = preset.host
  form.mailPort = preset.port
  form.mailProtocol = preset.protocol
  form.useSsl = preset.useSsl
  form.useStartTls = preset.useStartTls
}

function onSslChange(value: boolean) {
  if (value) {
    form.useStartTls = false
  }
}

function onStartTlsChange(value: boolean) {
  if (value) {
    form.useSsl = false
  }
}

async function loadPresets() {
  try {
    presets.value = await queryPresets()
  } catch {
    // 预设加载失败不影响自定义配置
  }
}

async function loadConfig() {
  formError.value = ''
  try {
    const config = await queryMailChannel()
    mailChannel.value = config
    if (config) {
      applyConfigToForm(config)
      testEmail.value = ''
    } else {
      resetForm()
      testEmail.value = ''
    }
  } catch (e: any) {
    formError.value = e?.message || '加载邮件渠道配置失败'
  }
}

function applyConfigToForm(config: MailChannel) {
  form.provider = config.provider || 'CUSTOM'
  form.mailHost = config.mailHost
  form.mailPort = config.mailPort
  form.mailUsername = config.mailUsername
  form.mailPassword = ''
  form.mailFrom = config.mailFrom
  form.mailProtocol = config.mailProtocol || 'smtp'
  form.useSsl = config.useSsl
  form.useStartTls = config.useStartTls
  form.enabled = config.enabled
}

function resetForm() {
  form.provider = 'QQ'
  form.mailHost = 'smtp.qq.com'
  form.mailPort = 587
  form.mailUsername = ''
  form.mailPassword = ''
  form.mailFrom = ''
  form.mailProtocol = 'smtp'
  form.useSsl = false
  form.useStartTls = true
  form.enabled = true
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (needsPassword.value && !form.mailPassword) {
    ElMessage.error('首次配置当前租户 SMTP 时必须填写授权码/密码')
    return
  }
  if (form.useSsl && form.useStartTls) {
    ElMessage.error('SSL 与 STARTTLS 不能同时启用')
    return
  }

  saving.value = true
  try {
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
    }
    const saved = await saveMailChannel(payload)
    mailChannel.value = saved
    applyConfigToForm(saved)
    ElMessage.success('邮件渠道配置已保存')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm('确认删除当前租户的邮件渠道配置？删除后可能会继承平台默认邮件通道。', '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  deleting.value = true
  try {
    await deleteMailChannel()
    await loadConfig()
    ElMessage.success('邮件渠道配置已删除')
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  } finally {
    deleting.value = false
  }
}

async function handleTestSend() {
  if (!testEmail.value) return
  testing.value = true
  testResult.value = null
  try {
    const result = await testSendMail(testEmail.value)
    testResult.value = result
    ElMessage.success('测试邮件发送成功')
  } catch (e: any) {
    testResult.value = { success: false, message: e?.message || '发送失败' }
  } finally {
    testing.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    await Promise.all([loadPresets(), loadConfig()])
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.mail-channel-form {
  max-width: 640px;
}

.panel-head-actions {
  display: flex;
  gap: 8px;
}

.panel-hint,
.field-hint {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.field-hint {
  width: 100%;
}

.test-result {
  margin-top: 8px;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 13px;
  max-width: 360px;
}

.test-result.success {
  background: #f0fdf4;
  color: #166534;
  border: 1px solid #bbf7d0;
}

.test-result.fail {
  background: #fef2f2;
  color: #991b1b;
  border: 1px solid #fecaca;
}

.error-panel {
  margin-bottom: 16px;
}
</style>