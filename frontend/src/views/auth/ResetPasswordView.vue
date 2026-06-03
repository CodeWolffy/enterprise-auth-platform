<template>
  <div class="auth-page reset-password-page">
    <div class="auth-card reset-card">
      <div class="auth-logo">
        <span class="auth-logo__mark">EA</span>
      </div>
      <h1 class="auth-title">重置密码</h1>
      <p class="auth-subtitle">
        {{ token ? '请设置新密码，完成后使用新密码登录。' : '输入用户名后，如果账号存在且绑定邮箱，将收到重置链接。' }}
      </p>

      <form v-if="!token" class="auth-form" @submit.prevent="submitRequest">
        <div class="auth-field">
          <input v-model.trim="username" type="text" placeholder="用户名（5-16 位）" autocomplete="username" />
          <p v-if="fieldError" class="auth-error">{{ fieldError }}</p>
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { confirmPasswordReset, requestPasswordReset, verifyPasswordResetToken } from '@/api/modules'

type SceneStatus = 'idle' | 'error' | 'success'

const route = useRoute()
const router = useRouter()
const token = ref(String(route.query.token ?? ''))
const username = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const resetUsername = ref('')
const tokenValid = ref(false)
const verifying = ref(false)
const loading = ref(false)
const fieldError = ref('')
const statusMessage = ref('')
const sceneStatus = ref<SceneStatus>('idle')

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
  loading.value = true
  try {
    const result = await requestPasswordReset({ username: username.value })
    if (result?.result === 'NOT_FOUND') {
      sceneStatus.value = 'error'
      statusMessage.value = result.message || '用户名不存在'
    } else if (result?.result === 'EMAIL_NOT_CONFIGURED') {
      sceneStatus.value = 'error'
      statusMessage.value = result.message || '该账号未绑定邮箱，无法通过邮件重置密码'
    } else {
      sceneStatus.value = 'success'
      statusMessage.value = result?.message || '如果账号存在且已配置邮箱，将会收到密码重置邮件'
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