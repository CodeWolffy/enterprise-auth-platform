<template>
  <div class="auth-stage">
    <section class="auth-panel auth-panel--hero">
      <span class="eyebrow">Frontend / Authorization Code + PKCE</span>
      <h1>企业级权限管理平台</h1>
      <p>
        当前前端已经直接联调 Spring Authorization Server。登录完成后会进入统一控制台，可继续管理
        OAuth2 客户端、用户、角色、权限、部门、租户、审计与系统配置。
      </p>
      <ul class="highlights">
        <li>公共客户端：eap-frontend-spa</li>
        <li>授权方式：Authorization Code + PKCE</li>
        <li>支持租户登录、中文登录页与中文同意页</li>
      </ul>
    </section>

    <section class="auth-panel auth-panel--form">
      <span class="eyebrow">Tenant Access</span>
      <h2>{{ oauthContextReady ? '登录并继续授权' : '开始 OAuth2 登录' }}</h2>
      <p>{{ oauthContextReady ? '请输入账号密码，系统将自动匹配所属租户并继续授权流程。' : '点击后将跳转到后端统一认证中心继续完成授权。' }}</p>

      <el-alert
        v-if="loginErrorMessage"
        :title="loginErrorMessage"
        type="warning"
        show-icon
        :closable="false"
        class="auth-alert"
      />

      <el-form label-position="top" @submit.prevent="handleLogin">
        <el-form-item v-if="oauthContextReady" label="用户名" :error="usernameError">
          <el-input v-model="username" placeholder="请输入用户名" autocomplete="username" />
        </el-form-item>

        <el-form-item v-if="oauthContextReady" label="密码" :error="passwordError">
          <el-input
            v-model="password"
            type="password"
            show-password
            placeholder="请输入密码"
            autocomplete="current-password"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <div class="auth-actions">
          <el-button type="primary" size="large" :loading="loading" data-testid="login-submit" native-type="submit">
            {{ oauthContextReady ? '登录并继续授权' : '跳转统一认证中心' }}
          </el-button>
        </div>
      </el-form>

      <div class="auth-footnote">
        登录后将由后端写入 HttpOnly 安全 Cookie，前端不落地敏感令牌。
      </div>

      <el-divider>还没有账号？<el-link type="primary" @click="$router.push('/register')">立即注册</el-link></el-divider>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { getBackendOrigin } from '@/utils/oauth'

const authStore = useAuthStore()
const route = useRoute()
const username = ref('')
const password = ref('')
const loading = ref(false)
const usernameError = ref('')
const passwordError = ref('')

const backendOrigin = getBackendOrigin()

const loginErrorMessage = computed(() => {
  const code = String(route.query.error ?? '').trim().toLowerCase()
  if (!code) {
    return ''
  }
  if (code === 'locked') {
    return '账户已锁定，请联系管理员或稍后重试。'
  }
  if (code === 'bad_credentials') {
    return '用户名或密码错误，请重新登录。'
  }
  return `登录失败：${code}`
})

const oauthContextReady = computed(() => {
  const hasResponseType = String(route.query.response_type ?? '').toLowerCase() === 'code'
  const hasClientId = String(route.query.client_id ?? '').trim().length > 0
  const hasState = String(route.query.state ?? '').trim().length > 0
  return hasResponseType && hasClientId && hasState
})

function resetCredentialErrors() {
  usernameError.value = ''
  passwordError.value = ''
}

async function handleLogin() {
  resetCredentialErrors()
  loading.value = true
  try {
    if (oauthContextReady.value) {
      if (!username.value.trim()) {
        usernameError.value = '请输入用户名'
        return
      }
      if (!password.value) {
        passwordError.value = '请输入密码'
        return
      }
      submitBackendLoginForm()
      return
    }

    await authStore.startLogin()
  } finally {
    loading.value = false
  }
}

function submitBackendLoginForm() {
  const form = document.createElement('form')
  form.method = 'post'
  form.action = `${backendOrigin}/login`

  appendHidden(form, 'username', username.value.trim())
  appendHidden(form, 'password', password.value)

  const clientId = String(route.query.client_id ?? '').trim()
  if (clientId) {
    appendHidden(form, 'client_id', clientId)
  }

  document.body.appendChild(form)
  form.submit()
}

function appendHidden(form: HTMLFormElement, name: string, value: string) {
  const input = document.createElement('input')
  input.type = 'hidden'
  input.name = name
  input.value = value
  form.appendChild(input)
}
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
