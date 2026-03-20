<template>
  <aside class="app-nav">
    <div class="brand">
      <span class="eyebrow">Enterprise Auth Platform</span>
      <h1>权限中台</h1>
      <p>统一管理认证、授权、多租户、审计与系统治理能力。</p>
    </div>

    <nav class="app-nav__links">
      <RouterLink v-for="item in visibleLinks" :key="item.to" :to="item.to" class="nav-link">
        <el-icon><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
      </RouterLink>
    </nav>

    <div class="tenant-card">
      <span>当前租户</span>
      <strong>{{ authStore.tenantId }}</strong>
      <small>{{ authStore.snapshot?.dataScopeType ?? '未登录' }}</small>
    </div>
  </aside>
</template>

<script setup lang="ts">
import {
  Avatar,
  Connection,
  Flag,
  Histogram,
  Key,
  Lock,
  Monitor,
  OfficeBuilding,
  Setting,
  Tickets,
} from '@element-plus/icons-vue'
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

const iconMap: Record<string, any> = {
  dashboard: Monitor,
  'oauth-clients': Lock,
  users: Avatar,
  roles: Connection,
  permissions: Tickets,
  depts: OfficeBuilding,
  tenants: Flag,
  audit: Histogram,
  settings: Setting,
  consents: Key,
}

const titleMap: Record<string, string> = {
  dashboard: '运行总览',
  'oauth-clients': 'OAuth2 客户端',
  users: '用户管理',
  roles: '角色管理',
  permissions: '权限管理',
  depts: '部门管理',
  tenants: '租户管理',
  audit: '安全审计',
  settings: '系统管理',
}

const visibleLinks = computed(() => {
  const links = authStore.menuItems.map((menu) => ({
    to: menu.path,
    label: titleMap[menu.code] || menu.title,
    icon: iconMap[menu.code] || Tickets,
  }))

  if (authStore.snapshot?.permissions.includes('auth:read')) {
    links.push({
      to: '/system/consents',
      label: '授权记录',
      icon: Key,
    })
    links.push({
      to: '/oauth-scopes',
      label: 'OAuth2 作用域',
      icon: Lock,
    })
  }

  return links
})
</script>
