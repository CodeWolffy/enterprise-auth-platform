<template>
  <aside class="app-nav">
    <div class="brand">
      <span class="eyebrow">企业级权限中台</span>
      <h1>权限中台</h1>
      <p>统一管理认证、多租户、审计与系统治理能力。</p>
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
      <el-select
        v-if="canLoadTenants"
        :model-value="authStore.tenantId"
        placeholder="切换租户视角"
        size="small"
        filterable
        style="margin-top: 8px"
        @change="handleTenantChange"
      >
        <el-option
          v-for="tenant in tenantOptions"
          :key="tenant.tenantId"
          :label="`${tenant.name} (${tenant.tenantId})`"
          :value="tenant.tenantId"
        />
      </el-select>
      <small v-if="authStore.canSwitchTenant">操作员租户：{{ authStore.operatorTenantId }}</small>
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
  Monitor,
  OfficeBuilding,
  Setting,
  Tickets,
} from '@element-plus/icons-vue'
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { queryTenants } from '@/api/platform'
import type { MenuItem } from '@/types/auth'

const authStore = useAuthStore()
const tenantOptions = ref<Array<{ tenantId: string; name: string }>>([])

const ROUTE_KEY_PATH_MAP: Record<string, string> = {
  dashboard: '/dashboard',
  users: '/system/users',
  roles: '/system/roles',
  depts: '/system/depts',
  tenants: '/system/tenants',
  audit: '/system/audit',
  settings: '/system/settings',
}

const canLoadTenants = computed(() => {
  if (!authStore.canSwitchTenant) {
    return false
  }
  return Boolean(authStore.snapshot?.grants.includes('tenant:read'))
})

const iconMap: Record<string, any> = {
  dashboard: Monitor,
  users: Avatar,
  roles: Connection,
  depts: OfficeBuilding,
  tenants: Flag,
  audit: Histogram,
  settings: Setting,
}

const titleMap: Record<string, string> = {
  dashboard: '运行总览',
  users: '用户管理',
  roles: '角色管理',
  depts: '部门管理',
  tenants: '租户管理',
  audit: '安全审计',
  settings: '系统管理',
}

const visibleLinks = computed(() => {
  const flattened = flattenMenus(authStore.snapshot?.menus ?? [])
  const links = flattened
    .map((menu) => {
      const routeKey = menu.routeKey?.trim()
      if (!routeKey) {
        return null
      }
      const path = ROUTE_KEY_PATH_MAP[routeKey]
      if (!path) {
        console.warn('[auth] 后端菜单快照中存在未知的路由键:', routeKey)
        return null
      }
      return {
        to: path,
        label: titleMap[routeKey] || menu.title,
        icon: iconMap[routeKey] || Tickets,
      }
    })
    .filter((item): item is { to: string; label: string; icon: any } => Boolean(item))

  const deduplicated = new Map<string, { to: string; label: string; icon: any }>()
  for (const item of links) {
    if (!deduplicated.has(item.to)) {
      deduplicated.set(item.to, item)
    }
  }
  return Array.from(deduplicated.values())
})

function flattenMenus(nodes: MenuItem[]): MenuItem[] {
  const result: MenuItem[] = []
  const walk = (items: MenuItem[]) => {
    for (const item of items) {
      result.push(item)
      if (item.children?.length) {
        walk(item.children)
      }
    }
  }
  walk(nodes)
  return result
}

async function loadTenantOptions() {
  for (let attempt = 0; attempt < 2; attempt += 1) {
    try {
      const page = await queryTenants(
        { page: 1, size: 200 },
        { silentAuthFailure: true, suppressErrorMessage: true },
      )
      tenantOptions.value = page.records.map((item) => ({ tenantId: item.tenantId, name: item.name }))
      return
    } catch {
      if (attempt === 1) {
        tenantOptions.value = []
        return
      }
      await new Promise((resolve) => window.setTimeout(resolve, 200))
    }
  }
}

watch(
  () => [canLoadTenants.value, authStore.authenticated] as const,
  async ([canLoad, authenticated]) => {
    if (!canLoad || !authenticated) {
      tenantOptions.value = []
      return
    }
    await loadTenantOptions()
  },
  { immediate: true },
)

async function handleTenantChange(value: string) {
  try {
    await authStore.switchTenant(value)
    ElMessage.success(`已切换到租户视角：${value}`)
  } catch {
    ElMessage.error('租户切换失败')
  }
}
</script>
