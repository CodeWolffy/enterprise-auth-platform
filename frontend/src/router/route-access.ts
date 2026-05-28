import type { RouteLocationNormalized } from 'vue-router'
import { ROUTE_KEY_PATH_MAP } from '@/app/registry/module-manifest'
import type { MenuItem, PermissionSnapshot } from '@/types/auth'

interface RouteAccessTarget {
  meta: RouteLocationNormalized['meta']
  name?: RouteLocationNormalized['name'] | null
  path: string
}

const ROUTE_KEYS = new Set(Object.keys(ROUTE_KEY_PATH_MAP))

export function resolveRoutePath(routeKey?: string | null) {
  if (!routeKey) {
    return ''
  }
  return ROUTE_KEY_PATH_MAP[routeKey.trim()] ?? ''
}

export function collectAllowedRouteKeys(menus: MenuItem[]) {
  const routeKeys = new Set<string>()
  const walk = (items: MenuItem[]) => {
    for (const item of items) {
      const routeKey = item.routeKey?.trim()
      if (routeKey) {
        if (ROUTE_KEYS.has(routeKey)) {
          routeKeys.add(routeKey)
        } else {
          console.warn('[auth] 后端菜单快照中存在未知的路由键:', routeKey)
        }
      }
      if (item.children?.length) {
        walk(item.children)
      }
    }
  }
  walk(menus)
  return routeKeys
}

export function isAllowedRoute(snapshot: PermissionSnapshot | null, to: RouteAccessTarget) {
  if (!snapshot) {
    return false
  }

  const requiredGrant = String(to.meta.requiresGrant ?? '').trim()
  if (requiredGrant && !snapshot.superAdmin && !(snapshot.grants ?? []).includes(requiredGrant)) {
    return false
  }

  const routeKey = String(to.meta.routeKey ?? '').trim()
  if (!routeKey) {
    return true
  }

  const routeKeys = collectAllowedRouteKeys(snapshot.menus ?? [])
  return routeKeys.has(routeKey)
}

export function resolveFirstAllowedPath(snapshot: PermissionSnapshot | null) {
  if (!snapshot) {
    return null
  }

  const routeKeys = collectAllowedRouteKeys(snapshot.menus ?? [])
  const menuList = flattenMenuTree(snapshot.menus ?? [])
  for (const menu of menuList) {
    const routeKey = menu.routeKey?.trim()
    if (!routeKey || !routeKeys.has(routeKey)) {
      continue
    }
    const path = resolveRoutePath(routeKey)
    if (path) {
      return path
    }
  }
  return routeKeys.has('dashboard') ? '/dashboard' : null
}

function flattenMenuTree(menus: MenuItem[]) {
  const result: MenuItem[] = []
  const walk = (items: MenuItem[]) => {
    for (const item of items) {
      result.push(item)
      if (item.children?.length) {
        walk(item.children)
      }
    }
  }
  walk(menus)
  return result
}