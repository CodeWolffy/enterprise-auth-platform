import { describe, expect, it } from 'vitest'
import { isAllowedRoute, resolveFirstAllowedPath } from './route-access'
import type { PermissionSnapshot } from '@/types/auth-models'

function snapshot(options?: { superAdmin?: boolean; grants?: string[]; menus?: { path?: string; permission?: string; component?: string; code?: string; children?: unknown[] }[] }): PermissionSnapshot {
  return {
    userId: 1,
    username: 'tester',
    tenantId: 'tenant-a',
    operatorTenantId: 'tenant-a',
    superAdmin: options?.superAdmin ?? false,
    roles: ['USER'],
    grants: options?.grants ?? [],
    dataScopeType: 'ALL',
    customDeptIds: [],
    menus: (options?.menus ?? []).map((item, index) => ({
      id: index + 1,
      code: item.code ?? '',
      title: 'Menu',
      path: item.path ?? '',
      routeKey: '',
      component: item.component,
      permission: item.permission,
      children: (item.children ?? []) as never,
    })),
  }
}

describe('isAllowedRoute', () => {
  it('允许访问 skipMenuAccess 的隐藏静态路由，只要拥有所需权限', () => {
    const s = snapshot({ menus: [{ path: '/dashboard' }] })
    expect(isAllowedRoute(s, { path: '/account/profile', meta: { skipMenuAccess: true } })).toBe(true)
  })

  it('skipMenuAccess 路由仍受 requiresGrant 约束', () => {
    const s = snapshot({ menus: [{ path: '/dashboard' }] })
    expect(
      isAllowedRoute(s, {
        path: '/some/internal',
        meta: { skipMenuAccess: true, requiresGrant: 'upms:secret:page' },
      }),
    ).toBe(false)
  })

  it('生成菜单路径还必须拥有模块 page 权限', () => {
    const s = snapshot({ menus: [{ path: '/platform/generated/orderGen' }] })
    expect(isAllowedRoute(s, { path: '/platform/generated/orderGen', meta: { generatedRoute: true } })).toBe(false)

    const granted = snapshot({
      grants: ['orderGen:page'],
      menus: [{ path: '/platform/generated/orderGen' }],
    })
    expect(isAllowedRoute(granted, { path: '/platform/generated/orderGen', meta: { generatedRoute: true } })).toBe(true)
  })

  it('普通菜单路径必须出现在快照菜单中', () => {
    const s = snapshot({ grants: ['upms:dashboard:page'], menus: [{ path: '/dashboard' }] })
    expect(isAllowedRoute(s, { path: '/system/users', meta: {} })).toBe(false)
    expect(isAllowedRoute(s, { path: '/dashboard', meta: {} })).toBe(true)
  })

  it('超级管理员仍需路径在快照菜单中，但拥有任意权限', () => {
    const s = snapshot({ superAdmin: true, menus: [{ path: '/dashboard' }] })
    expect(isAllowedRoute(s, { path: '/system/users', meta: {} })).toBe(false)
    expect(isAllowedRoute(s, { path: '/dashboard', meta: { requiresGrant: 'upms:any:page' } })).toBe(true)
  })
})

describe('resolveFirstAllowedPath', () => {
  it('返回第一个可访问的菜单路径', () => {
    const s = snapshot({ grants: ['upms:dashboard:page'], menus: [{ path: '/dashboard' }, { path: '/system/users' }] })
    expect(resolveFirstAllowedPath(s)).toBe('/dashboard')
  })

  it('无可用菜单时返回 null', () => {
    const s = snapshot({ menus: [] })
    expect(resolveFirstAllowedPath(s)).toBeNull()
  })
})
