<template>
  <div class="auth-page">
    <div class="auth-card auth-card--wide">
      <div class="auth-logo">
        <span class="auth-logo__mark">EA</span>
      </div>
      <h1 class="auth-title">创建账号</h1>
      <p class="auth-subtitle">注册后即可登录平台</p>

      <form class="auth-form" @submit.prevent="handleRegister">
        <div class="auth-field">
          <input
            id="register-username"
            v-model.trim="form.username"
            type="text"
            placeholder="用户名（5-16 位）"
            autocomplete="off"
            :class="{ 'is-error': Boolean(fieldErrors.username) }"
            @input="clearFieldError('username')"
          />
          <p v-if="fieldErrors.username" class="auth-error">{{ fieldErrors.username }}</p>
        </div>

        <div class="auth-field">
          <input
            id="register-display-name"
            v-model.trim="form.displayName"
            type="text"
            placeholder="显示名称"
            autocomplete="off"
            :class="{ 'is-error': Boolean(fieldErrors.displayName) }"
            @input="clearFieldError('displayName')"
          />
          <p v-if="fieldErrors.displayName" class="auth-error">{{ fieldErrors.displayName }}</p>
        </div>

        <div class="auth-field">
          <div class="auth-password">
            <input
              id="register-password"
              v-model="form.password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="密码（至少 8 位，含字母和数字）"
              autocomplete="new-password"
              :class="{ 'is-error': Boolean(fieldErrors.password) }"
              @input="clearFieldError('password')"
            />
            <button
              class="auth-toggle-pw"
              type="button"
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

        <div class="auth-field">
          <div class="auth-password">
            <input
              id="register-confirm-password"
              v-model="confirmPassword"
              :type="showConfirmPassword ? 'text' : 'password'"
              placeholder="确认密码"
              autocomplete="new-password"
              :class="{ 'is-error': Boolean(fieldErrors.confirmPassword) }"
              @input="clearFieldError('confirmPassword')"
            />
            <button
              class="auth-toggle-pw"
              type="button"
              :aria-label="showConfirmPassword ? '隐藏确认密码' : '显示确认密码'"
              @click="showConfirmPassword = !showConfirmPassword"
            >
              <svg v-if="showConfirmPassword" viewBox="0 0 24 24" fill="none" width="18" height="18">
                <path d="M3 3l18 18M10.58 10.59A2 2 0 0013.41 13.4M9.88 5.09A10.94 10.94 0 0112 5c5 0 9.27 3.11 11 7-0.64 1.43-1.67 2.79-3 3.96M6.61 6.62C4.62 7.88 3.09 9.77 2 12c1.73 3.89 6 7 10 7 1.66 0 3.24-.34 4.68-.96" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" />
              </svg>
              <svg v-else viewBox="0 0 24 24" fill="none" width="18" height="18">
                <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z" stroke="currentColor" stroke-width="1.5" />
                <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1.5" />
              </svg>
            </button>
          </div>
          <p v-if="fieldErrors.confirmPassword" class="auth-error">{{ fieldErrors.confirmPassword }}</p>
        </div>

        <div class="auth-row">
          <input
            id="register-mobile"
            v-model.trim="form.mobile"
            type="text"
            inputmode="numeric"
            placeholder="手机号（选填）"
            :class="{ 'is-error': Boolean(fieldErrors.mobile) }"
            @input="clearFieldError('mobile')"
          />
          <input
            id="register-email"
            v-model.trim="form.email"
            type="email"
            placeholder="邮箱（选填）"
            :class="{ 'is-error': Boolean(fieldErrors.email) }"
            @input="clearFieldError('email')"
          />
        </div>
        <p v-if="fieldErrors.mobile" class="auth-error">{{ fieldErrors.mobile }}</p>
        <p v-if="fieldErrors.email" class="auth-error">{{ fieldErrors.email }}</p>

        <label class="auth-check">
          <input v-model="acceptedTerms" type="checkbox" @change="clearFieldError('terms')" />
          <span>我已阅读并同意平台服务条款与注册规则</span>
        </label>
        <p v-if="fieldErrors.terms" class="auth-error">{{ fieldErrors.terms }}</p>

        <div class="auth-captcha-row" :class="{ 'is-verified': captchaVerified }">
          <span>{{ captchaVerified ? '验证通过' : '需完成滑块验证' }}</span>
          <button class="auth-link" type="button" @click="openCaptchaDialog({ refresh: true })">
            {{ captchaVerified ? '重新验证' : '去验证' }}
          </button>
        </div>
        <p v-if="fieldErrors.captcha" class="auth-error">{{ fieldErrors.captcha }}</p>

        <div v-if="statusMessage" class="auth-status" :class="sceneStatus">
          {{ statusMessage }}
        </div>

        <button class="auth-submit" type="submit" :disabled="loading">
          {{ loading ? '创建中...' : '创建账号' }}
        </button>
      </form>

      <p class="auth-switch">
        已有账号？<button class="auth-link" type="button" @click="router.push('/login')">去登录</button>
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
import { useRouter } from 'vue-router'
import SliderCaptcha from '@/components/auth/SliderCaptcha.vue'
import { fetchCaptcha, fetchRegisterOptions, verifyCaptcha } from '@/api/modules'
import { http } from '@/api/http'
import type { CaptchaTrackPayload } from '@/types/auth-models'

