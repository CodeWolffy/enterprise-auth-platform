import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { PermissionSnapshot } from '@/types/auth'

const DEFAULT_TENANT_ID = 'platform'

export const useTenantSessionStore = defineStore('tenantSession', () => {
  const tenantId = ref(DEFAULT_TENANT_ID)
  const operatorTenantId = ref(DEFAULT_TENANT_ID)
  const tenantSwitching = ref(false)
  let tenantSwitchRequestId = 0

  function restoreTenant(payload: { tenantId?: string; operatorTenantId?: string }) {
    tenantId.value = payload.tenantId || DEFAULT_TENANT_ID
    operatorTenantId.value = payload.operatorTenantId || payload.tenantId || DEFAULT_TENANT_ID
  }

  function setOperatorTenant(nextTenantId: string) {
    operatorTenantId.value = nextTenantId || DEFAULT_TENANT_ID
    tenantId.value = operatorTenantId.value
  }

  function syncTenantFromSnapshot(snapshot: PermissionSnapshot | null) {
    if (!snapshot) {
      return
    }
    const operator = snapshot.operatorTenantId || operatorTenantId.value || snapshot.tenantId
    operatorTenantId.value = operator
    if (snapshot.superAdmin) {
      tenantId.value = snapshot.tenantId || tenantId.value || operator
      return
    }
    tenantId.value = operator
  }

  function nextTenantSwitchRequest() {
    tenantSwitchRequestId += 1
    return tenantSwitchRequestId
  }

  function isActiveTenantSwitchRequest(requestId: number) {
    return requestId === tenantSwitchRequestId
  }

  function setTenantSwitching(nextSwitching: boolean) {
    tenantSwitching.value = nextSwitching
  }

  function restoreSwitchFailure(previousSession: {
    tenantId: string
    operatorTenantId: string
  }) {
    tenantId.value = previousSession.tenantId
    operatorTenantId.value = previousSession.operatorTenantId
  }

  function clearTenantState() {
    tenantId.value = DEFAULT_TENANT_ID
    operatorTenantId.value = DEFAULT_TENANT_ID
    tenantSwitching.value = false
    tenantSwitchRequestId += 1
  }

  return {
    tenantId,
    operatorTenantId,
    tenantSwitching,
    restoreTenant,
    setOperatorTenant,
    syncTenantFromSnapshot,
    nextTenantSwitchRequest,
    isActiveTenantSwitchRequest,
    setTenantSwitching,
    restoreSwitchFailure,
    clearTenantState,
  }
})