<template>
  <div class="auth-page auth-page--login">
    <section class="auth-card">
      <aside class="left-panel">
        <AuthCharactersScene
          mode="login"
          :focused-field="focusedField"
          :password-visible="showPassword"
          :status="sceneStatus"
        />
      </aside>

      <main class="right-panel">
        <div class="form-container">
          <div class="sparkle-icon">
            <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 2L13.5 9H10.5L12 2Z" fill="#1A1A2E" />
              <path d="M12 22L10.5 15H13.5L12 22Z" fill="#1A1A2E" />
              <path d="M2 12L9 10.5V13.5L2 12Z" fill="#1A1A2E" />
              <path d="M22 12L15 13.5V10.5L22 12Z" fill="#1A1A2E" />
            </svg>
          </div>

          <header class="form-header">
            <h1>欢迎回来！</h1>
            <p>请输入您的登录信息</p>
          </header>

          <form class="login-form" @submit.prevent="handleLogin">
            <div class="form-group">
              <label :class="{ 'error-label': Boolean(fieldErrors.username) }" for="login-username">用户名</label>
              <div class="input-wrapper">
                <input
                  id="login-username"
                  v-model.trim="username"
                  type="text"
                  placeholder="请输入用户名"
                  autocomplete="username"
                  :class="{ error: Boolean(fieldErrors.username) }"
                  @focus="focusedField = 'text'"
                  @blur="onFieldBlur"
                  @input="clearFieldError('username')"
                />
              </div>
            </div>

            <div class="form-group">
              <label :class="{ 'error-label': Boolean(fieldErrors.password) }" for="login-password">密码</label>
              <div class="input-wrapper">
                <input
                  id="login-password"
                  v-model="password"
                  :type="showPassword ? 'text' : 'password'"
                  placeholder="••••••••"
                  autocomplete="current-password"
                  :class="{ error: Boolean(fieldErrors.password) }"
                  @focus="focusedField = 'password'"
                  @blur="onFieldBlur"
                  @input="clearFieldError('password')"
                />
                <button
                  ref="toggleButtonRef"
                  type="button"
                  class="toggle-password"
                  :class="{ closed: showPassword, error: sceneStatus === 'error', success: sceneStatus === 'success' }"
                  aria-label="切换密码可见性"
                  @mousedown.prevent
                  @click="togglePasswordVisibility"
                >
                  <svg
                    id="interactive-eye"
                    width="20"
                    height="20"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2.5"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <path class="eye-upper"></path>
                    <path class="eye-lower"></path>
                    <g class="eyelashes">
                      <g style="transform: translateY(-4px)">
                        <line x1="12" y1="18.5" x2="12" y2="22.5"></line>
                        <line x1="7" y1="17" x2="4.5" y2="20"></line>
                        <line x1="17" y1="17" x2="19.5" y2="20"></line>
                      </g>
                    </g>
                    <circle
                      class="eye-pupil"
                      cx="12"
                      cy="12"
                      r="3"
                      fill="currentColor"
                      :style="{ transform: `translate(${eyePupilOffset.x}px, ${eyePupilOffset.y}px)` }"
                    ></circle>
                  </svg>
                </button>
              </div>
            </div>

            <div class="form-group">
              <label :class="{ 'error-label': Boolean(fieldErrors.captcha) }" for="login-captcha">验证码</label>
              <div class="captcha-row">
                <div class="input-wrapper">
                  <input
                    id="login-captcha"
                    v-model.trim="captchaCode"
                    type="text"
                    maxlength="5"
                    placeholder="请输入验证码"
                    autocomplete="one-time-code"
                    :class="{ error: Boolean(fieldErrors.captcha) }"
                    @focus="focusedField = 'text'"
                    @blur="onFieldBlur"
                    @input="clearFieldError('captcha')"
                  />
                </div>
                <button class="captcha-preview" type="button" title="刷新验证码" @click="reloadCaptcha">
                  <img v-if="captchaImage" :src="captchaImage" alt="验证码" />
                  <span v-else>加载中...</span>
                </button>
              </div>
            </div>

            <div class="form-options">
              <label class="remember-me">
                <input v-model="rememberMe" type="checkbox" />
                记住30天
              </label>
              <a href="#" class="forgot-link" @click.prevent="showComingSoon('忘记密码')">忘记密码？</a>
            </div>

            <div class="status-msg error" :class="{ visible: sceneStatus === 'error' && Boolean(statusMessage) }">
              {{ statusMessage }}
            </div>
            <div class="status-msg success" :class="{ visible: sceneStatus === 'success' && Boolean(statusMessage) }">
              {{ statusMessage }}
            </div>

            <button
              class="btn-login"
              :class="{ success: sceneStatus === 'success' }"
              type="submit"
              :disabled="loading"
              data-testid="login-submit"
            >
              <span class="btn-text">{{ loading ? '登录中...' : '登录' }}</span>
              <span class="btn-hover-content">
                <span>{{ sceneStatus === 'success' ? '跳转中...' : '登录' }}</span>
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2.5"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <line x1="5" y1="12" x2="19" y2="12" />
                  <polyline points="12 5 19 12 12 19" />
                </svg>
              </span>
            </button>

            <button class="btn-google" type="button" @click="showComingSoon('Google 登录')">
              <span class="btn-text">
                <svg class="google-icon" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                  <path
                    d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 01-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z"
                    fill="#4285F4"
                  />
                  <path
                    d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                    fill="#34A853"
                  />
                  <path
                    d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18A11.96 11.96 0 001 12c0 1.94.46 3.77 1.18 5.07l3.66-2.84v-.14z"
                    fill="#FBBC05"
                  />
                  <path
                    d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
                    fill="#EA4335"
                  />
                </svg>
                使用 Google 登录
              </span>
              <span class="btn-hover-content">
                <span>即将支持</span>
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2.5"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <line x1="5" y1="12" x2="19" y2="12" />
                  <polyline points="12 5 19 12 12 19" />
                </svg>
              </span>
            </button>

            <button class="register-link-button" type="button" @click="router.push('/register')">注册</button>
          </form>
        </div>
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import AuthCharactersScene from '@/components/auth/AuthCharactersScene.vue'
import { fetchCaptcha } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

