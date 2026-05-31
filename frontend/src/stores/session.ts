import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

const DEFAULT_TENANT_ID = 'platform'

export const useSessionStore = defineStore('session', () => {
  const authenticated = ref(false)
  const token = ref('')
  const expiresAt = ref(0)

  const isAuthenticated = computed(() => authenticated.value)

  function setSession(payload: { token: string; expiresAt?: number }) {
    authenticated.value = true
    token.value = payload.token
    expiresAt.value = Number.isFinite(payload.expiresAt)
      ? Number(payload.expiresAt)
      : Date.now() + 7 * 24 * 60 * 60 * 1000
  }

  function restoreSession(payload: { authenticated?: boolean; token?: string; expiresAt?: number }) {
    authenticated.value = Boolean(payload.authenticated)
    token.value = payload.token || ''
    expiresAt.value = Number(payload.expiresAt ?? 0)
  }

  function clearSessionState() {
    authenticated.value = false
    token.value = ''
    expiresAt.value = 0
  }

  return {
    DEFAULT_TENANT_ID,
    authenticated,
    token,
    expiresAt,
    isAuthenticated,
    setSession,
    restoreSession,
    clearSessionState,
  }
})