import { createRouter, createWebHistory, type RouteRecordName, type RouteRecordRaw } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { MenuItem, PermissionSnapshot } from '@/types/auth-models'
import { useAuthStore } from '@/stores/auth'
import { APP_ROUTE_MANIFESTS, type AppRouteManifest } from '@/app/registry/module-manifest'
import { isAllowedRoute, resolveFirstAllowedPath } from './route-access'

const CONSOLE_SHELL_ROUTE_NAME = 'console-shell'

const PUBLIC_ROUTES: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/reset-password',
    name: 'reset-password',
    component: () => import('@/views/auth/ResetPasswordView.vue'),
    meta: { public: true, title: '重置密码' },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { public: true, title: '注册' },
  },
]

const SHELL_ROUTE: RouteRecordRaw = {
  path: '/',
  name: CONSOLE_SHELL_ROUTE_NAME,
  component: () => import('@/layouts/ConsoleLayout.vue'),
  children: [
    {
      path: '',
      name: 'console-home',
      redirect: '/dashboard',
      meta: { hidden: true },
    },
    {
      path: 'account/profile',
      name: 'account-profile',
      component: () => import('@/views/account/AccountProfileView.vue'),
      meta: { title: '个人中心', allowPasswordChangeRequired: true, skipMenuAccess: true },
    },
    {
      path: 'notices/:id',
      name: 'notice-detail',
      component: () => import('@/views/system/NoticeDetailView.vue'),
      meta: { title: '公告详情', skipMenuAccess: true },
    },
  ],
}

const FALLBACK_ROUTE: RouteRecordRaw = {
  path: '/:pathMatch(.*)*',
  name: 'not-found',
  component: () => import('@/views/NotFoundView.vue'),
  meta: { title: '页面未找到' },
}

const router = createRouter({
  history: createWebHistory(),
  routes: [...PUBLIC_ROUTES, SHELL_ROUTE, FALLBACK_ROUTE],
  scrollBehavior() {
    return { top: 0, left: 0 }
  },
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()

  if (to.meta.public) {
    return true
  }

  if (!authStore.authenticated) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (authStore.passwordChangeRequired) {
    if (to.meta.allowPasswordChangeRequired) {
      return true
    }
    return { path: '/account/profile', replace: true }
  }

  try {
    if (!authStore.snapshot) {
      await authStore.bootstrapSnapshot()
    }
  } catch (error) {
    const status = axios.isAxiosError(error) ? error.response?.status : undefined
    if (status === 401) {
      authStore.clearSession()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
    ElMessage.error('会话引导失败，请重试')
    return false
  }

  if (to.name === 'not-found') {
    const resolvedRoute = router.resolve(to.fullPath)
    if (resolvedRoute.name !== 'not-found') {
      return { path: to.path, query: to.query, hash: to.hash, replace: true }
    }
    return true
  }

  if (!isAllowedRoute(authStore.snapshot, to)) {
    const fallbackPath = resolveFirstAllowedPath(authStore.snapshot)
    if (fallbackPath && fallbackPath !== to.path) {
      return fallbackPath
    }
    ElMessage.error('当前账号暂无该页面访问权限')
    return false
  }

  return true
})

router.afterEach((to) => {
  document.title = `${String(to.meta.title ?? 'Console')} | Enterprise Auth Platform`
})

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
