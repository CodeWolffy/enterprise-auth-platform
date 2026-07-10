import { createMemoryHistory, createRouter } from 'vue-router';

import { describe, expect, it } from 'vitest';

import { resetRoutes } from '../reset-routes';
import { coreRouteNames, isPublicRouteName, routes } from '../routes';

describe('router access policy', () => {
  it('keeps authenticated core pages out of the public route set', () => {
    expect(coreRouteNames).toContain('AccountProfile');
    expect(coreRouteNames).toContain('CodegenGenerate');
    expect(isPublicRouteName('AccountProfile')).toBe(false);
    expect(isPublicRouteName('CodegenGenerate')).toBe(false);
    expect(isPublicRouteName('Login')).toBe(true);
    expect(isPublicRouteName('Register')).toBe(true);
    expect(isPublicRouteName('ResetPassword')).toBe(true);
  });

  it('removes dynamic routes while preserving static routes', () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes,
    });
    router.addRoute({
      component: { template: '<div />' },
      name: 'PreviousAccountRoute',
      path: '/previous-account-route',
    });

    expect(router.hasRoute('PreviousAccountRoute')).toBe(true);
    resetRoutes(router);

    expect(router.hasRoute('PreviousAccountRoute')).toBe(false);
    expect(router.hasRoute('Login')).toBe(true);
    expect(router.hasRoute('AccountProfile')).toBe(true);
  });
});
