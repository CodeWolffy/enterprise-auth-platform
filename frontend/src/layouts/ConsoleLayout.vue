<template>
  <div class="console-shell">
    <AppNav />
    <main class="console-main">
      <header class="console-header">
        <div>
          <span class="eyebrow">Database + Spring Authorization Server</span>
          <h2>{{ pageTitle }}</h2>
        </div>
        <div class="console-header__actions">
          <div class="identity">
            <strong>{{ authStore.snapshot?.username }}</strong>
            <span>{{ authStore.snapshot?.roles?.join(' / ') || '未加载角色' }}</span>
          </div>
          <el-button type="primary" plain @click="authStore.logout()">退出当前会话</el-button>
        </div>
      </header>

      <section class="console-content">
        <RouterView />
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import AppNav from '@/components/AppNav.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const authStore = useAuthStore()

const pageTitle = computed(() => String(route.meta.title ?? '控制台'))
</script>
