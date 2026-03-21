import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import { exchangeAuthorizationCode, fetchPermissionSnapshot, logoutCurrentSession, refreshOauthToken } from '@/api/auth'
import type { OAuthTokenResponse, PermissionSnapshot } from '@/types/auth'
import { createOAuthRedirect } from '@/utils/oauth'
import { clearDynamicRoutes, registerDynamicRoutes } from '@/router'

const storageKey = 'eap.frontend.auth'

interface PersistedSession {
  accessToken: string
  refreshToken: string
  expiresAt: number
  tenantId: string
  snapshot: PermissionSnapshot | null
}

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref('')
  const refreshToken = ref('')
  const expiresAt = ref(0)
  const tenantId = ref('platform')
  const snapshot = ref<PermissionSnapshot | null>(null)
  let refreshingPromise: Promise<void> | null = null

  const isAuthenticated = computed(() => Boolean(accessToken.value && snapshot.value))
  const menuItems = computed(() => snapshot.value?.menus ?? [])

  function restore() {
    const raw = localStorage.getItem(storageKey)
    if (!raw) {
      return
    }
    const parsed = JSON.parse(raw) as PersistedSession
    accessToken.value = parsed.accessToken
    refreshToken.value = parsed.refreshToken
    expiresAt.value = parsed.expiresAt
    tenantId.value = parsed.tenantId
    snapshot.value = parsed.snapshot
    registerDynamicRoutes(snapshot.value)
  }

  function persist() {
    const payload: PersistedSession = {
      accessToken: accessToken.value,
      refreshToken: refreshToken.value,
      expiresAt: expiresAt.value,
      tenantId: tenantId.value,
      snapshot: snapshot.value,
    }
    localStorage.setItem(storageKey, JSON.stringify(payload))
  }

  async function startLogin(selectedTenantId: string) {
    const target = await createOAuthRedirect(selectedTenantId)
    window.location.href = target
  }

  async function finishLogin(code: string, state: string) {
    const { payload, tenantId: resolvedTenantId } = await exchangeAuthorizationCode(code, state)
    applyTokenPayload(payload, resolvedTenantId)
    snapshot.value = await fetchPermissionSnapshot()
    registerDynamicRoutes(snapshot.value)
    persist()
  }

  async function refreshTokens(options?: { reloadSnapshot?: boolean }) {
    if (refreshingPromise) {
      await refreshingPromise
      return
    }

    const reloadSnapshot = Boolean(options?.reloadSnapshot)
    refreshingPromise = (async () => {
      if (!refreshToken.value) {
        throw new Error('missing refresh token')
      }
      const payload = (await refreshOauthToken(refreshToken.value)) as OAuthTokenResponse
      applyTokenPayload(payload, tenantId.value)

      if (reloadSnapshot) {
        snapshot.value = await fetchPermissionSnapshot()
        registerDynamicRoutes(snapshot.value)
      }
      persist()
    })()

    try {
      await refreshingPromise
    } finally {
      refreshingPromise = null
    }
  }

  async function bootstrapSnapshot() {
    if (!accessToken.value) {
      return
    }
    if (shouldRefreshToken()) {
      await refreshTokens({ reloadSnapshot: true })
      return
    }
    snapshot.value = await fetchPermissionSnapshot()
    registerDynamicRoutes(snapshot.value)
    persist()
  }

  function shouldRefreshToken() {
    return Boolean(refreshToken.value) && Date.now() > expiresAt.value - 60_000
  }

  function clearSession() {
    accessToken.value = ''
    refreshToken.value = ''
    expiresAt.value = 0
    tenantId.value = 'platform'
    snapshot.value = null
    localStorage.removeItem(storageKey)
    clearDynamicRoutes()
  }

  async function logout() {
    try {
      if (accessToken.value) {
        await logoutCurrentSession()
      }
    } catch {
      // Keep local logout reliable even when backend logout fails.
    }
    clearSession()
    ElMessage.success('已退出当前会话')
  }

  function applyTokenPayload(payload: OAuthTokenResponse, resolvedTenantId: string) {
    accessToken.value = payload.access_token
    refreshToken.value = payload.refresh_token ?? ''
    expiresAt.value = Date.now() + payload.expires_in * 1000
    tenantId.value = resolvedTenantId
  }

  return {
    accessToken,
    refreshToken,
    expiresAt,
    tenantId,
    snapshot,
    isAuthenticated,
    menuItems,
    restore,
    bootstrapSnapshot,
    startLogin,
    finishLogin,
    refreshTokens,
    shouldRefreshToken,
    clearSession,
    logout,
  }
})
