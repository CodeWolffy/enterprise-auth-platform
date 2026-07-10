import type { RouteRecordName, RouteRecordRaw } from 'vue-router';

import { traverseTreeValues } from '@vben/utils';

import { coreRoutes, fallbackNotFoundRoute, publicRouteNames } from './core';
import dashboardRoutes from './modules/dashboard';
import platformRoutes from './modules/platform';
import systemRoutes from './modules/system';
import workflowRoutes from './modules/workflow';

const staticRoutes: RouteRecordRaw[] = [
  ...dashboardRoutes,
  ...workflowRoutes,
  ...systemRoutes,
  ...platformRoutes,
];
const externalRoutes: RouteRecordRaw[] = [];

/** 路由列表，由基本路由、外部路由和404兜底路由组成
 *  无需走权限验证（会一直显示在菜单中） */
const routes: RouteRecordRaw[] = [
  ...coreRoutes,
  ...externalRoutes,
  fallbackNotFoundRoute,
];

/** 基本路由列表，这些路由不需要进入权限拦截 */
const coreRouteNames = traverseTreeValues(coreRoutes, (route) => route.name);
const publicRouteNameSet = new Set<RouteRecordName>(publicRouteNames);

function isPublicRouteName(name: null | RouteRecordName | undefined) {
  return name !== null && name !== undefined && publicRouteNameSet.has(name);
}

/** 有权限校验的路由列表：当前项目以后端菜单为唯一来源 */
const accessRoutes = staticRoutes;
export {
  accessRoutes,
  coreRouteNames,
  isPublicRouteName,
  publicRouteNames,
  routes,
};
