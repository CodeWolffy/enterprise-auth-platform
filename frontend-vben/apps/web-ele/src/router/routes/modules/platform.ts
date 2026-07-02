import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    name: 'Platform',
    path: '/platform',
    meta: {
      icon: 'lucide:layout-grid',
      title: '平台管理',
    },
    redirect: '/platform/tenants',
    children: [
      {
        name: 'platform-tenants',
        path: 'tenants',
        component: () =>
          import('#/views/upms/tenant/index.vue'),
        meta: { title: '租户管理' },
      },
      {
        name: 'platform-tenant-catalog',
        path: 'tenant-catalog',
        component: () =>
          import('#/views/system/tenant-catalog/index.vue'),
        meta: { title: '租户套餐' },
      },
      {
        name: 'platform-files',
        path: 'files',
        component: () =>
          import('#/views/upms/file/index.vue'),
        meta: { title: '文件管理' },
      },
      {
        name: 'platform-dicts',
        path: 'dicts',
        component: () =>
          import('#/views/upms/dict/index.vue'),
        meta: { title: '字典管理' },
      },
      {
        name: 'platform-configs',
        path: 'configs',
        component: () =>
          import('#/views/upms/config/index.vue'),
        meta: { title: '参数管理' },
      },
      {
        name: 'platform-mail-channel',
        path: 'mail-channel',
        component: () =>
          import('#/views/upms/mail-channel/index.vue'),
        meta: { title: '邮件配置' },
      },
      {
        name: 'platform-notices',
        path: 'notices',
        component: () =>
          import('#/views/upms/notice/index.vue'),
        meta: { title: '公告管理' },
      },
      {
        name: 'platform-categories',
        path: 'categories',
        component: () =>
          import('#/views/upms/category/index.vue'),
        meta: { title: '分类配置' },
      },
      {
        name: 'platform-codegen',
        path: 'codegen',
        redirect: '/platform/codegen/gen-table',
        meta: { title: '代码生成', icon: 'carbon:ibm-cloud-code-engine' },
        children: [
          {
            name: 'platform-codegen-datasource',
            path: 'datasource',
            component: () =>
              import('#/views/gen/datasource/index.vue'),
            meta: { title: '数据源管理', icon: 'carbon:database' },
          },
          {
            name: 'platform-codegen-gen-table',
            path: 'gen-table',
            component: () =>
              import('#/views/gen/gen-table/index.vue'),
            meta: { title: '数据表管理', icon: 'carbon:data-table' },
          },
          {
            name: 'platform-codegen-template',
            path: 'template',
            component: () =>
              import('#/views/gen/gen-template/index.vue'),
            meta: { title: '自定义模板' },
          },
          {
            name: 'platform-codegen-generate',
            path: 'generate',
            component: () =>
              import('#/views/gen/gen-table/generate.vue'),
            meta: {
              title: '生成代码',
              hideInMenu: true,
              ignoreAccess: true,
            },
          },
        ],
      },
    ],
  },
];

export default routes;