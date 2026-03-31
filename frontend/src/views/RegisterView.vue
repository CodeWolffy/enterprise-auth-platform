<template>
  <div class="auth-page auth-page--register">
    <section class="auth-card">
      <aside class="left-panel">
        <AuthCharactersScene
          mode="register"
          :focused-field="focusedField"
          :password-visible="showPassword || showConfirmPassword"
          :status="sceneStatus"
          :confirm-match="confirmMatchState"
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
            <h1>创建账号</h1>
            <p>立即加入企业认证平台</p>
            <div class="meta-tags">
              <span>{{ defaultTenantId }}</span>
              <span v-if="defaultRoleCodes.length">{{ defaultRoleCodes.join(' / ') }}</span>
            </div>
          </header>

          <form class="register-form" @submit.prevent="handleRegister">
            <div class="form-group">
              <label :class="{ 'error-label': Boolean(fieldErrors.username) }" for="register-username">用户名</label>
              <div class="input-wrapper">
                <input
                  id="register-username"
                  v-model.trim="form.username"
                  type="text"
                  placeholder="请输入用户名"
                  autocomplete="off"
                  :class="{ error: Boolean(fieldErrors.username) }"
                  @focus="focusedField = 'text'"
                  @blur="onFieldBlur"
                  @input="clearFieldError('username')"
                />
              </div>
            </div>

            <div class="form-group">
              <label :class="{ 'error-label': Boolean(fieldErrors.displayName) }" for="register-display-name">显示名称</label>
              <div class="input-wrapper">
                <input
                  id="register-display-name"
                  v-model.trim="form.displayName"
                  type="text"
                  placeholder="请输入显示名称"
                  autocomplete="off"
                  :class="{ error: Boolean(fieldErrors.displayName) }"
                  @focus="focusedField = 'text'"
                  @blur="onFieldBlur"
                  @input="clearFieldError('displayName')"
                />
              </div>
            </div>

            <div class="form-group">
              <label :class="{ 'error-label': Boolean(fieldErrors.password) }" for="register-password">密码</label>
              <div class="input-wrapper">
                <input
                  id="register-password"
                  v-model="form.password"
                  :type="showPassword ? 'text' : 'password'"
                  placeholder="••••••••"
                  autocomplete="new-password"
                  :class="{ error: Boolean(fieldErrors.password) }"
                  @focus="focusedField = 'password'"
                  @blur="onFieldBlur"
                  @input="clearFieldError('password')"
                />
                <button
                  type="button"
                  class="toggle-password"
                  :class="{ closed: showPassword, error: sceneStatus === 'error', success: sceneStatus === 'success' }"
                  aria-label="切换密码可见性"
                  @mousedown.prevent
                  @click="showPassword = !showPassword"
                >
                  <svg
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
                    <circle class="eye-pupil" cx="12" cy="12" r="3" fill="currentColor"></circle>
                  </svg>
                </button>
              </div>
            </div>

            <div class="form-group">
              <label :class="{ 'error-label': Boolean(fieldErrors.confirmPassword) }" for="register-confirm-password">确认密码</label>
              <div class="input-wrapper">
                <input
                  id="register-confirm-password"
                  v-model="confirmPassword"
                  :type="showConfirmPassword ? 'text' : 'password'"
                  placeholder="••••••••"
                  autocomplete="new-password"
                  :class="{ error: Boolean(fieldErrors.confirmPassword) }"
                  @focus="focusedField = 'password'"
                  @blur="onFieldBlur"
                  @input="clearFieldError('confirmPassword')"
                />
                <button
                  type="button"
                  class="toggle-password"
                  :class="{ closed: showConfirmPassword, error: sceneStatus === 'error', success: sceneStatus === 'success' }"
                  aria-label="切换确认密码可见性"
                  @mousedown.prevent
                  @click="showConfirmPassword = !showConfirmPassword"
                >
                  <svg
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
                    <circle class="eye-pupil" cx="12" cy="12" r="3" fill="currentColor"></circle>
                  </svg>
                </button>
              </div>
            </div>

            <div class="form-group">
              <label :class="{ 'error-label': Boolean(fieldErrors.mobile) }" for="register-mobile">手机号（选填）</label>
              <div class="input-wrapper">
                <input
                  id="register-mobile"
                  v-model.trim="form.mobile"
                  type="text"
                  inputmode="numeric"
                  placeholder="13800000000"
                  :class="{ error: Boolean(fieldErrors.mobile) }"
                  @focus="focusedField = 'text'"
                  @blur="onFieldBlur"
                  @input="clearFieldError('mobile')"
                />
              </div>
            </div>

            <div class="form-group">
              <label :class="{ 'error-label': Boolean(fieldErrors.email) }" for="register-email">邮箱（选填）</label>
              <div class="input-wrapper">
                <input
                  id="register-email"
                  v-model.trim="form.email"
                  type="email"
                  placeholder="you@example.com"
                  :class="{ error: Boolean(fieldErrors.email) }"
                  @focus="focusedField = 'text'"
                  @blur="onFieldBlur"
                  @input="clearFieldError('email')"
                />
              </div>
            </div>

            <div class="form-options">
              <label class="terms-checkbox">
                <input v-model="acceptedTerms" type="checkbox" />
                我同意
                <a href="#" @click.prevent="showComingSoon('服务条款')">服务条款</a>
              </label>
            </div>

            <div class="status-msg error" :class="{ visible: sceneStatus === 'error' && Boolean(statusMessage) }">
              {{ statusMessage }}
            </div>
            <div class="status-msg success" :class="{ visible: sceneStatus === 'success' && Boolean(statusMessage) }">
              {{ statusMessage }}
            </div>

            <button class="btn-login" :class="{ success: sceneStatus === 'success' }" type="submit" :disabled="loading">
              <span class="btn-text">{{ loading ? '创建中...' : '创建账号' }}</span>
              <span class="btn-hover-content">
                <span>{{ sceneStatus === 'success' ? '欢迎加入！' : '创建账号' }}</span>
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
          </form>

          <div class="auth-switch-link">
            已有账号？
            <a href="#" @click.prevent="router.push('/login')">去登录</a>
          </div>
        </div>
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import AuthCharactersScene from '@/components/auth/AuthCharactersScene.vue'
import { fetchRegisterOptions } from '@/api/auth'
import { http } from '@/api/http'

