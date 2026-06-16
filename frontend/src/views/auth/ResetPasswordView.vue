<template>
  <div class="auth-page reset-password-page">
    <div class="auth-card reset-card">
      <div class="auth-logo">
        <span class="auth-logo__mark">EA</span>
      </div>
      <h1 class="auth-title">重置密码</h1>
      <p class="auth-subtitle">
        {{ token ? '请设置新密码，完成后使用新密码登录。' : '输入用户名和绑定邮箱后，如果信息匹配，将收到重置链接。' }}
      </p>

      <form v-if="!token" class="auth-form" @submit.prevent="submitRequest">
        <div class="auth-field">
          <input v-model.trim="username" type="text" placeholder="用户名（5-16 位）" autocomplete="username" />
        </div>
        <div class="auth-field">
          <input v-model.trim="email" type="email" placeholder="绑定邮箱" autocomplete="email" />
          <p v-if="fieldError" class="auth-error">{{ fieldError }}</p>
        </div>

        <div class="auth-captcha-row" :class="{ 'is-verified': captchaVerified }">
          <span>{{ captchaVerified ? '验证通过' : '需完成滑块验证' }}</span>
          <button class="auth-link" type="button" @click="openCaptchaDialog({ refresh: true })">
            {{ captchaVerified ? '重新验证' : '去验证' }}
          </button>
        </div>

        <div v-if="statusMessage" class="auth-status" :class="sceneStatus">{{ statusMessage }}</div>
        <button class="auth-submit" type="submit" :disabled="loading">
          {{ loading ? '提交中...' : '发送重置邮件' }}
        </button>
      </form>

      <form v-else class="auth-form" @submit.prevent="submitConfirm">
        <div v-if="verifying" class="auth-status idle">正在校验重置链接...</div>
        <template v-else>
          <div v-if="!tokenValid" class="auth-status error">重置链接无效或已过期，请重新发起申请。</div>
          <div v-else class="auth-status success">链接已验证{{ resetUsername ? `：${resetUsername}` : '' }}</div>
          <div class="auth-field">
            <input v-model="newPassword" type="password" placeholder="新密码" autocomplete="new-password" :disabled="!tokenValid" />
          </div>
          <div class="auth-field">
            <input v-model="confirmPassword" type="password" placeholder="确认新密码" autocomplete="new-password" :disabled="!tokenValid" />
            <p v-if="fieldError" class="auth-error">{{ fieldError }}</p>
          </div>
          <div v-if="statusMessage" class="auth-status" :class="sceneStatus">{{ statusMessage }}</div>
          <button class="auth-submit" type="submit" :disabled="loading || !tokenValid">
            {{ loading ? '重置中...' : '确认重置密码' }}
          </button>
        </template>
      </form>

      <p class="auth-switch">
        想起密码了？<button class="auth-link" type="button" @click="router.push('/login')">返回登录</button>
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
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SliderCaptcha from '@/components/auth/SliderCaptcha.vue'
import { confirmPasswordReset, fetchCaptcha, requestPasswordReset, verifyCaptcha, verifyPasswordResetToken } from '@/api/modules'
import type { CaptchaTrackPayload } from '@/types/auth-models'

type SceneStatus = 'idle' | 'error' | 'success'

const route = useRoute()
const router = useRouter()
const token = ref(String(route.query.token ?? ''))
const username = ref('')
const email = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const resetUsername = ref('')
const tokenValid = ref(false)
const verifying = ref(false)
const loading = ref(false)
const fieldError = ref('')
const statusMessage = ref('')
const sceneStatus = ref<SceneStatus>('idle')

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

onMounted(async () => {
  if (!token.value) {
    return
  }
  verifying.value = true
  try {
    const result = await verifyPasswordResetToken(token.value)
    tokenValid.value = Boolean(result?.valid)
    resetUsername.value = result?.username || ''
  } finally {
    verifying.value = false
  }
})

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

async function submitRequest() {
  fieldError.value = ''
  statusMessage.value = ''
  if (!username.value) {
    fieldError.value = '请输入用户名'
    return
  }
  if (username.value.length < 5 || username.value.length > 16) {
    fieldError.value = '用户名长度需为 5-16 位'
    return
  }
  if (!email.value) {
    fieldError.value = '请输入绑定邮箱'
    return
  }
  if (!isValidEmail(email.value)) {
    fieldError.value = '请输入有效的邮箱地址'
    return
  }
  if (!captchaVerified.value) {
    sceneStatus.value = 'error'
    statusMessage.value = '请先完成滑块验证'
    await openCaptchaDialog({ refresh: true })
    return
  }
  loading.value = true
  try {
    const result = await requestPasswordReset({
      username: username.value,
      email: email.value,
      captchaId: captchaId.value,
    })
    if (result?.result === 'NOT_FOUND') {
      sceneStatus.value = 'error'
      statusMessage.value = result.message || '用户名不存在'
    } else if (result?.result === 'EMAIL_NOT_CONFIGURED') {
      sceneStatus.value = 'error'
      statusMessage.value = result.message || '该账号未绑定邮箱，无法通过邮件重置密码'
    } else {
      sceneStatus.value = 'success'
      statusMessage.value = result?.message || '如果账号存在且邮箱匹配，将会收到密码重置邮件'
    }
  } catch (error) {
    sceneStatus.value = 'error'
    statusMessage.value = resolveErrorMessage(error, '请求失败，请稍后再试')
  } finally {
    loading.value = false
  }
}

async function submitConfirm() {
  fieldError.value = ''
  statusMessage.value = ''
  if (newPassword.value.length < 8) {
    fieldError.value = '新密码至少 8 位'
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    fieldError.value = '两次输入的新密码不一致'
    return
  }
  loading.value = true
  try {
    const result = await confirmPasswordReset({ token: token.value, newPassword: newPassword.value })
    sceneStatus.value = 'success'
    statusMessage.value = result?.message || '密码已重置，请使用新密码登录'
    window.setTimeout(() => router.replace('/login'), 800)
  } catch (error) {
    sceneStatus.value = 'error'
    statusMessage.value = resolveErrorMessage(error, '重置失败，请重新申请')
  } finally {
    loading.value = false
  }
}

function isValidEmail(value: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
}

function resolveErrorMessage(error: unknown, fallback: string) {
  const maybe = error as { response?: { data?: { message?: string } } }
  return maybe?.response?.data?.message || fallback
}
</script>

<style scoped lang="scss">
.reset-password-page {
  background:
    radial-gradient(circle at 20% 20%, rgba(24, 144, 255, 0.18), transparent 32%),
    radial-gradient(circle at 80% 80%, rgba(82, 196, 26, 0.14), transparent 28%),
    linear-gradient(135deg, #f7faff 0%, #eef4fb 100%);
}

.reset-card {
  border: 1px solid rgba(30, 42, 64, 0.08);
  box-shadow: 0 28px 80px rgba(31, 45, 61, 0.14);
}
</style>