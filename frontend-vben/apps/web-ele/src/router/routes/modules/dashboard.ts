import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:layout-dashboard',
      order: -1,
      title: $t('page.dashboard.title'),
    },
    name: 'Dashboard',
    path: '/dashboard',
    children: [
      {
        name: '平台概览',
        path: '',
        component: () => import('#/views/dashboard/platform/index.vue'),
        meta: {
          affixTab: true,
          icon: 'lucide:area-chart',
          title: '平台概览',
        },
      },
    ],
  },
];

export default routes;