type FocusState = 'none' | 'text' | 'password'
type SceneStatus = 'idle' | 'error' | 'success'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const username = ref('')
const password = ref('')
const captchaCode = ref('')
const captchaId = ref('')
const captchaImage = ref('')
const rememberMe = ref(true)
const loading = ref(false)
const showPassword = ref(false)
const focusedField = ref<FocusState>('none')
const sceneStatus = ref<SceneStatus>('idle')
const statusMessage = ref('')
const toggleButtonRef = ref<HTMLButtonElement | null>(null)
const eyePupilOffset = ref({ x: 0, y: 0 })

const fieldErrors = reactive({
  username: '',
  password: '',
  captcha: '',
})

function clearFieldError(field: keyof typeof fieldErrors) {
  fieldErrors[field] = ''
  if (sceneStatus.value === 'error') {
    sceneStatus.value = 'idle'
    statusMessage.value = ''
  }
}

function clearAllErrors() {
  fieldErrors.username = ''
  fieldErrors.password = ''
  fieldErrors.captcha = ''
}

function onFieldBlur() {
  focusedField.value = 'none'
}

function showComingSoon(feature: string) {
  ElMessage.info(`${feature}功能暂未开放`)
}

function togglePasswordVisibility() {
  showPassword.value = !showPassword.value
  focusedField.value = 'password'
  if (showPassword.value) {
    eyePupilOffset.value = { x: 0, y: 0 }
  }
}

function revokeCaptchaUrl() {
  if (captchaImage.value) {
    URL.revokeObjectURL(captchaImage.value)
    captchaImage.value = ''
  }
}

async function reloadCaptcha() {
  revokeCaptchaUrl()
  const captcha = await fetchCaptcha()
  captchaId.value = captcha.captchaId
  captchaImage.value = captcha.imageUrl
  captchaCode.value = ''
}

function resolveErrorMessage(error: unknown, fallback: string) {
  const maybe = error as { response?: { data?: { message?: string } } }
  return maybe?.response?.data?.message || fallback
}

function validateForm() {
  clearAllErrors()
  let hasError = false

  if (!username.value.trim()) {
    fieldErrors.username = '请输入用户名'
    hasError = true
  }
  if (!password.value) {
    fieldErrors.password = '请输入密码'
    hasError = true
  }
  if (!captchaCode.value.trim()) {
    fieldErrors.captcha = '请输入验证码'
    hasError = true
  }
  if (!captchaId.value) {
    fieldErrors.captcha = '验证码缺失，请刷新后重试'
    hasError = true
  }

  if (hasError) {
    sceneStatus.value = 'error'
    statusMessage.value = '请完整填写登录信息'
  }
  return !hasError
}

async function handleLogin() {
  if (loading.value) {
    return
  }
  if (!validateForm()) {
    return
  }

  loading.value = true
  sceneStatus.value = 'idle'
  statusMessage.value = ''

  try {
    await authStore.login({
      username: username.value.trim(),
      password: password.value,
      captchaId: captchaId.value,
      captchaCode: captchaCode.value.trim(),
      device: navigator.userAgent,
    })
    sceneStatus.value = 'success'
    statusMessage.value = '登录成功，正在跳转...'
    const redirect = String(route.query.redirect ?? authStore.menuItems[0]?.path ?? '/dashboard')
    window.setTimeout(async () => {
      await router.replace(redirect)
    }, 280)
  } catch (error) {
    sceneStatus.value = 'error'
    statusMessage.value = resolveErrorMessage(error, '用户名、密码或验证码错误')
    await reloadCaptcha()
  } finally {
    loading.value = false
  }
}

function updateEyePupilOffset(event: MouseEvent) {
  if (showPassword.value) {
    return
  }
  const toggleButton = toggleButtonRef.value
  if (!toggleButton) {
    return
  }
  const rect = toggleButton.getBoundingClientRect()
  const cx = rect.left + rect.width / 2
  const cy = rect.top + rect.height / 2
  const dx = event.clientX - cx
  const dy = event.clientY - cy
  const angle = Math.atan2(dy, dx)
  const dist = Math.min(Math.sqrt(dx * dx + dy * dy) / 40, 2.5)
  eyePupilOffset.value = {
    x: Math.cos(angle) * dist,
    y: Math.sin(angle) * dist,
  }
}

onMounted(async () => {
  await reloadCaptcha()
  window.addEventListener('mousemove', updateEyePupilOffset, { passive: true })
})

onBeforeUnmount(() => {
  revokeCaptchaUrl()
  window.removeEventListener('mousemove', updateEyePupilOffset)
})
</script>

<style scoped>
.auth-page .right-panel {
  padding-top: 28px;
}

.register-link-button {
  margin-top: 16px;
  width: 100%;
  border: 0;
  padding: 0;
  background: transparent;
  color: #1a1a2e;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}

.register-link-button:hover {
  color: #5b21b6;
  text-decoration: underline;
}
</style>
