<template>
  <div class="login-shell">
    <section class="login-card">
      <div class="brand">
        <span class="brand-mark" aria-hidden="true"></span>
        <span class="brand-text">Enterprise Auth</span>
      </div>

      <header class="login-header">
        <h1>登录</h1>
      </header>

      <el-form class="login-form" @submit.prevent="handleLogin">
        <el-form-item :error="usernameError">
          <el-input v-model="username" size="large" placeholder="用户名" autocomplete="username" />
        </el-form-item>

        <el-form-item :error="passwordError">
          <el-input
            v-model="password"
            size="large"
            type="password"
            show-password
            placeholder="密码"
            autocomplete="current-password"
          />
        </el-form-item>

        <el-form-item :error="captchaError">
          <div class="captcha-field">
            <el-input
              v-model="captchaCode"
              size="large"
              maxlength="5"
              autocomplete="one-time-code"
              placeholder="验证码"
              spellcheck="false"
            />
            <button class="captcha-image" type="button" title="刷新验证码" @click="reloadCaptcha">
              <img v-if="captchaImage" :src="captchaImage" alt="验证码" />
              <span v-else>加载中</span>
            </button>
          </div>
        </el-form-item>

        <el-button
          class="submit-button"
          type="primary"
          size="large"
          :loading="loading"
          data-testid="login-submit"
          native-type="submit"
        >
          进入系统
        </el-button>
      </el-form>

      <button class="register-link" type="button" @click="router.push('/register')">注册</button>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchCaptcha } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const username = ref('')
const password = ref('')
const captchaId = ref('')
const captchaCode = ref('')
const captchaImage = ref('')
const loading = ref(false)
const usernameError = ref('')
const passwordError = ref('')
const captchaError = ref('')

async function reloadCaptcha() {
  if (captchaImage.value) {
    URL.revokeObjectURL(captchaImage.value)
  }
  const captcha = await fetchCaptcha()
  captchaId.value = captcha.captchaId
  captchaImage.value = captcha.imageUrl
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

onBeforeUnmount(() => {
  if (captchaImage.value) {
    URL.revokeObjectURL(captchaImage.value)
  }
})
</script>

<style scoped>
.login-shell {
  --surface: rgba(255, 255, 255, 0.82);
  --surface-border: rgba(15, 23, 42, 0.08);
  --surface-shadow: 0 28px 60px rgba(15, 23, 42, 0.16);
  --text-primary: #111827;
  --text-muted: #6b7280;
  --accent: #0f172a;
  min-height: calc(100vh - 120px);
  position: relative;
  display: grid;
  place-items: center;
  padding: 24px;
  overflow: hidden;
}

.login-shell::before,
.login-shell::after {
  content: '';
  position: absolute;
  inset: auto;
  border-radius: 999px;
  filter: blur(10px);
}

.login-shell::before {
  width: 320px;
  height: 320px;
  top: 8%;
  left: calc(50% - 280px);
  background: radial-gradient(circle, rgba(180, 206, 255, 0.72), rgba(180, 206, 255, 0));
}

.login-shell::after {
  width: 280px;
  height: 280px;
  right: calc(50% - 260px);
  bottom: 6%;
  background: radial-gradient(circle, rgba(221, 214, 254, 0.56), rgba(221, 214, 254, 0));
}

.login-card {
  position: relative;
  width: min(100%, 420px);
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

.login-header {
  margin: 26px 0 18px;
}

.login-header h1 {
  margin: 0;
  font-size: 34px;
  line-height: 1;
  color: var(--text-primary);
  letter-spacing: -0.04em;
}

.login-form {
  display: grid;
  gap: 8px;
}

.captcha-field {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 124px;
  gap: 12px;
  align-items: stretch;
}

.captcha-image {
  display: grid;
  place-items: center;
  width: 124px;
  min-height: 48px;
  padding: 0;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 16px;
  background: rgba(248, 250, 252, 0.96);
  cursor: pointer;
  overflow: hidden;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    border-color 0.18s ease;
}

.captcha-image:hover {
  transform: translateY(-1px);
  border-color: rgba(15, 23, 42, 0.14);
  box-shadow: 0 14px 26px rgba(15, 23, 42, 0.1);
}

.captcha-image img {
  display: block;
  width: 100%;
  height: 48px;
  object-fit: cover;
}

.captcha-image span {
  font-size: 12px;
  color: var(--text-muted);
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

.register-link {
  margin-top: 16px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-muted);
  font-size: 13px;
  cursor: pointer;
  transition: color 0.18s ease;
}

.register-link:hover {
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
  .login-shell {
    min-height: calc(100vh - 96px);
    padding: 16px;
  }

  .login-card {
    padding: 24px;
    border-radius: 24px;
  }

  .login-header {
    margin-top: 22px;
  }

  .login-header h1 {
    font-size: 30px;
  }

  .captcha-field {
    grid-template-columns: 1fr;
  }

  .captcha-image {
    width: 100%;
  }
}
</style>
