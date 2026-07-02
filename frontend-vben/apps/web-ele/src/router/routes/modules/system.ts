import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    name: 'System',
    path: '/system',
    meta: {
      icon: 'lucide:shield-check',
      title: '系统管理',
    },
    redirect: '/system/overview',
    children: [
      {
        name: 'system-overview',
        path: 'overview',
        component: () => import('#/views/system/SystemManagementView.vue'),
        meta: { title: '系统设置' },
      },
      {
        name: 'system-security',
        path: 'security',
        component: () => import('#/views/system/SecurityPolicyView.vue'),
        meta: { title: '安全策略' },
      },
      {
        name: 'system-users',
        path: 'users',
        component: () => import('#/views/upms/user/index.vue'),
        meta: { title: '用户管理' },
      },
      {
        name: 'system-roles',
        path: 'roles',
        component: () => import('#/views/upms/role/index.vue'),
        meta: { title: '角色管理' },
      },
      {
        name: 'system-depts',
        path: 'depts',
        component: () => import('#/views/upms/dept/index.vue'),
        meta: { title: '部门管理' },
      },
      {
        name: 'system-online-users',
        path: 'online-users',
        component: () => import('#/views/upms/online-user/index.vue'),
        meta: { title: '在线用户' },
      },
      {
        name: 'system-menus',
        path: 'menus',
        component: () => import('#/views/upms/menu/index.vue'),
        meta: { title: '菜单管理' },
      },
      {
        name: 'system-logs-operation',
        path: 'logs/operation',
        component: () => import('#/views/upms/log/index.vue'),
        meta: { title: '操作日志' },
      },
      {
        name: 'system-logs-login',
        path: 'logs/login',
        component: () => import('#/views/upms/login-log/index.vue'),
        meta: { title: '登录日志' },
      },
      {
        name: 'system-mail-channel',
        path: 'mail-channel',
        component: () => import('#/views/upms/mail-channel/index.vue'),
        meta: { title: '邮件配置' },
      },
    ],
  },
];

export default routes;