type SceneStatus = 'idle' | 'error' | 'success'
type RegisterField = 'username' | 'displayName' | 'password' | 'confirmPassword' | 'mobile' | 'email' | 'terms'

const router = useRouter()

const form = reactive({
  username: '',
  displayName: '',
  password: '',
  mobile: '',
  email: '',
})

const fieldErrors = reactive<Record<RegisterField, string>>({
  username: '',
  displayName: '',
  password: '',
  confirmPassword: '',
  mobile: '',
  email: '',
  terms: '',
})

const confirmPassword = ref('')
const acceptedTerms = ref(false)
const showPassword = ref(false)
const showConfirmPassword = ref(false)
const loading = ref(false)
const sceneStatus = ref<SceneStatus>('idle')
const statusMessage = ref('')
const defaultTenantId = ref('tenant-a')
const defaultRoleCodes = ref<string[]>([])

// 滑块验证码状态
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
const captchaVerifying = ref(false)

function clearAllErrors() {
  fieldErrors.username = ''
  fieldErrors.displayName = ''
  fieldErrors.password = ''
  fieldErrors.confirmPassword = ''
  fieldErrors.mobile = ''
  fieldErrors.email = ''
  fieldErrors.terms = ''
}

function clearFieldError(field: RegisterField) {
  fieldErrors[field] = ''
  if (sceneStatus.value === 'error') {
    sceneStatus.value = 'idle'
    statusMessage.value = ''
  }
}

function setError(field: RegisterField, message: string) {
  fieldErrors[field] = message
}

function validateForm() {
  clearAllErrors()
  let hasError = false

  if (!form.username.trim()) {
    setError('username', '请输入用户名')
    hasError = true
  } else if (form.username.trim().length < 5 || form.username.trim().length > 16) {
    setError('username', '用户名长度需为 5-16 位')
    hasError = true
  }

  if (!form.displayName.trim()) {
    setError('displayName', '请输入显示名称')
    hasError = true
  } else if (form.displayName.trim().length < 2 || form.displayName.trim().length > 100) {
    setError('displayName', '显示名称长度需为 2-100 位')
    hasError = true
  }

  if (!form.password) {
    setError('password', '请输入密码')
    hasError = true
  } else if (!/^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,}$/.test(form.password)) {
    setError('password', '密码至少 8 位，且需包含字母和数字')
    hasError = true
  }

  if (!confirmPassword.value) {
    setError('confirmPassword', '请再次输入密码')
    hasError = true
  } else if (confirmPassword.value !== form.password) {
    setError('confirmPassword', '两次输入的密码不一致')
    hasError = true
  }

  if (form.mobile && !/^1\d{10}$/.test(form.mobile)) {
    setError('mobile', '请输入正确的 11 位手机号')
    hasError = true
  }

  if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
    setError('email', '请输入正确的邮箱地址')
    hasError = true
  }

  if (!acceptedTerms.value) {
    setError('terms', '请先同意服务条款与注册规则')
    hasError = true
  }

  if (hasError) {
    sceneStatus.value = 'error'
    statusMessage.value = fieldErrors.terms || '请先修正表单中的错误信息。'
  }

  return !hasError
}

function resolveErrorMessage(error: unknown, fallback: string) {
  const maybe = error as { response?: { data?: { message?: string } } }
  return maybe?.response?.data?.message || fallback
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
  if (options.refresh || !captchaBackground.value || !captchaSlider.value || !captchaId.value) {
    await reloadCaptcha()
  }
  captchaDialogVisible.value = true
}

function handleCaptchaDialogClosed() {
  // 关闭弹窗不做额外操作
}

async function handleSliderVerify(track: CaptchaTrackPayload) {
  const code = JSON.stringify(track)
  captchaVerifying.value = true
  try {
    await verifyCaptcha(captchaId.value, code)
    captchaCode.value = code
    captchaVerified.value = true
    captchaDialogVisible.value = false
  } catch {
    captchaVerified.value = false
    captchaCode.value = ''
    await reloadCaptcha()
  } finally {
    captchaVerifying.value = false
  }
}

async function handleRegister() {
  if (loading.value) {
    return
  }

  if (!validateForm()) {
    return
  }

  if (!captchaVerified.value) {
    fieldErrors['terms'] = '' // 清除条款错误干扰
    sceneStatus.value = 'error'
    statusMessage.value = '请先完成滑块验证'
    await openCaptchaDialog({ refresh: true })
    return
  }

  loading.value = true
  sceneStatus.value = 'idle'
  statusMessage.value = ''

  try {
    await http.post('/api/auth/register', {
      username: form.username.trim(),
      displayName: form.displayName.trim(),
      password: form.password,
      mobile: form.mobile.trim(),
      email: form.email.trim(),
      captchaId: captchaId.value,
    })
    sceneStatus.value = 'success'
    statusMessage.value = '账号创建成功，正在跳转登录页...'
    window.setTimeout(async () => {
      await router.push('/login')
    }, 380)
  } catch (error) {
    sceneStatus.value = 'error'
    statusMessage.value = resolveErrorMessage(error, '注册失败，请稍后重试')
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
    // Keep defaults when options endpoint is unavailable.
  }
})
</script>
