import type { Router } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { preferences } from '@vben/preferences';
import { useAccessStore, useUserStore } from '@vben/stores';
import { startProgress, stopProgress } from '@vben/utils';

import {
  accessRoutes,
  coreRouteNames,
  isPublicRouteName,
} from '#/router/routes';
import { useAuthStore } from '#/store';

import { generateAccess } from './access';

/**
 * 通用守卫配置
 * @param router
 */
function setupCommonGuard(router: Router) {
  // 记录已经加载的页面
  const loadedPaths = new Set<string>();

  router.beforeEach((to) => {
    to.meta.loaded = loadedPaths.has(to.path);

    // 页面加载进度条
    if (!to.meta.loaded && preferences.transition.progress) {
      startProgress();
    }
    return true;
  });

  router.afterEach((to) => {
    // 记录页面是否加载,如果已经加载，后续的页面切换动画等效果不在重复执行

    loadedPaths.add(to.path);

    // 关闭页面加载进度条
    if (preferences.transition.progress) {
      stopProgress();
    }
  });
}

/**
 * 权限访问守卫配置
 * @param router
 */
function setupAccessGuard(router: Router) {
  router.beforeEach(async (to, from) => {
    const accessStore = useAccessStore();
    const userStore = useUserStore();
    const authStore = useAuthStore();

    // 强制密码修改拦截
    if (
      accessStore.accessToken &&
      authStore.passwordChangeRequired &&
      !to.meta.ignorePasswordChange &&
      to.path !== '/account/profile'
    ) {
      return {
        path: '/account/profile',
        query: {
          forcePasswordChange: 'true',
        },
        replace: true,
      };
    }

    // 静态核心路由仍可能需要登录。只有认证相关页面属于公开路由。
    const isCoreRoute = coreRouteNames.includes(to.name as string);
    const isPublicRoute = isPublicRouteName(to.name);
    if (to.path === LOGIN_PATH && accessStore.accessToken) {
      return decodeURIComponent(
        (to.query?.redirect as string) ||
          userStore.userInfo?.homePath ||
          preferences.app.defaultHomePath,
      );
    }

    // accessToken 检查
    if (!accessStore.accessToken) {
      if (isPublicRoute) {
        return true;
      }

      // 没有访问权限，跳转登录页面
      if (to.fullPath !== LOGIN_PATH) {
        return {
          path: LOGIN_PATH,
          // 如不需要，直接删除 query
          query:
            to.fullPath === preferences.app.defaultHomePath
              ? {}
              : { redirect: encodeURIComponent(to.fullPath) },
          // 携带当前跳转的页面，登录后重新跳转该页面
          replace: true,
        };
      }
      return to;
    }

    // 登录后首次刷新到隐藏核心路由时仍需生成动态菜单，否则侧边栏为空。
    if (isCoreRoute && accessStore.isAccessChecked) {
      return true;
    }

    // 是否已经生成过动态路由
    if (accessStore.isAccessChecked) {
      return true;
    }

    // 生成路由表
    // 当前登录用户拥有的角色标识列表
    let userInfo = userStore.userInfo;
    if (!userInfo) {
      try {
        userInfo = await authStore.fetchUserInfo();
      } catch {
        // fetchUserInfo 失败时（如 token 失效），拦截器已触发 doReAuthenticate
        // 此处阻止错误继续传播，避免 Vue Router 因未捕获异常而崩溃
        return {
          path: LOGIN_PATH,
          replace: true,
        };
      }
    }
    const userRoles = userInfo.roles ?? [];

    // 生成菜单和路由
    const { accessibleMenus, accessibleRoutes } = await generateAccess({
      roles: userRoles,
      router,
      // 则会在菜单中显示，但是访问会被重定向到403
      routes: accessRoutes,
    });

    // 保存菜单信息和路由信息
    accessStore.setAccessMenus(accessibleMenus);
    accessStore.setAccessRoutes(accessibleRoutes);
    accessStore.setIsAccessChecked(true);
    const redirectPath = (from.query.redirect ??
      (to.path === preferences.app.defaultHomePath
        ? userInfo.homePath || preferences.app.defaultHomePath
        : to.fullPath)) as string;

    return {
      ...router.resolve(decodeURIComponent(redirectPath)),
      replace: true,
    };
  });
}

/**
 * 项目守卫配置
 * @param router
 */
function createRouterGuard(router: Router) {
  /** 通用 */
  setupCommonGuard(router);
  /** 权限访问 */
  setupAccessGuard(router);
}

export { createRouterGuard };
