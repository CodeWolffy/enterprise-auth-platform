import type { RouteRecordRaw } from 'vue-router';

import { describe, expect, it, vi } from 'vitest';

vi.mock('@vben/utils', async () => {
  const actual = await vi.importActual<typeof import('@vben/utils')>('@vben/utils');
  return {
    ...actual,
    generateMenus: vi.fn((routes: RouteRecordRaw[]) =>
      routes.map((route) => ({
        children: [],
        name: route.meta?.title || route.name || '',
        path: route.path || '',
        show: true,
      })),
    ),
    generateRoutesByBackend: vi.fn(async () => [
      {
        name: 'backend-root',
        path: '/backend',
        meta: { title: '后端菜单' },
      },
    ]),
    generateRoutesByFrontend: vi.fn(async (routes: RouteRecordRaw[]) => routes),
  };
});

import { generateAccessible } from './accessible';

describe('generateAccessible', () => {
  it('backend mode should only use backend routes and not append static routes', async () => {
    const router = {
      addRoute: vi.fn(),
      getRoutes: vi.fn(() => [
        {
          children: [],
          name: 'Root',
          path: '/',
        },
      ]),
      removeRoute: vi.fn(),
    } as any;

    const options = {
      router,
      routes: [
        {
          name: 'static-root',
          path: '/static',
          meta: { title: '静态模块' },
        },
      ] as RouteRecordRaw[],
      fetchMenuListAsync: vi.fn(),
    };

    const result = await generateAccessible('backend', options as any);

    expect(result.accessibleRoutes).toEqual([
      {
        name: 'backend-root',
        path: '/backend',
        meta: { title: '后端菜单' },
      },
      {
        name: 'static-root',
        path: '/static',
        meta: { title: '静态模块' },
      },
    ]);
    expect(result.accessibleMenus).toEqual([
      {
        children: [],
        name: '后端菜单',
        path: '/backend',
        show: true,
      },
    ]);
  });
});
