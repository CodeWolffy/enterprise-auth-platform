<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-logo">
        <span class="auth-logo__mark">EA</span>
      </div>
      <h1 class="auth-title">欢迎回来</h1>
      <p class="auth-subtitle">登录以继续</p>

      <form class="auth-form" @submit.prevent="handleLogin">
        <div class="auth-field">
          <input
            id="login-username"
            v-model.trim="username"
            type="text"
            placeholder="用户名"
            autocomplete="username"
            data-testid="login-username"
            :class="{ 'is-error': Boolean(fieldErrors.username) }"
            @input="clearFieldError('username')"
          />
          <p v-if="fieldErrors.username" class="auth-error">{{ fieldErrors.username }}</p>
        </div>

        <div class="auth-field">
          <div class="auth-password">
            <input
              id="login-password"
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="密码"
              autocomplete="current-password"
              data-testid="login-password"
              :class="{ 'is-error': Boolean(fieldErrors.password) }"
              @input="clearFieldError('password')"
            />
            <button
              class="auth-toggle-pw"
              type="button"
              data-testid="login-toggle-password"
              :aria-label="showPassword ? '隐藏密码' : '显示密码'"
              @click="showPassword = !showPassword"
            >
              <svg v-if="showPassword" viewBox="0 0 24 24" fill="none" width="18" height="18">
                <path d="M3 3l18 18M10.58 10.59A2 2 0 0013.41 13.4M9.88 5.09A10.94 10.94 0 0112 5c5 0 9.27 3.11 11 7-0.64 1.43-1.67 2.79-3 3.96M6.61 6.62C4.62 7.88 3.09 9.77 2 12c1.73 3.89 6 7 10 7 1.66 0 3.24-.34 4.68-.96" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" />
              </svg>
              <svg v-else viewBox="0 0 24 24" fill="none" width="18" height="18">
                <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z" stroke="currentColor" stroke-width="1.5" />
                <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1.5" />
              </svg>
            </button>
          </div>
          <p v-if="fieldErrors.password" class="auth-error">{{ fieldErrors.password }}</p>
        </div>

        <div class="auth-captcha-row" :class="{ 'is-verified': captchaVerified }">
          <span>{{ captchaVerified ? '验证通过' : '需完成滑块验证' }}</span>
          <button class="auth-link" type="button" data-testid="login-open-captcha" @click="openCaptchaDialog({ refresh: true })">
            {{ captchaVerified ? '重新验证' : '去验证' }}
          </button>
        </div>
        <p v-if="fieldErrors.captcha" class="auth-error">{{ fieldErrors.captcha }}</p>

        <div v-if="statusMessage" class="auth-status" :class="sceneStatus" data-testid="login-status">
          {{ statusMessage }}
        </div>

        <button class="auth-submit" type="submit" :disabled="loading" data-testid="login-submit">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>

      <p class="auth-switch">
        <button class="auth-link" type="button" @click="router.push('/reset-password')">忘记密码？</button>
        <span class="auth-switch__divider">·</span>
        还没有账号？<button class="auth-link" type="button" @click="router.push('/register')">注册</button>
      </p>
    </div>

    <el-dialog
      v-model="captchaDialogVisible"
      class="captcha-verify-dialog"
      title="安全验证"
      width="360px"
      :close-on-click-modal="false"
      :destroy-on-close="false"
      :show-close="true"
      @closed="handleCaptchaDialogClosed"
    >
<SliderCaptcha
      v-if="!captchaLoading && captchaBackground && captchaSlider"
      :background-image="captchaBackground"
      :slider-image="captchaSlider"
      :background-width="captchaBackgroundWidth"
      :background-height="captchaBackgroundHeight"
      :slider-width="captchaSliderWidth"
      :slider-height="captchaSliderHeight"
      :verifying="captchaVerifying"
      @verify="handleSliderVerify"
      @refresh="reloadCaptcha"
    />
      <div v-else class="captcha-dialog__loading">
        <div class="captcha-dialog__spinner"></div>
        <span>验证码加载中</span>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SliderCaptcha from '@/components/auth/SliderCaptcha.vue'
import { fetchCaptcha, verifyCaptcha } from '@/api/modules'
import { useAuthStore } from '@/stores/auth'
import type { CaptchaTrackPayload } from '@/types/auth-models'

type SceneStatus = 'idle' | 'error' | 'success'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const username = ref('')
const password = ref('')
const showPassword = ref(false)
const loading = ref(false)
const sceneStatus = ref<SceneStatus>('idle')
const statusMessage = ref('')

