<template>
  <div class="callback-stage">
    <el-result icon="info" title="正在完成授权登录" :sub-title="message" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const message = ref('正在与授权服务器交换令牌，请稍候。')

onMounted(async () => {
  const params = new URLSearchParams(window.location.search)
  const code = params.get('code')
  const state = params.get('state')
  const error = params.get('error')

  if (error) {
    message.value = `授权失败：${error}`
    setTimeout(() => router.replace('/login'), 1200)
    return
  }

  if (!code || !state) {
    message.value = '缺少授权参数，请重新登录。'
    setTimeout(() => router.replace('/login'), 1200)
    return
  }

  try {
    await authStore.finishLogin(code, state)
    message.value = '授权成功，正在进入控制台。'
    setTimeout(() => router.replace('/dashboard'), 600)
  } catch (err) {
    message.value = err instanceof Error ? err.message : '登录失败，请重新尝试。'
    setTimeout(() => router.replace('/login'), 1500)
  }
})
</script>
