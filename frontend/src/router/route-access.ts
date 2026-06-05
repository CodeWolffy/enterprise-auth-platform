import type { RouteLocationNormalized } from 'vue-router'
import { ROUTE_KEY_PATH_MAP } from '@/app/registry/module-manifest'
import type { MenuItem, PermissionSnapshot } from '@/types/auth-models'

interface RouteAccessTarget {
  meta: RouteLocationNormalized['meta']
  name?: RouteLocationNormalized['name'] | null
  path: string
}

const ROUTE_KEYS = new Set(Object.keys(ROUTE_KEY_PATH_MAP))
const GENERATED_ROUTE_KEY_PREFIX = 'generated.'

export function resolveRoutePath(routeKey?: string | null) {
  if (!routeKey) {
    return ''
  }
  const normalizedRouteKey = routeKey.trim()
  if (normalizedRouteKey.startsWith(GENERATED_ROUTE_KEY_PREFIX)) {
    const moduleName = normalizedRouteKey.slice(GENERATED_ROUTE_KEY_PREFIX.length).trim()
    return moduleName ? `/platform/generated/${encodeURIComponent(moduleName)}` : ''
  }
  return ROUTE_KEY_PATH_MAP[normalizedRouteKey] ?? ''
}

export function collectAllowedRouteKeys(menus: MenuItem[]) {
  const routeKeys = new Set<string>()
  const walk = (items: MenuItem[]) => {
    for (const item of items) {
      const routeKey = item.routeKey?.trim()
      if (routeKey) {
        if (ROUTE_KEYS.has(routeKey) || routeKey.startsWith(GENERATED_ROUTE_KEY_PREFIX)) {
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

  if (Boolean(to.meta.generatedRoute)) {
    return isAllowedGeneratedRoute(snapshot, to.path)
  }

  const routeKey = String(to.meta.routeKey ?? '').trim()
  if (!routeKey) {
    return true
  }

  const routeKeys = collectAllowedRouteKeys(snapshot.menus ?? [])
  return routeKeys.has(routeKey)
}

function isAllowedGeneratedRoute(snapshot: PermissionSnapshot, path: string) {
  const moduleName = resolveGeneratedModuleName(path)
  if (!moduleName) {
    return false
  }
  return collectAllowedRouteKeys(snapshot.menus ?? []).has(`${GENERATED_ROUTE_KEY_PREFIX}${moduleName}`)
}

function resolveGeneratedModuleName(path: string) {
  const prefix = '/platform/generated/'
  if (!path.startsWith(prefix)) {
    return ''
  }
  const segment = path.slice(prefix.length).split('/')[0]?.trim()
  if (!segment) {
    return ''
  }
  try {
    return decodeURIComponent(segment)
  } catch {
    return segment
  }
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