type FocusState = 'none' | 'text' | 'password'
type SceneStatus = 'idle' | 'error' | 'success'
type FieldErrorKey = keyof typeof fieldErrors

const router = useRouter()

const form = reactive({
  username: '',
  displayName: '',
  password: '',
  mobile: '',
  email: '',
})

const confirmPassword = ref('')
const acceptedTerms = ref(false)
const showPassword = ref(false)
const showConfirmPassword = ref(false)
const loading = ref(false)
const focusedField = ref<FocusState>('none')
const sceneStatus = ref<SceneStatus>('idle')
const statusMessage = ref('')
const defaultTenantId = ref('tenant-a')
const defaultRoleCodes = ref<string[]>([])

const fieldErrors = reactive({
  username: '',
  displayName: '',
  password: '',
  confirmPassword: '',
  mobile: '',
  email: '',
  terms: '',
})

const confirmMatchState = computed<boolean | null>(() => {
  if (!confirmPassword.value) {
    return null
  }
  return confirmPassword.value === form.password
})

function onFieldBlur() {
  focusedField.value = 'none'
}

function clearAllErrors() {
  fieldErrors.username = ''
  fieldErrors.displayName = ''
  fieldErrors.password = ''
  fieldErrors.confirmPassword = ''
  fieldErrors.mobile = ''
  fieldErrors.email = ''
  fieldErrors.terms = ''
}

function clearFieldError(field: FieldErrorKey) {
  fieldErrors[field] = ''
  if (sceneStatus.value === 'error') {
    sceneStatus.value = 'idle'
    statusMessage.value = ''
  }
}

function showComingSoon(feature: string) {
  ElMessage.info(`${feature}功能暂未开放`)
}

function setError(field: FieldErrorKey, message: string) {
  fieldErrors[field] = message
}

function validateForm() {
  clearAllErrors()
  let hasError = false

  if (!form.username.trim()) {
    setError('username', '请输入用户名')
    hasError = true
  } else if (form.username.trim().length < 3 || form.username.trim().length > 50) {
    setError('username', '用户名长度应为3-50个字符')
    hasError = true
  }

  if (!form.displayName.trim()) {
    setError('displayName', '请输入显示名称')
    hasError = true
  } else if (form.displayName.trim().length < 2 || form.displayName.trim().length > 100) {
    setError('displayName', '显示名称长度应为2-100个字符')
    hasError = true
  }

  if (!form.password) {
    setError('password', '请输入密码')
    hasError = true
  } else if (!/^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,}$/.test(form.password)) {
    setError('password', '密码至少8位，需包含字母和数字')
    hasError = true
  }

  if (!confirmPassword.value) {
    setError('confirmPassword', '请确认密码')
    hasError = true
  } else if (confirmPassword.value !== form.password) {
    setError('confirmPassword', '两次密码输入不一致')
    hasError = true
  }

  if (form.mobile && !/^1\d{10}$/.test(form.mobile)) {
    setError('mobile', '请输入正确的11位手机号')
    hasError = true
  }

  if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
    setError('email', '邮箱格式不正确')
    hasError = true
  }

  if (!acceptedTerms.value) {
    setError('terms', '请先同意服务条款')
    hasError = true
  }

  if (hasError) {
    sceneStatus.value = 'error'
    statusMessage.value = fieldErrors.terms || '请修正表单中的错误信息'
  }
  return !hasError
}

function resolveErrorMessage(error: unknown, fallback: string) {
  const maybe = error as { response?: { data?: { message?: string } } }
  return maybe?.response?.data?.message || fallback
}

async function handleRegister() {
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
    await http.post('/api/auth/register', {
      username: form.username.trim(),
      displayName: form.displayName.trim(),
      password: form.password,
      mobile: form.mobile.trim(),
      email: form.email.trim(),
    })
    sceneStatus.value = 'success'
    statusMessage.value = '账号创建成功，正在跳转登录...'
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

<style scoped>
.auth-page .right-panel {
  align-items: flex-start;
  padding-top: 30px;
}
</style>
