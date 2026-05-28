import { createRouter, createWebHistory, type RouteRecordName, type RouteRecordRaw } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { PermissionSnapshot } from '@/types/auth'
import { useAuthStore } from '@/stores/auth'
import { APP_ROUTE_MANIFESTS, type AppRouteManifest } from '@/app/registry/module-manifest'
import { collectAllowedRouteKeys, isAllowedRoute, resolveFirstAllowedPath } from './route-access'

const CONSOLE_SHELL_ROUTE_NAME = 'console-shell'

const PUBLIC_ROUTES: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { public: true, title: '登录' },
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

  const allowedRouteKeys = collectAllowedRouteKeys(snapshot.menus ?? [])
  for (const manifest of APP_ROUTE_MANIFESTS) {
    if (!canRegisterRoute(manifest, snapshot, allowedRouteKeys)) {
      continue
    }
    router.addRoute(CONSOLE_SHELL_ROUTE_NAME, toRouteRecord(manifest))
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
  allowedRouteKeys: Set<string>,
) {
  if (manifest.routeKey) {
    return allowedRouteKeys.has(manifest.routeKey)
  }
  const requiredGrant = manifest.requiredGrant?.trim()
  return !requiredGrant || snapshot.superAdmin || (snapshot.grants ?? []).includes(requiredGrant)
}

function toRouteRecord(manifest: AppRouteManifest): RouteRecordRaw {
  return {
    path: manifest.path,
    name: manifest.name,
    component: manifest.component,
    meta: {
      title: manifest.title,
      routeKey: manifest.routeKey,
      requiresGrant: manifest.requiredGrant,
      hidden: manifest.hidden,
      icon: manifest.icon,
    },
  }
}

export { registerDynamicRoutes, clearDynamicRoutes }
export default router
