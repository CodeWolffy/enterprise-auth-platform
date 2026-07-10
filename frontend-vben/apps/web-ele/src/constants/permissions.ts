/**
 * 平台权限码常量表。
 *
 * 与后端 PermissionCodes.java 对齐
 * (src/main/java/com/enterprise/auth/platform/common/authz/PermissionCodes.java)。
 * 新增/调整权限码时请同步维护两侧。
 */
export const PERMS = {
  upms: {
    system: {
      page: 'upms:system:page',
      get: 'upms:system:get',
    },
    dashboard: {
      page: 'upms:dashboard:page',
      get: 'upms:dashboard:get',
    },
    user: {
      page: 'upms:sysuser:page',
      get: 'upms:sysuser:get',
      add: 'upms:sysuser:add',
      edit: 'upms:sysuser:edit',
      del: 'upms:sysuser:del',
    },
    role: {
      page: 'upms:sysrole:page',
      get: 'upms:sysrole:get',
      add: 'upms:sysrole:add',
      edit: 'upms:sysrole:edit',
      del: 'upms:sysrole:del',
    },
    dept: {
      page: 'upms:sysdept:page',
      get: 'upms:sysdept:get',
      add: 'upms:sysdept:add',
      edit: 'upms:sysdept:edit',
      del: 'upms:sysdept:del',
    },
    tenant: {
      page: 'upms:systenant:page',
      get: 'upms:systenant:get',
      add: 'upms:systenant:add',
      edit: 'upms:systenant:edit',
      del: 'upms:systenant:del',
    },
    tenantPackage: {
      page: 'upms:tenantpackage:page',
      get: 'upms:tenantpackage:get',
      add: 'upms:tenantpackage:add',
      edit: 'upms:tenantpackage:edit',
      del: 'upms:tenantpackage:del',
    },
    dict: {
      page: 'upms:sysdict:page',
      get: 'upms:sysdict:get',
      add: 'upms:sysdict:add',
      edit: 'upms:sysdict:edit',
      del: 'upms:sysdict:del',
    },
    config: {
      page: 'upms:sysconfig:page',
      get: 'upms:sysconfig:get',
      add: 'upms:sysconfig:add',
      edit: 'upms:sysconfig:edit',
      del: 'upms:sysconfig:del',
    },
    category: {
      page: 'upms:syscategory:page',
      get: 'upms:syscategory:get',
      add: 'upms:syscategory:add',
      edit: 'upms:syscategory:edit',
      del: 'upms:syscategory:del',
    },
    mail: {
      page: 'upms:sysmail:page',
      get: 'upms:sysmail:get',
      add: 'upms:sysmail:add',
      edit: 'upms:sysmail:edit',
      del: 'upms:sysmail:del',
    },
    notice: {
      page: 'upms:sysnotice:page',
      get: 'upms:sysnotice:get',
      add: 'upms:sysnotice:add',
      edit: 'upms:sysnotice:edit',
      del: 'upms:sysnotice:del',
    },
    security: {
      get: 'upms:security:get',
      edit: 'upms:security:edit',
    },
    workflow: {
      designer: {
        page: 'upms:workflowdesigner:page',
      },
      definition: {
        page: 'upms:workflowdefinition:page',
        get: 'upms:workflowdefinition:get',
        add: 'upms:workflowdefinition:add',
        edit: 'upms:workflowdefinition:edit',
        deploy: 'upms:workflowdefinition:deploy',
      },
      instance: {
        page: 'upms:workflowinstance:page',
        get: 'upms:workflowinstance:get',
        add: 'upms:workflowinstance:add',
        edit: 'upms:workflowinstance:edit',
        del: 'upms:workflowinstance:del',
      },
      todo: {
        page: 'upms:workflowtodo:page',
        get: 'upms:workflowtodo:get',
        edit: 'upms:workflowtodo:edit',
      },
      done: {
        page: 'upms:workflowdone:page',
        get: 'upms:workflowdone:get',
      },
    },
    menu: {
      page: 'upms:sysmenu:page',
      get: 'upms:sysmenu:get',
      add: 'upms:sysmenu:add',
      edit: 'upms:sysmenu:edit',
      del: 'upms:sysmenu:del',
    },
    file: {
      page: 'upms:file:page',
      get: 'upms:file:get',
      add: 'upms:file:add',
      del: 'upms:file:del',
    },
    onlineUser: {
      kick: 'upms:session:kick',
    },
  },
  gen: {
    datasource: {
      page: 'gen:datasource:page',
      get: 'gen:datasource:get',
      add: 'gen:datasource:add',
      edit: 'gen:datasource:edit',
      del: 'gen:datasource:del',
    },
    table: {
      page: 'gen:gen-table:page',
      get: 'gen:gen-table:get',
      add: 'gen:gen-table:add',
      edit: 'gen:gen-table:edit',
      del: 'gen:gen-table:del',
      download: 'gen:gen-table:download',
    },
  },
} as const;
