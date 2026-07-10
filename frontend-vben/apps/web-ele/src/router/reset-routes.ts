import type { Router } from 'vue-router';

import { resetStaticRoutes } from '@vben/utils';

import { routes } from './routes';

function resetRoutes(router: Router) {
  resetStaticRoutes(router, routes);
}

export { resetRoutes };
