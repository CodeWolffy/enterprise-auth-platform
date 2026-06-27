import { createRouter, createWebHistory, type RouteRecordName, type RouteRecordRaw } from 'vue-router'
import type { MenuItem, PermissionSnapshot } from '@/types/auth-models'
import { APP_ROUTE_MANIFESTS, type AppRouteManifest } from '@/app/registry/module-manifest'
import { setupRouterGuards } from './guard'
import { CONSOLE_SHELL_ROUTE_NAME, coreRoutes } from './routes'

const router = createRouter({
  history: createWebHistory(),
  routes: coreRoutes,
  scrollBehavior() {
    return { top: 0, left: 0 }
  },
})
setupRouterGuards(router)

const dynamicRouteNames = new Set<RouteRecordName>()

function registerDynamicRoutes(snapshot?: PermissionSnapshot | null) {
  clearDynamicRoutes()
  if (!snapshot) {
    return
  }

  const menuByPath = collectMenuByPath(snapshot.menus ?? [])
  const allowedRoutePaths = new Set(menuByPath.keys())
  for (const manifest of APP_ROUTE_MANIFESTS) {
    const menu = resolveMenuForManifest(manifest, menuByPath)
    if (!canRegisterRoute(manifest, snapshot, allowedRoutePaths, Boolean(menu))) {
      continue
    }
    router.addRoute(CONSOLE_SHELL_ROUTE_NAME, toRouteRecord(manifest, menu))
    dynamicRouteNames.add(manifest.name)
  }
}

function clearDynamicRoutes() {
  for (const routeName of dynamicRouteNames) {
    if (router.hasRoute(routeName)) {
      router.removeRoute(routeName)
    }
  }
  dynamicRouteNames.clear()
}

function canRegisterRoute(
  manifest: AppRouteManifest,
  snapshot: PermissionSnapshot,
  allowedRoutePaths: Set<string>,
  hasRuntimeMenu: boolean,
) {
  if (manifest.generatedRoute) {
    return [...allowedRoutePaths].some((path) => path.startsWith('/platform/generated/'))
  }
  const manifestPath = normalizeAbsoluteRoutePath(manifest.path)
  if (manifestPath && allowedRoutePaths.has(manifestPath)) {
    return hasRequiredGrant(snapshot, manifest.requiredGrant)
  }
  if (hasRuntimeMenu) {
    return hasRequiredGrant(snapshot, manifest.requiredGrant)
  }
  const requiredGrant = manifest.requiredGrant?.trim()
  return !requiredGrant || snapshot.superAdmin || (snapshot.grants ?? []).includes(requiredGrant)
}

function hasRequiredGrant(snapshot: PermissionSnapshot, requiredGrant?: string | null) {
  const normalizedGrant = requiredGrant?.trim()
  return !normalizedGrant || snapshot.superAdmin || (snapshot.grants ?? []).includes(normalizedGrant)
}

function collectMenuByPath(menus: MenuItem[]) {
  const result = new Map<string, MenuItem>()
  const walk = (items: MenuItem[]) => {
    for (const item of items) {
      const path = normalizeAbsoluteRoutePath(item.path)
      if (path && !result.has(path)) {
        result.set(path, item)
      }
      if (item.children?.length) {
        walk(item.children)
      }
    }
  }
  walk(menus)
  return result
}

function resolveMenuForManifest(
  manifest: AppRouteManifest,
  menuByPath: Map<string, MenuItem>,
) {
  const manifestPath = normalizeAbsoluteRoutePath(manifest.path)
  if (manifestPath) {
    return menuByPath.get(manifestPath)
  }
  return undefined
}

function normalizeAbsoluteRoutePath(path?: string | null) {
  const normalized = path?.trim()
  if (!normalized) {
    return undefined
  }
  return normalized.startsWith('/') ? normalized : `/${normalized}`
}

function normalizeChildRoutePath(path?: string | null) {
  const normalized = path?.trim()
  if (!normalized) {
    return undefined
  }
  return normalized.startsWith('/') ? normalized.slice(1) : normalized
}

function toRouteRecord(manifest: AppRouteManifest, menu?: MenuItem): RouteRecordRaw {
  return {
    path: normalizeChildRoutePath(menu?.path) ?? manifest.path,
    name: manifest.name,
    component: manifest.component,
    meta: {
      title: menu?.name?.trim() || menu?.title?.trim() || manifest.title,
      routeKey: manifest.routeKey,
      requiresGrant: manifest.requiredGrant,
      hidden: manifest.hidden,
      icon: menu?.icon?.trim() || manifest.icon,
      generatedRoute: manifest.generatedRoute,
    },
  }
}

export { registerDynamicRoutes, clearDynamicRoutes }
export default router
