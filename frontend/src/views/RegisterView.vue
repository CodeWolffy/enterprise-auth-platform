<template>
  <div class="register-shell">
    <section class="register-card">
      <div class="brand">
        <span class="brand-mark" aria-hidden="true"></span>
        <span class="brand-text">Enterprise Auth</span>
      </div>

      <header class="register-header">
        <h1>注册</h1>
        <p class="register-meta">
          <span>{{ defaultTenantId }}</span>
          <span v-if="defaultRoleCodes.length">{{ defaultRoleCodes.join(' / ') }}</span>
        </p>
      </header>

      <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" class="register-alert" />

      <el-form ref="formRef" class="register-form" :rules="rules" :model="form" @submit.prevent="handleRegister">
        <el-form-item prop="username">
          <el-input v-model="form.username" size="large" placeholder="用户名" spellcheck="false" />
        </el-form-item>

        <el-form-item prop="displayName">
          <el-input v-model="form.displayName" size="large" placeholder="显示名称" spellcheck="false" />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            size="large"
            type="password"
            show-password
            placeholder="密码"
            autocomplete="new-password"
          />
        </el-form-item>

        <el-form-item prop="mobile">
          <el-input v-model="form.mobile" size="large" placeholder="手机号（选填）" />
        </el-form-item>

        <el-form-item prop="email">
          <el-input v-model="form.email" size="large" placeholder="邮箱（选填）" spellcheck="false" />
        </el-form-item>

        <el-button class="submit-button" type="primary" size="large" :loading="loading" native-type="submit">
          创建账号
        </el-button>
      </el-form>

      <button class="login-link" type="button" @click="router.push('/login')">返回登录</button>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { fetchRegisterOptions } from '@/api/auth'
import { http } from '@/api/http'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const errorMessage = ref('')
const defaultTenantId = ref('tenant-a')
const defaultRoleCodes = ref<string[]>([])

const form = reactive({
  username: '',
  displayName: '',
  password: '',
  mobile: '',
  email: '',
})

const rules = reactive<FormRules>({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度为 3-50 字符', trigger: 'blur' },
  ],
  displayName: [
    { required: true, message: '请输入显示名称', trigger: 'blur' },
    { min: 2, max: 100, message: '显示名称长度为 2-100 字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    {
      pattern: /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,}$/,
      message: '密码至少8位，包含字母和数字',
      trigger: 'blur',
    },
  ],
  mobile: [
    {
      pattern: /^1\d{10}$/,
      message: '请输入有效的 11 位手机号',
      trigger: ['blur', 'change'],
    },
  ],
  email: [{ type: 'email', message: '请输入有效的邮箱地址', trigger: ['blur', 'change'] }],
})

async function handleRegister() {
  if (!formRef.value) {
    return
  }

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    await http.post('/api/auth/register', form)
    ElMessage.success('注册成功，请登录')
    await router.push('/login')
  } catch (err) {
    const message = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
    errorMessage.value = message || '注册失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    const options = await fetchRegisterOptions()
    if (options?.defaultTenantId) {
      defaultTenantId.value = options.defaultTenantId
    }
    defaultRoleCodes.value = Array.isArray(options?.defaultRoleCodes) ? options.defaultRoleCodes : []
  } catch {
    // Keep defaults when remote options are temporarily unavailable.
  }
})
</script>

<style scoped>
.register-shell {
  --surface: rgba(255, 255, 255, 0.82);
  --surface-border: rgba(15, 23, 42, 0.08);
  --surface-shadow: 0 28px 60px rgba(15, 23, 42, 0.16);
  --text-primary: #111827;
  --text-muted: #6b7280;
  min-height: calc(100vh - 120px);
  position: relative;
  display: grid;
  place-items: center;
  padding: 24px;
  overflow: hidden;
}

.register-shell::before,
.register-shell::after {
  content: '';
  position: absolute;
  border-radius: 999px;
  filter: blur(12px);
}

.register-shell::before {
  width: 360px;
  height: 360px;
  top: 4%;
  left: calc(50% - 310px);
  background: radial-gradient(circle, rgba(194, 233, 251, 0.72), rgba(194, 233, 251, 0));
}

.register-shell::after {
  width: 300px;
  height: 300px;
  right: calc(50% - 280px);
  bottom: 4%;
  background: radial-gradient(circle, rgba(253, 230, 138, 0.4), rgba(253, 230, 138, 0));
}

.register-card {
  position: relative;
  width: min(100%, 460px);
  padding: 32px;
  border-radius: 28px;
  background: var(--surface);
  border: 1px solid var(--surface-border);
  box-shadow: var(--surface-shadow);
  backdrop-filter: blur(18px);
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--text-primary);
}

.brand-mark {
  width: 12px;
  height: 12px;
  border-radius: 999px;
  background: linear-gradient(135deg, #111827, #64748b);
  box-shadow: 0 0 0 6px rgba(15, 23, 42, 0.08);
}

.brand-text {
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.register-header {
  margin: 26px 0 18px;
}

.register-header h1 {
  margin: 0;
  font-size: 34px;
  line-height: 1;
  color: var(--text-primary);
  letter-spacing: -0.04em;
}

.register-meta {
  margin: 10px 0 0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.register-meta span {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(15, 23, 42, 0.08);
  color: var(--text-muted);
  font-size: 12px;
}

.register-alert {
  margin-bottom: 12px;
  border-radius: 16px;
}

.register-form {
  display: grid;
  gap: 8px;
}

.submit-button {
  margin-top: 6px;
  min-height: 50px;
  border: 0;
  border-radius: 16px;
  background: linear-gradient(135deg, #111827 0%, #1f2937 100%);
  font-weight: 700;
  letter-spacing: 0.02em;
}

.login-link {
  margin-top: 16px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-muted);
  font-size: 13px;
  cursor: pointer;
  transition: color 0.18s ease;
}

.login-link:hover {
  color: var(--text-primary);
}

:deep(.el-form-item) {
  margin-bottom: 0;
}

:deep(.el-input__wrapper) {
  min-height: 50px;
  padding: 0 16px;
  border-radius: 16px;
  box-shadow: none;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(15, 23, 42, 0.08);
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
}

:deep(.el-input__wrapper.is-focus) {
  border-color: rgba(15, 23, 42, 0.2);
  box-shadow: 0 10px 20px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
}

:deep(.el-input__inner) {
  color: var(--text-primary);
  font-size: 14px;
}

:deep(.el-input__inner::placeholder) {
  color: #9ca3af;
}

:deep(.el-form-item__error) {
  padding-top: 6px;
}

@media (max-width: 640px) {
  .register-shell {
    min-height: calc(100vh - 96px);
    padding: 16px;
  }

  .register-card {
    padding: 24px;
    border-radius: 24px;
  }

  .register-header {
    margin-top: 22px;
  }

  .register-header h1 {
    font-size: 30px;
  }
}
</style>
