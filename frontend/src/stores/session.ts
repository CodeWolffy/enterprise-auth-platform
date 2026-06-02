import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

const DEFAULT_TENANT_ID = 'platform'

export const useSessionStore = defineStore('session', () => {
  const authenticated = ref(false)
  const token = ref('')
  const expiresAt = ref(0)
  const passwordChangeRequired = ref(false)
  const passwordChangeReason = ref<string | null>(null)

  const isAuthenticated = computed(() => authenticated.value)

  function setSession(payload: {
    token: string
    expiresAt?: number
    passwordChangeRequired?: boolean
    passwordChangeReason?: string | null
  }) {
    authenticated.value = true
    token.value = payload.token
    expiresAt.value = Number.isFinite(payload.expiresAt)
      ? Number(payload.expiresAt)
      : Date.now() + 7 * 24 * 60 * 60 * 1000
    passwordChangeRequired.value = Boolean(payload.passwordChangeRequired)
    passwordChangeReason.value = payload.passwordChangeReason ?? null
  }

  function restoreSession(payload: {
    authenticated?: boolean
    token?: string
    expiresAt?: number
    passwordChangeRequired?: boolean
    passwordChangeReason?: string | null
  }) {
    authenticated.value = Boolean(payload.authenticated)
    token.value = payload.token || ''
    expiresAt.value = Number(payload.expiresAt ?? 0)
    passwordChangeRequired.value = Boolean(payload.passwordChangeRequired)
    passwordChangeReason.value = payload.passwordChangeReason ?? null
  }

  function clearPasswordChangeRequirement() {
    passwordChangeRequired.value = false
    passwordChangeReason.value = null
  }

  function clearSessionState() {
    authenticated.value = false
    token.value = ''
    expiresAt.value = 0
    clearPasswordChangeRequirement()
  }

  return {
    DEFAULT_TENANT_ID,
    authenticated,
    token,
    expiresAt,
    passwordChangeRequired,
    passwordChangeReason,
    isAuthenticated,
    setSession,
    restoreSession,
    clearPasswordChangeRequirement,
    clearSessionState,
  }
})