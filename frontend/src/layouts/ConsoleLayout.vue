<template>
  <div class="console-shell">
    <AppNav />
    <main class="console-main">
      <header class="console-header">
        <div class="console-header__title">
          <span class="eyebrow">数据库 + Spring Authorization Server</span>
          <h2>{{ pageTitle }}</h2>
        </div>
        <div class="console-header__actions">
          <div class="identity">
            <strong>{{ authStore.snapshot?.username }}</strong>
            <span>{{ authStore.snapshot?.roles?.join(' / ') || '未加载角色' }}</span>
          </div>
          <el-button type="primary" plain @click="handleLogout">退出当前会话</el-button>
        </div>
      </header>

      <section class="console-content" :class="isDashboard ? 'console-content--dashboard' : 'console-content--management'">
        <RouterView />
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import AppNav from '@/components/AppNav.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const pageTitle = computed(() => String(route.meta.title ?? '控制台'))
const isDashboard = computed(() => route.name === 'dashboard')

async function handleLogout() {
  await authStore.logout()
  await router.replace({ name: 'login' })
}
</script>
