import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { MenuItem, PermissionSnapshot } from '@/types/auth-models'
import { clearDynamicRoutes, registerDynamicRoutes } from '@/router'

export const usePermissionSnapshotStore = defineStore('permissionSnapshot', () => {
  const snapshot = ref<PermissionSnapshot | null>(null)

  const menuItems = computed(() => flattenMenuItems(snapshot.value?.menus ?? []))
  const canSwitchTenant = computed(() => Boolean(snapshot.value?.superAdmin))

  function setSnapshot(nextSnapshot: PermissionSnapshot | null) {
    snapshot.value = nextSnapshot
    if (nextSnapshot) {
      registerDynamicRoutes(nextSnapshot)
      return
    }
    clearDynamicRoutes()
  }

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

  function clearSnapshot() {
    setSnapshot(null)
  }

  return {
    snapshot,
    menuItems,
    canSwitchTenant,
    setSnapshot,
    hasGrant,
    clearSnapshot,
  }
})

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