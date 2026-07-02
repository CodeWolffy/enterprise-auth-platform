import type { RouteRecordRaw } from 'vue-router';

const WorkflowLayout = () => import('#/views/workflow/index.vue');

const routes: RouteRecordRaw[] = [
  {
    name: 'workflow',
    path: '/workflow',
    component: WorkflowLayout,
    meta: {
      icon: 'lucide:git-branch',
      title: '流程管理',
    },
    redirect: '/workflow/definitions',
    children: [
      {
        name: 'workflow-definitions',
        path: 'definitions',
        component: () => import('#/views/workflow/definitions.vue'),
        meta: {
          title: '流程定义',
        },
      },
      {
        name: 'workflow-designer',
        path: 'designer',
        component: () => import('#/views/workflow/designer.vue'),
        meta: {
          title: '流程设计器',
        },
      },
      {
        name: 'workflow-todo',
        path: 'todo',
        component: () => import('#/views/workflow/todo.vue'),
        meta: {
          title: '我的待办',
        },
      },
      {
        name: 'workflow-done',
        path: 'done',
        component: () => import('#/views/workflow/done.vue'),
        meta: {
          title: '我的已办',
        },
      },
      {
        name: 'workflow-instances',
        path: 'instances',
        component: () => import('#/views/workflow/instances.vue'),
        meta: {
          title: '我的发起',
        },
      },
    ],
  },
];

export default routes;
