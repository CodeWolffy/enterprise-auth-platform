import type { RouteLocationNormalized } from 'vue-router'
import { resolveRouteManifest, resolveRouteManifestByPath } from '@/app/registry/module-manifest'
import type { MenuItem, PermissionSnapshot } from '@/types/auth-models'

interface RouteAccessTarget {
  meta: RouteLocationNormalized['meta']
  name?: RouteLocationNormalized['name'] | null
  path: string
}

export function collectAllowedRoutePaths(menus: MenuItem[]) {
  const paths = new Set<string>()
  const walk = (items: MenuItem[]) => {
    for (const item of items) {
      const path = normalizeRoutePath(item.path)
      if (path) {
        paths.add(path)
      }
      if (item.children?.length) {
        walk(item.children)
      }
    }
  }
  walk(menus)
  return paths
}

export function isAllowedRoute(snapshot: PermissionSnapshot | null, to: RouteAccessTarget) {
  if (!snapshot) {
    return false
  }

  const requiredGrant = String(to.meta.requiresGrant ?? '').trim()
  if (requiredGrant && !snapshot.superAdmin && !(snapshot.grants ?? []).includes(requiredGrant)) {
    return false
  }

  if (to.meta.skipMenuAccess) {
    return hasRequiredGrant(snapshot, requiredGrant)
  }

  if (to.meta.generatedRoute) {
    return isAllowedGeneratedRoute(snapshot, to.path)
  }

  const routePath = normalizeRoutePath(to.path)
  if (!routePath) {
    return true
  }

  const menu = findMenuByPath(snapshot.menus ?? [], routePath)
  return Boolean(menu) && hasRequiredGrant(snapshot, requiredGrant) && hasRequiredGrant(snapshot, resolveMenuRequiredGrant(menu))
}

function isAllowedGeneratedRoute(snapshot: PermissionSnapshot, path: string) {
  const routePath = normalizeRoutePath(path)
  if (!routePath) {
    return false
  }
  return Boolean(findMenuByPath(snapshot.menus ?? [], routePath)) && hasRequiredGrant(snapshot, generatedPageGrant(routePath))
}

export function isAllowedMenuPath(snapshot: PermissionSnapshot | null, menu: MenuItem) {
  if (!snapshot) {
    return false
  }
  const path = normalizeRoutePath(menu.path)
  if (!path) {
    return false
  }
  return Boolean(findMenuByPath(snapshot.menus ?? [], path)) && hasRequiredGrant(snapshot, resolveMenuRequiredGrant(menu))
}

export function resolveFirstAllowedPath(snapshot: PermissionSnapshot | null) {
  if (!snapshot) {
    return null
  }

  const menuList = flattenMenuTree(snapshot.menus ?? [])
  for (const menu of menuList) {
    const path = normalizeRoutePath(menu.path)
    if (!path) {
      continue
    }
    const requiredGrant = resolveMenuRequiredGrant(menu)
    if (!hasRequiredGrant(snapshot, requiredGrant)) {
      continue
    }
    return path
  }
  return null
}

function resolveMenuRequiredGrant(menu: MenuItem) {
  const path = normalizeRoutePath(menu.path)
  return generatedPageGrant(path)
    ?? resolveRouteManifestByPath(path)?.requiredGrant?.trim()
    ?? resolveRouteManifest(menu.component?.trim() || menu.permission?.trim() || menu.code?.trim() || '')?.requiredGrant?.trim()
    ?? ''
}

function findMenuByPath(menus: MenuItem[], targetPath: string) {
  const normalizedTarget = normalizeRoutePath(targetPath)
  const walk = (items: MenuItem[]): MenuItem | null => {
    for (const item of items) {
      if (normalizeRoutePath(item.path) === normalizedTarget) {
        return item
      }
      const child = walk(item.children ?? [])
      if (child) {
        return child
      }
    }
    return null
  }
  return walk(menus)
}

function generatedPageGrant(path?: string | null) {
  const normalizedPath = normalizeRoutePath(path)
  const match = normalizedPath.match(/^\/platform\/generated\/([^/]+)$/)
  return match ? `${decodeURIComponent(match[1])}:page` : undefined
}

function hasRequiredGrant(snapshot: PermissionSnapshot, requiredGrant?: string | null) {
  const normalizedGrant = requiredGrant?.trim()
  return !normalizedGrant || snapshot.superAdmin || (snapshot.grants ?? []).includes(normalizedGrant)
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

function normalizeRoutePath(path?: string | null) {
  const normalized = path?.trim()
  if (!normalized) {
    return ''
  }
  return normalized.startsWith('/') ? normalized : `/${normalized}`
}