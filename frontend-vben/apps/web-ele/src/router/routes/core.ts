import type { RouteRecordRaw } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { preferences } from '@vben/preferences';

import { $t } from '#/locales';

const BasicLayout = () => import('#/layouts/basic.vue');
const AuthPageLayout = () => import('#/layouts/auth.vue');
/** 全局404页面 */
const fallbackNotFoundRoute: RouteRecordRaw = {
  component: () => import('#/views/_core/fallback/not-found.vue'),
  meta: {
    hideInBreadcrumb: true,
    hideInMenu: true,
    hideInTab: true,
    title: '404',
  },
  name: 'FallbackNotFound',
  path: '/:path(.*)*',
};

/** 基本路由，这些路由是必须存在的 */
const coreRoutes: RouteRecordRaw[] = [
  {
    component: BasicLayout,
    meta: {
      hideInBreadcrumb: true,
      title: 'Root',
    },
    name: 'Root',
    path: '/',
    redirect: preferences.app.defaultHomePath,
    children: [],
  },
  {
    meta: {
      hideInBreadcrumb: true,
      hideInMenu: true,
      hideInTab: true,
      title: 'LegacyAnalyticsRedirect',
    },
    name: 'LegacyAnalyticsRedirect',
    path: '/analytics',
    redirect: preferences.app.defaultHomePath,
  },
  {
    component: AuthPageLayout,
    meta: {
      hideInTab: true,
      title: 'Authentication',
    },
    name: 'Authentication',
    path: '/auth',
    redirect: LOGIN_PATH,
    children: [
      {
        name: 'Login',
        path: 'login',
        component: () => import('#/views/_core/authentication/login.vue'),
        meta: {
          title: $t('page.auth.login'),
        },
      },
    ],
  },
  {
    component: BasicLayout,
    meta: {
      hideInBreadcrumb: true,
      hideInMenu: true,
      hideInTab: true,
      title: 'CodegenGenerate',
    },
    name: 'CodegenGeneratePage',
    path: '/platform/codegen/generate',
    children: [
      {
        name: 'CodegenGenerate',
        path: '',
        component: () => import('#/views/gen/gen-table/generate.vue'),
        meta: {
          title: '生成代码',
          hideInMenu: true,
          ignoreAccess: true,
        },
      },
    ],
  },
  {
    component: BasicLayout,
    meta: {
      hideInBreadcrumb: true,
      hideInMenu: true,
      title: 'Notice',
    },
    name: 'Notice',
    path: '/platform/notice-detail',
    children: [
      {
        name: 'NoticeDetail',
        path: ':id',
        component: () => import('#/views/system/NoticeDetailView.vue'),
        meta: {
          title: '公告详情',
          ignoreAccess: true,
        },
      },
    ],
  },
  {
    component: BasicLayout,
    meta: {
      hideInBreadcrumb: true,
      hideInMenu: true,
      title: 'Account',
    },
    name: 'Account',
    path: '/account',
    children: [
      {
        name: 'AccountProfile',
        path: 'profile',
        component: () => import('#/views/account/AccountProfileView.vue'),
        meta: {
          title: '个人中心',
          allowPasswordChangeRequired: true,
          ignoreAccess: true,
        },
      },
    ],
  },
  {
    component: AuthPageLayout,
    meta: {
      hideInTab: true,
      title: 'Register',
    },
    name: 'RegisterPage',
    path: '/register',
    children: [
      {
        name: 'Register',
        path: '',
        component: () => import('#/views/auth/RegisterView.vue'),
        meta: {
          title: '注册',
        },
      },
    ],
  },
  {
    component: AuthPageLayout,
    meta: {
      hideInTab: true,
      title: 'ResetPassword',
    },
    name: 'ResetPasswordPage',
    path: '/reset-password',
    children: [
      {
        name: 'ResetPassword',
        path: '',
        component: () => import('#/views/auth/ResetPasswordView.vue'),
        meta: {
          title: '重置密码',
        },
      },
    ],
  },
];

export { coreRoutes, fallbackNotFoundRoute };
