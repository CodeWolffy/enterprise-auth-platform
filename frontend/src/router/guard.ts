import axios from 'axios'
import type { Router } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { isAllowedRoute, resolveFirstAllowedPath } from './route-access'

export function setupRouterGuards(router: Router) {
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

    let snapshotBootstrapped = false
    try {
      if (!authStore.snapshot) {
        await authStore.bootstrapSnapshot()
        snapshotBootstrapped = true
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

    if (snapshotBootstrapped) {
      const resolvedRoute = router.resolve(to.fullPath)
      const routeShapeChanged =
        resolvedRoute.name !== to.name ||
        resolvedRoute.matched.length !== to.matched.length

      if (routeShapeChanged) {
        return {
          path: to.path,
          query: to.query,
          hash: to.hash,
          replace: true,
        }
      }
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
}
