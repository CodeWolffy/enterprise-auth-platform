import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import { fetchPermissionSnapshot, loginWithPassword, logoutCurrentSession, switchTenant as switchTenantSession } from '@/api/modules'
import type { MenuItem, PermissionSnapshot } from '@/types/auth'
import { clearDynamicRoutes, registerDynamicRoutes } from '@/router'

const storageKey = 'eap.frontend.auth'
interface PersistedSession {
  authenticated: boolean
  token: string
  expiresAt: number
  tenantId: string
  operatorTenantId?: string
  snapshot: PermissionSnapshot | null
}

export const useAuthStore = defineStore('auth', () => {
  const authenticated = ref(false)
  const token = ref('')
  const expiresAt = ref(0)
  const tenantId = ref('platform')
  const operatorTenantId = ref('platform')
  const snapshot = ref<PermissionSnapshot | null>(null)
  const tenantSwitching = ref(false)
  let tenantSwitchRequestId = 0

  const isAuthenticated = computed(() => authenticated.value)
  const menuItems = computed(() => flattenMenuItems(snapshot.value?.menus ?? []))
  const canSwitchTenant = computed(() => Boolean(snapshot.value?.superAdmin))

  function hasGrant(required: string | string[]) {
    if (!required || (Array.isArray(required) && required.length === 0)) {
      return true
    }
    if (snapshot.value?.superAdmin) {
      return true
    }
    const grantSet = new Set(snapshot.value?.grants ?? [])
    return Array.isArray(required)
      ? required.some((grant) => grantSet.has(grant))
      : grantSet.has(required)
  }

  function restore() {
    const raw = sessionStorage.getItem(storageKey) ?? localStorage.getItem(storageKey)
    if (!raw) {
      return
    }
    const parsed = JSON.parse(raw) as PersistedSession
    authenticated.value = parsed.authenticated
    token.value = parsed.token || ''
    expiresAt.value = parsed.expiresAt
    tenantId.value = parsed.tenantId || 'platform'
    operatorTenantId.value = parsed.operatorTenantId || parsed.tenantId || 'platform'
    snapshot.value = parsed.snapshot
    syncTenantFromSnapshot()
    registerDynamicRoutes(snapshot.value)
    sessionStorage.setItem(storageKey, raw)
    localStorage.removeItem(storageKey)
  }

  function persist() {
    const payload: PersistedSession = {
      authenticated: authenticated.value,
      token: token.value,
      expiresAt: expiresAt.value,
      tenantId: tenantId.value,
      operatorTenantId: operatorTenantId.value,
      snapshot: snapshot.value,
    }
    sessionStorage.setItem(storageKey, JSON.stringify(payload))
  }

  async function login(payload: {
    username: string
    password: string
    captchaId: string
    captchaCode: string
    tenantId?: string
    device?: string
  }) {
    const session = await loginWithPassword(payload)
    authenticated.value = true
    token.value = session.token
    expiresAt.value = Number.isFinite(session.expiresAt) ? session.expiresAt : Date.now() + 7 * 24 * 60 * 60 * 1000
    operatorTenantId.value = session.tenantId || payload.tenantId || 'platform'
    tenantId.value = operatorTenantId.value
    snapshot.value = await fetchPermissionSnapshot()
    syncTenantFromSnapshot()
    registerDynamicRoutes(snapshot.value)
    persist()
  }

  async function bootstrapSnapshot() {
    if (!authenticated.value) {
      return
    }
    snapshot.value = await fetchPermissionSnapshot()
    syncTenantFromSnapshot()
    registerDynamicRoutes(snapshot.value)
    persist()
  }

  async function switchTenant(targetTenantId: string) {
    const trimmed = targetTenantId.trim()
    if (!trimmed || trimmed === tenantId.value) {
      return
    }
    if (!canSwitchTenant.value) {
      throw new Error('当前账号不支持租户切换')
    }
    if (tenantSwitching.value) {
      throw new Error('租户切换正在进行')
    }

    const requestId = ++tenantSwitchRequestId
    const previousSession = {
      tenantId: tenantId.value,
      operatorTenantId: operatorTenantId.value,
      snapshot: snapshot.value,
    }

    tenantSwitching.value = true
    try {
      const nextSnapshot = await switchTenantSession(trimmed)
      if (requestId !== tenantSwitchRequestId) {
        return
      }
      snapshot.value = nextSnapshot
      syncTenantFromSnapshot()
      registerDynamicRoutes(snapshot.value)
      persist()
    } catch (error) {
      if (requestId === tenantSwitchRequestId) {
        tenantId.value = previousSession.tenantId
        operatorTenantId.value = previousSession.operatorTenantId
        snapshot.value = previousSession.snapshot
        registerDynamicRoutes(snapshot.value)
        persist()
      }
      throw error
    } finally {
      if (requestId === tenantSwitchRequestId) {
        tenantSwitching.value = false
      }
    }
  }

  function clearSession() {
    authenticated.value = false
    token.value = ''
    expiresAt.value = 0
    tenantId.value = 'platform'
    operatorTenantId.value = 'platform'
    snapshot.value = null
    sessionStorage.removeItem(storageKey)
    localStorage.removeItem(storageKey)
    clearDynamicRoutes()
  }

  async function logout() {
    try {
      if (authenticated.value) {
        await logoutCurrentSession()
      }
    } catch {
      // Keep local logout reliable even when backend logout fails.
    }
    clearSession()
    ElMessage.success('已退出当前会话')
  }

  function syncTenantFromSnapshot() {
    if (!snapshot.value) {
      return
    }
    const operator = snapshot.value.operatorTenantId || operatorTenantId.value || snapshot.value.tenantId
    operatorTenantId.value = operator
    if (snapshot.value.superAdmin) {
      tenantId.value = snapshot.value.tenantId || tenantId.value || operator
      return
    }
    tenantId.value = operator
  }

  function flattenMenuItems(nodes: MenuItem[]): MenuItem[] {
    const result: MenuItem[] = []
    const walk = (items: MenuItem[]) => {
      for (const item of items) {
        const path = item.path?.trim()
        if (path) {
          result.push(item)
        }
        if (item.children?.length) {
          walk(item.children)
        }
      }
    }
    walk(nodes)
    return result
  }

  return {
    authenticated,
    token,
    expiresAt,
    tenantId,
    operatorTenantId,
    snapshot,
    tenantSwitching,
    isAuthenticated,
    menuItems,
    canSwitchTenant,
    hasGrant,
    restore,
    bootstrapSnapshot,
    login,
    switchTenant,
    clearSession,
    logout,
  }
})
