import type { RouteRecordRaw } from 'vue-router'

export const CONSOLE_SHELL_ROUTE_NAME = 'console-shell'

export const publicRoutes: RouteRecordRaw[] = [
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

export const shellRoute: RouteRecordRaw = {
  path: '/',
  name: CONSOLE_SHELL_ROUTE_NAME,
  component: () => import('@/layouts/basic.vue'),
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

export const fallbackRoute: RouteRecordRaw = {
  path: '/:pathMatch(.*)*',
  name: 'not-found',
  component: () => import('@/views/NotFoundView.vue'),
  meta: { title: '页面未找到' },
}

export const coreRoutes: RouteRecordRaw[] = [...publicRoutes, shellRoute, fallbackRoute]
