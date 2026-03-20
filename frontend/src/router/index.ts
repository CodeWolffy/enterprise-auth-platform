import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, title: '统一登录' },
    },
    {
      path: '/auth/callback',
      name: 'callback',
      component: () => import('@/views/AuthCallbackView.vue'),
      meta: { public: true, title: '登录回调' },
    },
    {
      path: '/',
      component: () => import('@/layouts/ConsoleLayout.vue'),
      children: [
        { path: '', redirect: '/dashboard' },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '运行总览' },
        },
        {
          path: 'oauth-clients',
          name: 'oauth-clients',
          component: () => import('@/views/OAuthClientsView.vue'),
          meta: { title: 'OAuth2 客户端' },
        },
        {
          path: 'system/users',
          name: 'users',
          component: () => import('@/views/UsersView.vue'),
          meta: { title: '用户管理' },
        },
        {
          path: 'system/roles',
          name: 'roles',
          component: () => import('@/views/RolesView.vue'),
          meta: { title: '角色管理' },
        },
        {
          path: 'system/permissions',
          name: 'permissions',
          component: () => import('@/views/PermissionsView.vue'),
          meta: { title: '权限管理' },
        },
        {
          path: 'system/depts',
          name: 'depts',
          component: () => import('@/views/DepartmentsView.vue'),
          meta: { title: '部门管理' },
        },
        {
          path: 'system/tenants',
          name: 'tenants',
          component: () => import('@/views/TenantsView.vue'),
          meta: { title: '租户管理' },
        },
        {
          path: 'system/audit',
          name: 'audit',
          component: () => import('@/views/AuditView.vue'),
          meta: { title: '安全审计' },
        },
        {
          path: 'system/settings',
          name: 'settings',
          component: () => import('@/views/SystemManagementView.vue'),
          meta: { title: '系统管理' },
        },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  if (to.meta.public) {
    return true
  }
  if (!authStore.accessToken) {
    return { name: 'login' }
  }
  if (!authStore.snapshot) {
    try {
      await authStore.bootstrapSnapshot()
    } catch {
      ElMessage.error('登录态已失效，请重新登录')
      authStore.clearSession()
      return { name: 'login' }
    }
  }
  return true
})

router.afterEach((to) => {
  document.title = `${String(to.meta.title ?? '控制台')} | 企业级权限管理平台`
})

export default router
