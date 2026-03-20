<template>
  <aside class="app-nav">
    <div class="brand">
      <span class="eyebrow">Enterprise Auth Platform</span>
      <h1>权限中台</h1>
      <p>认证、授权、多租户、审计与系统治理统一控制台。</p>
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

const links = [
  { to: '/dashboard', label: '运行总览', icon: Monitor, permission: 'auth:read' },
  { to: '/oauth-clients', label: 'OAuth2 客户端', icon: Lock, permission: 'auth:read' },
  { to: '/system/users', label: '用户管理', icon: Avatar, permission: 'user:read' },
  { to: '/system/roles', label: '角色管理', icon: Connection, permission: 'role:read' },
  { to: '/system/permissions', label: '权限管理', icon: Tickets, permission: 'permission:read' },
  { to: '/system/depts', label: '部门管理', icon: OfficeBuilding, permission: 'dept:read' },
  { to: '/system/tenants', label: '租户管理', icon: Flag, permission: 'tenant:read' },
  { to: '/system/audit', label: '安全审计', icon: Histogram, permission: 'audit:read' },
  { to: '/system/settings', label: '系统管理', icon: Setting, permission: 'system:read' },
]

const visibleLinks = computed(() => {
  const permissionSet = new Set(authStore.snapshot?.permissions ?? [])
  return links.filter((item) => permissionSet.has(item.permission))
})
</script>
