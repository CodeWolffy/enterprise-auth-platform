import type { RouteLocationNormalized } from 'vue-router'
import { resolveRouteManifest } from '@/app/registry/module-manifest'
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

  if (to.meta.generatedRoute) {
    return isAllowedGeneratedRoute(snapshot, to.path)
  }

  const routePath = normalizeRoutePath(to.path)
  if (!routePath) {
    return true
  }

  const allowedPaths = collectAllowedRoutePaths(snapshot.menus ?? [])
  return allowedPaths.has(routePath) && hasRequiredGrant(snapshot, requiredGrant)
}

function isAllowedGeneratedRoute(snapshot: PermissionSnapshot, path: string) {
  const routePath = normalizeRoutePath(path)
  if (!routePath) {
    return false
  }
  return collectAllowedRoutePaths(snapshot.menus ?? []).has(routePath)
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
    const requiredGrant = resolveRouteManifest(menu.component?.trim() || menu.permission?.trim() || menu.code?.trim() || '')?.requiredGrant?.trim() ?? ''
    if (!hasRequiredGrant(snapshot, requiredGrant)) {
      continue
    }
    return path
  }
  return null
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