const captchaCode = ref('')
const captchaId = ref('')
const captchaBackground = ref('')
const captchaSlider = ref('')
const captchaBackgroundWidth = ref(0)
const captchaBackgroundHeight = ref(0)
const captchaSliderWidth = ref(0)
const captchaSliderHeight = ref(0)
const captchaVerified = ref(false)
const captchaDialogVisible = ref(false)
const captchaLoading = ref(false)
const pendingSubmitAfterCaptcha = ref(false)

const fieldErrors = reactive({
  username: '',
  password: '',
  captcha: '',
})

onMounted(() => {
  const reason = String(route.query.authReason ?? '')
  if (!reason) {
    return
  }
  sceneStatus.value = 'error'
  statusMessage.value = authReasonMessage(reason)
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

function resetCaptchaVerification() {
  captchaVerified.value = false
  captchaCode.value = ''
  captchaId.value = ''
}

function toImageDataUrl(image: string, defaultMime: string) {
  if (!image) {
    return ''
  }
  return image.startsWith('data:') ? image : `data:${defaultMime};base64,${image}`
}

async function reloadCaptcha() {
  captchaLoading.value = true
  try {
    const captcha = await fetchCaptcha()
    captchaId.value = captcha.captchaId
    captchaBackground.value = toImageDataUrl(captcha.backgroundImage, 'image/jpeg')
    captchaSlider.value = toImageDataUrl(captcha.sliderImage, 'image/png')
    captchaBackgroundWidth.value = captcha.backgroundImageWidth || 0
    captchaBackgroundHeight.value = captcha.backgroundImageHeight || 0
    captchaSliderWidth.value = captcha.sliderImageWidth || 0
    captchaSliderHeight.value = captcha.sliderImageHeight || 0
    captchaVerified.value = false
    captchaCode.value = ''
  } finally {
    captchaLoading.value = false
  }
}

async function openCaptchaDialog(options: { refresh?: boolean } = {}) {
  fieldErrors.captcha = ''
  if (options.refresh || !captchaBackground.value || !captchaSlider.value || !captchaId.value) {
    await reloadCaptcha()
  }
  captchaDialogVisible.value = true
}

function handleCaptchaDialogClosed() {
  if (!captchaVerified.value) {
    pendingSubmitAfterCaptcha.value = false
  }
}

const captchaVerifying = ref(false)

async function handleSliderVerify(track: CaptchaTrackPayload) {
  const code = JSON.stringify(track)
  captchaVerifying.value = true
  try {
    await verifyCaptcha(captchaId.value, code)
    captchaCode.value = code
    captchaVerified.value = true
    captchaDialogVisible.value = false

    if (pendingSubmitAfterCaptcha.value) {
      pendingSubmitAfterCaptcha.value = false
      await submitLogin()
    }
  } catch {
    captchaVerified.value = false
    captchaCode.value = ''
    await reloadCaptcha()
  } finally {
    captchaVerifying.value = false
  }
}

function resolveErrorMessage(error: unknown, fallback: string) {
  const maybe = error as { response?: { data?: { message?: string } } }
  return maybe?.response?.data?.message || fallback
}

function authReasonMessage(reason: string) {
  if (reason === 'SESSION_OFFLINE') {
    return '当前账号已被强制下线，请重新登录'
  }
  if (reason === 'SESSION_EXPIRED') {
    return '登录已过期，请重新登录'
  }
  if (reason === 'INVALID_TOKEN') {
    return '登录凭证已失效，请重新登录'
  }
  return '请先登录后继续访问'
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

  if (hasError) {
    sceneStatus.value = 'error'
    statusMessage.value = '请先完善登录信息。'
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

  if (!captchaVerified.value) {
    pendingSubmitAfterCaptcha.value = true
    fieldErrors.captcha = '请先完成滑块验证'
    await openCaptchaDialog({ refresh: true })
    return
  }

  await submitLogin()
}

async function submitLogin() {
  if (loading.value) {
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
    statusMessage.value = authStore.passwordChangeRequired ? '登录成功，请先修改密码...' : '登录成功，正在进入控制台...'
    const redirect = authStore.passwordChangeRequired
      ? '/account/profile'
      : String(route.query.redirect ?? authStore.menuItems[0]?.path ?? '/dashboard')
    window.setTimeout(async () => {
      await router.replace(redirect)
    }, 280)
  } catch (error) {
    resetCaptchaVerification()
    sceneStatus.value = 'error'
    statusMessage.value = resolveErrorMessage(error, '用户名、密码或验证码错误')
  } finally {
    loading.value = false
  }
}
</script>
