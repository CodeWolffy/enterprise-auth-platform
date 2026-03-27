<template>
  <div class="auth-stage">
    <section class="auth-panel auth-panel--hero">
      <span class="eyebrow">Registration</span>
      <h1>用户注册</h1>
      <p>创建一个新账号以访问企业级权限管理平台。</p>
      <ul class="highlights">
        <li>默认分配到 {{ defaultTenantId }} 租户</li>
        <li v-if="defaultRoleCodes.length">注册后默认角色：{{ defaultRoleCodes.join(' / ') }}</li>
        <li v-else>注册后需管理员分配角色权限</li>
        <li>密码至少8位，包含字母和数字</li>
        <li>注册后可登录系统</li>
      </ul>
    </section>

    <section class="auth-panel auth-panel--form">
      <span class="eyebrow">Create Account</span>
      <h2>填写注册信息</h2>
      <p>请填写以下信息完成注册。</p>

      <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" class="auth-alert" />

      <el-form ref="formRef" label-position="top" :rules="rules" :model="form" @submit.prevent="handleRegister">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名（3-50字符）" />
        </el-form-item>

        <el-form-item label="显示名称" prop="displayName">
          <el-input v-model="form.displayName" placeholder="请输入显示名称（2-100字符）" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码（至少8位，包含字母和数字）"
          />
        </el-form-item>

        <el-form-item label="手机号" prop="mobile">
          <el-input v-model="form.mobile" placeholder="请输入手机号（可选）" />
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱地址（可选）" />
        </el-form-item>

        <div class="auth-actions">
          <el-button type="primary" size="large" :loading="loading" native-type="submit">立即注册</el-button>
        </div>
      </el-form>

      <div class="auth-footnote">
        已有账号？<el-link type="primary" @click="$router.push('/login')">立即登录</el-link>
      </div>
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
  if (!formRef.value) return

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
    router.push('/login')
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
    // Keep fallback copy when register options endpoint is temporarily unavailable.
  }
})
</script>

<style scoped>
.auth-stage {
  min-height: calc(100vh - 120px);
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: 20px;
  padding: 28px;
}

.auth-panel {
  border-radius: 24px;
  background: linear-gradient(175deg, rgba(255, 255, 255, 0.94) 0%, rgba(244, 248, 255, 0.92) 100%);
  border: 1px solid rgba(27, 42, 68, 0.08);
  box-shadow: 0 24px 64px rgba(20, 32, 52, 0.12);
  padding: 28px;
}

.auth-panel--hero {
  display: grid;
  gap: 16px;
}

.eyebrow {
  letter-spacing: 0.12em;
  text-transform: uppercase;
  font-size: 12px;
  color: #3559a6;
  font-weight: 700;
}

.highlights {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 8px;
}

.highlights li {
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(53, 89, 166, 0.08);
}

.auth-panel--form {
  display: grid;
  align-content: start;
  gap: 10px;
}

.auth-alert {
  margin-bottom: 4px;
}

.auth-actions :deep(.el-button) {
  width: 100%;
  border-radius: 12px;
  min-height: 44px;
  font-weight: 700;
}

.auth-footnote {
  color: #61708a;
  font-size: 12px;
  line-height: 1.6;
  text-align: center;
}

@media (max-width: 960px) {
  .auth-stage {
    grid-template-columns: 1fr;
    padding: 16px;
  }
}
</style>
