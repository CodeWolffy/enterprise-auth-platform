import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { usePermissionSnapshotStore } from './permissionSnapshot'
import type { PermissionSnapshot } from '@/types/auth'

vi.mock('@/router', () => ({
  registerDynamicRoutes: vi.fn(),
  clearDynamicRoutes: vi.fn(),
}))

describe('permissionSnapshot store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('flattens menu items and checks grants', () => {
    const store = usePermissionSnapshotStore()
    store.setSnapshot(snapshot({ superAdmin: false }))

    expect(store.menuItems.map((item) => item.path)).toEqual(['/dashboard', '/system/users'])
    expect(store.hasGrant('user:read')).toBe(true)
    expect(store.hasGrant(['role:read', 'user:write'])).toBe(false)
  })

  it('allows all grants for super admin', () => {
    const store = usePermissionSnapshotStore()
    store.setSnapshot(snapshot({ superAdmin: true }))

    expect(store.canSwitchTenant).toBe(true)
    expect(store.hasGrant('missing:grant')).toBe(true)
  })
})

function snapshot(options: { superAdmin: boolean }): PermissionSnapshot {
  return {
    userId: 1,
    username: 'tester',
    tenantId: 'tenant-a',
    operatorTenantId: 'tenant-a',
    superAdmin: options.superAdmin,
    roles: ['USER'],
    grants: ['user:read'],
    dataScopeType: 'ALL',
    customDeptIds: [],
    menus: [
      {
        id: 1,
        code: 'dashboard',
        title: '运行总览',
        path: '/dashboard',
        routeKey: 'dashboard',
        children: [],
      },
      {
        id: 2,
        code: 'system',
        title: '系统管理',
        path: '',
        routeKey: '',
        children: [
          {
            id: 3,
            code: 'users',
            title: '用户管理',
            path: '/system/users',
            routeKey: 'users',
            children: [],
          },
        ],
      },
    ],
  }
}