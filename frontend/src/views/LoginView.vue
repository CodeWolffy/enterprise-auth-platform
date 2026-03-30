<template>
  <div class="auth-stage">
    <section class="auth-panel auth-panel--hero">
      <span class="eyebrow">Session / Redis / RBAC</span>
      <h1>企业级权限管理平台</h1>
      <p>
        当前登录链路已切换到轻量模式：账号密码登录、Redis 会话、HttpOnly Cookie、RBAC 与多租户隔离。
        浏览器不再保存 access token 或 refresh token。
      </p>
      <ul class="highlights">
        <li>认证方式：Session Cookie</li>
        <li>授权模型：RBAC + 多租户</li>
        <li>安全基线：CSRF、验证码、会话失效、强制下线</li>
      </ul>
    </section>

    <section class="auth-panel auth-panel--form">
      <span class="eyebrow">Tenant Access</span>
      <h2>登录控制台</h2>
      <p>请输入租户、用户名、密码与验证码，登录成功后会直接进入控制台。</p>

      <el-form label-position="top" @submit.prevent="handleLogin">
        <el-form-item label="租户">
          <el-input v-model="tenantId" placeholder="例如：platform" autocomplete="organization" />
        </el-form-item>

        <el-form-item label="用户名" :error="usernameError">
          <el-input v-model="username" placeholder="请输入用户名" autocomplete="username" />
        </el-form-item>

        <el-form-item label="密码" :error="passwordError">
          <el-input
            v-model="password"
            type="password"
            show-password
            placeholder="请输入密码"
            autocomplete="current-password"
          />
        </el-form-item>

        <el-form-item label="验证码" :error="captchaError">
          <div class="captcha-row">
            <el-input v-model="captchaCode" placeholder="请输入验证码" maxlength="4" />
            <el-button @click="reloadCaptcha">刷新</el-button>
          </div>
          <div class="captcha-preview">
            <span>验证码</span>
            <strong>{{ captchaPreview }}</strong>
          </div>
        </el-form-item>

        <div class="auth-actions">
          <el-button type="primary" size="large" :loading="loading" data-testid="login-submit" native-type="submit">
            登录
          </el-button>
        </div>
      </el-form>

      <div class="auth-footnote">
        登录后将由后端写入 HttpOnly 安全 Cookie，前端仅保存会话状态与权限快照。
      </div>

      <el-divider>还没有账号？<el-link type="primary" @click="$router.push('/register')">立即注册</el-link></el-divider>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { fetchCaptcha } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const tenantId = ref(String(route.query.tenantId ?? 'platform'))
const username = ref('')
const password = ref('')
const captchaId = ref('')
const captchaCode = ref('')
const captchaPreview = ref('----')
const loading = ref(false)
const usernameError = ref('')
const passwordError = ref('')
const captchaError = ref('')

async function reloadCaptcha() {
  const captcha = await fetchCaptcha()
  captchaId.value = captcha.captchaId
  captchaPreview.value = captcha.previewCode || '开发环境未开放预览'
  captchaCode.value = ''
}

function resetErrors() {
  usernameError.value = ''
  passwordError.value = ''
  captchaError.value = ''
}

async function handleLogin() {
  if (loading.value) {
    return
  }
  resetErrors()
  if (!tenantId.value.trim()) {
    usernameError.value = '请输入租户'
    return
  }
  if (!username.value.trim()) {
    usernameError.value = '请输入用户名'
    return
  }
  if (!password.value) {
    passwordError.value = '请输入密码'
    return
  }
  if (!captchaId.value || !captchaCode.value.trim()) {
    captchaError.value = '请输入验证码'
    return
  }

  loading.value = true
  try {
    await authStore.login({
      tenantId: tenantId.value.trim(),
      username: username.value.trim(),
      password: password.value,
      captchaId: captchaId.value,
      captchaCode: captchaCode.value.trim(),
      device: navigator.userAgent,
    })
    const redirect = String(route.query.redirect ?? authStore.menuItems[0]?.path ?? '/dashboard')
    await router.replace(redirect)
  } catch {
    await reloadCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await reloadCaptcha()
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

.captcha-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
}

.captcha-preview {
  margin-top: 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(53, 89, 166, 0.08);
  color: #3559a6;
}

.captcha-preview strong {
  font-size: 20px;
  letter-spacing: 0.18em;
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
}

:deep(.el-divider__text) {
  font-size: 12px;
  color: #61708a;
}

@media (max-width: 960px) {
  .auth-stage {
    grid-template-columns: 1fr;
    padding: 16px;
  }
}
</style>
