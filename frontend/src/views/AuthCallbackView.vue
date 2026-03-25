<template>
  <div class="callback-stage">
    <el-result :icon="resultIcon" :title="resultTitle" :sub-title="message">
      <template #extra>
        <el-button v-if="showRetry" type="primary" @click="backToLogin">返回登录</el-button>
        <el-button v-else-if="showDashboardEntry" type="primary" @click="goDashboard">进入控制台</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const message = ref('正在与授权服务器交换令牌，请稍候。')
const status = ref<'loading' | 'success' | 'error'>('loading')

const resultIcon = computed(() => {
  if (status.value === 'success') {
    return 'success'
  }
  if (status.value === 'error') {
    return 'error'
  }
  return 'info'
})

const resultTitle = computed(() => {
  if (status.value === 'success') {
    return '授权成功'
  }
  if (status.value === 'error') {
    return '授权失败'
  }
  return '正在完成授权登录'
})

const showRetry = computed(() => status.value === 'error')
const showDashboardEntry = computed(() => status.value === 'success')

function backToLogin() {
  router.replace('/login')
}

function goDashboard() {
  router.replace('/dashboard')
}

onMounted(async () => {
  const params = new URLSearchParams(window.location.search)
  const code = params.get('code')
  const state = params.get('state')
  const error = params.get('error')

  if (error) {
    status.value = 'error'
    message.value = `授权失败：${error}`
    setTimeout(() => router.replace('/login'), 1200)
    return
  }

  if (!code || !state) {
    status.value = 'error'
    message.value = '缺少授权参数，请重新登录。'
    setTimeout(() => router.replace('/login'), 1200)
    return
  }

  try {
    await authStore.finishLogin(code, state)
    status.value = 'success'
    message.value = '授权成功，正在进入控制台。'
    setTimeout(() => router.replace('/dashboard'), 600)
  } catch (err) {
    status.value = 'error'
    message.value = err instanceof Error ? err.message : '登录失败，请重新尝试。'
    setTimeout(() => router.replace('/login'), 1500)
  }
})
</script>

<style scoped>
.callback-stage {
  min-height: calc(100vh - 120px);
  display: grid;
  place-items: center;
  padding: 24px;
}

.callback-stage :deep(.el-result) {
  width: min(680px, 100%);
  border-radius: 20px;
  border: 1px solid rgba(22, 40, 75, 0.1);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 20px 50px rgba(22, 40, 75, 0.12);
  padding: 20px;
}
</style>
