import { computed } from 'vue'
import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import { fetchPermissionSnapshot, loginWithPassword, logoutCurrentSession, switchTenant as switchTenantSession } from '@/api/modules'
import type { PermissionSnapshot } from '@/types/auth-models'
import { usePermissionSnapshotStore } from './permissionSnapshot'
import { useSessionStore } from './session'
import { useTenantSessionStore } from './tenantSession'

const storageKey = 'eap.frontend.auth'

interface PersistedSession {
  authenticated: boolean
  token: string
  expiresAt: number
  passwordChangeRequired?: boolean
  passwordChangeReason?: string | null
  tenantId: string
  operatorTenantId?: string
  snapshot: PermissionSnapshot | null
}

export const useAuthStore = defineStore('auth', () => {
  const sessionStore = useSessionStore()
  const tenantStore = useTenantSessionStore()
  const permissionStore = usePermissionSnapshotStore()

  const authenticated = computed(() => sessionStore.authenticated)
  const token = computed(() => sessionStore.token)
  const expiresAt = computed(() => sessionStore.expiresAt)
  const passwordChangeRequired = computed(() => sessionStore.passwordChangeRequired)
  const passwordChangeReason = computed(() => sessionStore.passwordChangeReason)
  const tenantId = computed(() => tenantStore.tenantId)
  const operatorTenantId = computed(() => tenantStore.operatorTenantId)
  const snapshot = computed(() => permissionStore.snapshot)
  const tenantSwitching = computed(() => tenantStore.tenantSwitching)
  const isAuthenticated = computed(() => sessionStore.isAuthenticated)
  const menuItems = computed(() => permissionStore.menuItems)
  const canSwitchTenant = computed(() => permissionStore.canSwitchTenant)

  function hasGrant(required: string | string[]) {
    return permissionStore.hasGrant(required)
  }

  function restore() {
    const raw = sessionStorage.getItem(storageKey)
    if (!raw) {
      return
    }
    let parsed: PersistedSession
    try {
      parsed = JSON.parse(raw) as PersistedSession
    } catch {
      sessionStorage.removeItem(storageKey)
      return
    }
    sessionStore.restoreSession(parsed)
    tenantStore.restoreTenant(parsed)
    permissionStore.setSnapshot(parsed.snapshot)
    tenantStore.syncTenantFromSnapshot(parsed.snapshot)
    sessionStorage.setItem(storageKey, raw)
  }

  function persist() {
    const payload: PersistedSession = {
      authenticated: sessionStore.authenticated,
      token: sessionStore.token,
      expiresAt: sessionStore.expiresAt,
      passwordChangeRequired: sessionStore.passwordChangeRequired,
      passwordChangeReason: sessionStore.passwordChangeReason,
      tenantId: tenantStore.tenantId,
      operatorTenantId: tenantStore.operatorTenantId,
      snapshot: permissionStore.snapshot,
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
    sessionStore.setSession({
      token: session.token,
      expiresAt: session.expiresAt,
      passwordChangeRequired: session.passwordChangeRequired,
      passwordChangeReason: session.passwordChangeReason,
    })
    tenantStore.setOperatorTenant(session.tenantId || payload.tenantId || 'platform')
    if (session.passwordChangeRequired) {
      permissionStore.clearSnapshot()
      persist()
      return
    }
    const nextSnapshot = await fetchPermissionSnapshot()
    permissionStore.setSnapshot(nextSnapshot)
    tenantStore.syncTenantFromSnapshot(nextSnapshot)
    persist()
  }

  async function bootstrapSnapshot() {
    if (!sessionStore.authenticated) {
      return
    }
    const nextSnapshot = await fetchPermissionSnapshot()
    permissionStore.setSnapshot(nextSnapshot)
    tenantStore.syncTenantFromSnapshot(nextSnapshot)
    persist()
  }

  async function switchTenant(targetTenantId: string) {
    const trimmed = targetTenantId.trim()
    if (!trimmed || trimmed === tenantStore.tenantId) {
      return
    }
    if (!permissionStore.canSwitchTenant) {
      throw new Error('当前账号不支持租户切换')
    }
    if (tenantStore.tenantSwitching) {
      throw new Error('租户切换正在进行')
    }

    const requestId = tenantStore.nextTenantSwitchRequest()
    const previousSession = {
      tenantId: tenantStore.tenantId,
      operatorTenantId: tenantStore.operatorTenantId,
      snapshot: permissionStore.snapshot,
    }

    tenantStore.setTenantSwitching(true)
    try {
      const nextSnapshot = await switchTenantSession(trimmed)
      if (!tenantStore.isActiveTenantSwitchRequest(requestId)) {
        return
      }
      permissionStore.setSnapshot(nextSnapshot)
      tenantStore.syncTenantFromSnapshot(nextSnapshot)
      persist()
    } catch (error) {
      if (tenantStore.isActiveTenantSwitchRequest(requestId)) {
        tenantStore.restoreSwitchFailure(previousSession)
        permissionStore.setSnapshot(previousSession.snapshot)
        persist()
      }
      throw error
    } finally {
      if (tenantStore.isActiveTenantSwitchRequest(requestId)) {
        tenantStore.setTenantSwitching(false)
      }
    }
  }

    function clearSession() {
    sessionStore.clearSessionState()
    tenantStore.clearTenantState()
    permissionStore.clearSnapshot()
    sessionStorage.removeItem(storageKey)
  }

  function clearPasswordChangeRequirement() {
    sessionStore.clearPasswordChangeRequirement()
    persist()
  }

  async function logout() {
    try {
      if (sessionStore.authenticated) {
        await logoutCurrentSession()
      }
    } catch {
      // Keep local logout reliable even when backend logout fails.
    }
    clearSession()
    ElMessage.success('已退出当前会话')
  }

  return {
    authenticated,
    token,
    expiresAt,
    passwordChangeRequired,
    passwordChangeReason,
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
    clearPasswordChangeRequirement,
    clearSession,
    logout,
  }
})