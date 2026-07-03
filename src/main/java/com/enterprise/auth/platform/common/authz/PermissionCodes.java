package com.enterprise.auth.platform.common.authz;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 平台权限码登记入口。
 */
public final class PermissionCodes {

    public static final String DASHBOARD_PAGE = "upms:dashboard:page";
    public static final String DASHBOARD_GET = "upms:dashboard:get";

    public static final String SYSUSER_PAGE = "upms:sysuser:page";
    public static final String SYSUSER_GET = "upms:sysuser:get";
    public static final String SYSUSER_ADD = "upms:sysuser:add";
    public static final String SYSUSER_EDIT = "upms:sysuser:edit";
    public static final String SYSUSER_DEL = "upms:sysuser:del";

    public static final String SYSROLE_PAGE = "upms:sysrole:page";
    public static final String SYSROLE_GET = "upms:sysrole:get";
    public static final String SYSROLE_ADD = "upms:sysrole:add";
    public static final String SYSROLE_EDIT = "upms:sysrole:edit";
    public static final String SYSROLE_DEL = "upms:sysrole:del";

    public static final String SYSDEPT_PAGE = "upms:sysdept:page";
    public static final String SYSDEPT_GET = "upms:sysdept:get";
    public static final String SYSDEPT_ADD = "upms:sysdept:add";
    public static final String SYSDEPT_EDIT = "upms:sysdept:edit";
    public static final String SYSDEPT_DEL = "upms:sysdept:del";

    public static final String SYSTENANT_PAGE = "upms:systenant:page";
    public static final String SYSTENANT_GET = "upms:systenant:get";
    public static final String SYSTENANT_ADD = "upms:systenant:add";
    public static final String SYSTENANT_EDIT = "upms:systenant:edit";
    public static final String SYSTENANT_DEL = "upms:systenant:del";

    public static final String TENANT_PACKAGE_PAGE = "upms:tenantpackage:page";
    public static final String TENANT_PACKAGE_GET = "upms:tenantpackage:get";
    public static final String TENANT_PACKAGE_ADD = "upms:tenantpackage:add";
    public static final String TENANT_PACKAGE_EDIT = "upms:tenantpackage:edit";
    public static final String TENANT_PACKAGE_DEL = "upms:tenantpackage:del";

    public static final String SYSTEM_PAGE = "upms:system:page";
    public static final String SYSTEM_GET = "upms:system:get";

    public static final String SYSDICT_PAGE = "upms:sysdict:page";
    public static final String SYSDICT_GET = "upms:sysdict:get";
    public static final String SYSDICT_ADD = "upms:sysdict:add";
    public static final String SYSDICT_EDIT = "upms:sysdict:edit";
    public static final String SYSDICT_DEL = "upms:sysdict:del";

    public static final String SYSCONFIG_PAGE = "upms:sysconfig:page";
    public static final String SYSCONFIG_GET = "upms:sysconfig:get";
    public static final String SYSCONFIG_ADD = "upms:sysconfig:add";
    public static final String SYSCONFIG_EDIT = "upms:sysconfig:edit";
    public static final String SYSCONFIG_DEL = "upms:sysconfig:del";

    public static final String SYSCATEGORY_PAGE = "upms:syscategory:page";
    public static final String SYSCATEGORY_GET = "upms:syscategory:get";
    public static final String SYSCATEGORY_ADD = "upms:syscategory:add";
    public static final String SYSCATEGORY_EDIT = "upms:syscategory:edit";
    public static final String SYSCATEGORY_DEL = "upms:syscategory:del";

    public static final String SYSMAIL_PAGE = "upms:sysmail:page";
    public static final String SYSMAIL_GET = "upms:sysmail:get";
    public static final String SYSMAIL_ADD = "upms:sysmail:add";
    public static final String SYSMAIL_EDIT = "upms:sysmail:edit";
    public static final String SYSMAIL_DEL = "upms:sysmail:del";

    public static final String SYSNOTICE_PAGE = "upms:sysnotice:page";
    public static final String SYSNOTICE_GET = "upms:sysnotice:get";
    public static final String SYSNOTICE_ADD = "upms:sysnotice:add";
    public static final String SYSNOTICE_EDIT = "upms:sysnotice:edit";
    public static final String SYSNOTICE_DEL = "upms:sysnotice:del";

    public static final String SECURITY_GET = "upms:security:get";
    public static final String SECURITY_EDIT = "upms:security:edit";

    public static final String OPERATION_LOG_PAGE = "upms:operationlog:page";
    public static final String OPERATION_LOG_GET = "upms:operationlog:get";

    public static final String LOGIN_LOG_PAGE = "upms:loginlog:page";
    public static final String LOGIN_LOG_GET = "upms:loginlog:get";

    public static final String FILE_PAGE = "upms:file:page";
    public static final String FILE_GET = "upms:file:get";
    public static final String FILE_ADD = "upms:file:add";
    public static final String FILE_DEL = "upms:file:del";

    public static final String WORKFLOW_DESIGNER_PAGE = "upms:workflowdesigner:page";
    public static final String WORKFLOW_DEFINITION_PAGE = "upms:workflowdefinition:page";
    public static final String WORKFLOW_DEFINITION_GET = "upms:workflowdefinition:get";
    public static final String WORKFLOW_DEFINITION_ADD = "upms:workflowdefinition:add";
    public static final String WORKFLOW_DEFINITION_EDIT = "upms:workflowdefinition:edit";
    public static final String WORKFLOW_DEFINITION_DEPLOY = "upms:workflowdefinition:deploy";
    public static final String WORKFLOW_INSTANCE_PAGE = "upms:workflowinstance:page";
    public static final String WORKFLOW_INSTANCE_GET = "upms:workflowinstance:get";
    public static final String WORKFLOW_INSTANCE_ADD = "upms:workflowinstance:add";
    public static final String WORKFLOW_INSTANCE_EDIT = "upms:workflowinstance:edit";
    public static final String WORKFLOW_INSTANCE_DEL = "upms:workflowinstance:del";
    public static final String WORKFLOW_TODO_PAGE = "upms:workflowtodo:page";
    public static final String WORKFLOW_TODO_GET = "upms:workflowtodo:get";
    public static final String WORKFLOW_TODO_EDIT = "upms:workflowtodo:edit";
    public static final String WORKFLOW_DONE_PAGE = "upms:workflowdone:page";
    public static final String WORKFLOW_DONE_GET = "upms:workflowdone:get";

    public static final String CODEGEN_PAGE = "upms:codegen:page";
    public static final String CODEGEN_GET = "upms:codegen:get";
    public static final String CODEGEN_ADD = "upms:codegen:add";
    public static final String CODEGEN_EDIT = "upms:codegen:edit";
    public static final String CODEGEN_DEL = "upms:codegen:del";
    public static final String CODEGEN_DOWNLOAD = "upms:codegen:download";

    public static final String CODEGEN_DATASOURCE_PAGE = "gen:datasource:page";
    public static final String CODEGEN_DATASOURCE_GET = "gen:datasource:get";
    public static final String CODEGEN_DATASOURCE_ADD = "gen:datasource:add";
    public static final String CODEGEN_DATASOURCE_EDIT = "gen:datasource:edit";
    public static final String CODEGEN_DATASOURCE_DEL = "gen:datasource:del";
    public static final String CODEGEN_TABLE_PAGE = "gen:gen-table:page";
    public static final String CODEGEN_TABLE_GET = "gen:gen-table:get";
    public static final String CODEGEN_TABLE_ADD = "gen:gen-table:add";
    public static final String CODEGEN_TABLE_EDIT = "gen:gen-table:edit";
    public static final String CODEGEN_TABLE_DEL = "gen:gen-table:del";
    public static final String CODEGEN_TABLE_DOWNLOAD = "gen:gen-table:download";

    public static final String SYSMENU_PAGE = "upms:sysmenu:page";
    public static final String SYSMENU_GET = "upms:sysmenu:get";
    public static final String SYSMENU_ADD = "upms:sysmenu:add";
    public static final String SYSMENU_EDIT = "upms:sysmenu:edit";
    public static final String SYSMENU_DEL = "upms:sysmenu:del";

    public static final String SESSION_PAGE = "upms:session:page";
    public static final String SESSION_GET = "upms:session:get";
    public static final String SESSION_KICK = "upms:session:kick";

    public static final Set<String> ALL = Set.of(
            DASHBOARD_PAGE,
            DASHBOARD_GET,
            SYSUSER_PAGE,
            SYSUSER_GET,
            SYSUSER_ADD,
            SYSUSER_EDIT,
            SYSUSER_DEL,
            SYSROLE_PAGE,
            SYSROLE_GET,
            SYSROLE_ADD,
            SYSROLE_EDIT,
            SYSROLE_DEL,
            SYSDEPT_PAGE,
            SYSDEPT_GET,
            SYSDEPT_ADD,
            SYSDEPT_EDIT,
            SYSDEPT_DEL,
            SYSTENANT_PAGE,
            SYSTENANT_GET,
            SYSTENANT_ADD,
            SYSTENANT_EDIT,
            SYSTENANT_DEL,
            TENANT_PACKAGE_PAGE,
            TENANT_PACKAGE_GET,
            TENANT_PACKAGE_ADD,
            TENANT_PACKAGE_EDIT,
            TENANT_PACKAGE_DEL,
            SYSTEM_PAGE,
            SYSTEM_GET,
            SYSDICT_PAGE,
            SYSDICT_GET,
            SYSDICT_ADD,
            SYSDICT_EDIT,
            SYSDICT_DEL,
            SYSCONFIG_PAGE,
            SYSCONFIG_GET,
            SYSCONFIG_ADD,
            SYSCONFIG_EDIT,
            SYSCONFIG_DEL,
            SYSCATEGORY_PAGE,
            SYSCATEGORY_GET,
            SYSCATEGORY_ADD,
            SYSCATEGORY_EDIT,
            SYSCATEGORY_DEL,
            SYSMAIL_PAGE,
            SYSMAIL_GET,
            SYSMAIL_ADD,
            SYSMAIL_EDIT,
            SYSMAIL_DEL,
            SYSNOTICE_PAGE,
            SYSNOTICE_GET,
            SYSNOTICE_ADD,
            SYSNOTICE_EDIT,
            SYSNOTICE_DEL,
            SECURITY_GET,
            SECURITY_EDIT,
            OPERATION_LOG_PAGE,
            OPERATION_LOG_GET,
            LOGIN_LOG_PAGE,
            LOGIN_LOG_GET,
            FILE_PAGE,
            FILE_GET,
            FILE_ADD,
            FILE_DEL,
            WORKFLOW_DESIGNER_PAGE,
            WORKFLOW_DEFINITION_PAGE,
            WORKFLOW_DEFINITION_GET,
            WORKFLOW_DEFINITION_ADD,
            WORKFLOW_DEFINITION_EDIT,
            WORKFLOW_DEFINITION_DEPLOY,
            WORKFLOW_INSTANCE_PAGE,
            WORKFLOW_INSTANCE_GET,
            WORKFLOW_INSTANCE_ADD,
            WORKFLOW_INSTANCE_EDIT,
            WORKFLOW_INSTANCE_DEL,
            WORKFLOW_TODO_PAGE,
            WORKFLOW_TODO_GET,
            WORKFLOW_TODO_EDIT,
            WORKFLOW_DONE_PAGE,
            WORKFLOW_DONE_GET,
            CODEGEN_PAGE,
            CODEGEN_GET,
            CODEGEN_ADD,
            CODEGEN_EDIT,
            CODEGEN_DEL,
            CODEGEN_DOWNLOAD,
            CODEGEN_DATASOURCE_PAGE,
            CODEGEN_DATASOURCE_GET,
            CODEGEN_DATASOURCE_ADD,
            CODEGEN_DATASOURCE_EDIT,
            CODEGEN_DATASOURCE_DEL,
            CODEGEN_TABLE_PAGE,
            CODEGEN_TABLE_GET,
            CODEGEN_TABLE_ADD,
            CODEGEN_TABLE_EDIT,
            CODEGEN_TABLE_DEL,
            CODEGEN_TABLE_DOWNLOAD,
            SYSMENU_PAGE,
            SYSMENU_GET,
            SYSMENU_ADD,
            SYSMENU_EDIT,
            SYSMENU_DEL,
            SESSION_PAGE,
            SESSION_GET,
            SESSION_KICK
    );

    public static final List<PermissionDescriptor> CATALOG = Arrays.asList(
            new PermissionDescriptor(DASHBOARD_PAGE, "运行总览列表", "dashboard"),
            new PermissionDescriptor(DASHBOARD_GET, "运行总览查询", "dashboard"),
            new PermissionDescriptor(SYSUSER_PAGE, "用户管理列表", "sysuser"),
            new PermissionDescriptor(SYSUSER_GET, "用户管理查询", "sysuser"),
            new PermissionDescriptor(SYSUSER_ADD, "用户管理新增", "sysuser"),
            new PermissionDescriptor(SYSUSER_EDIT, "用户管理修改", "sysuser"),
            new PermissionDescriptor(SYSUSER_DEL, "用户管理删除", "sysuser"),
            new PermissionDescriptor(SYSROLE_PAGE, "角色管理列表", "sysrole"),
            new PermissionDescriptor(SYSROLE_GET, "角色管理查询", "sysrole"),
            new PermissionDescriptor(SYSROLE_ADD, "角色管理新增", "sysrole"),
            new PermissionDescriptor(SYSROLE_EDIT, "角色管理修改", "sysrole"),
            new PermissionDescriptor(SYSROLE_DEL, "角色管理删除", "sysrole"),
            new PermissionDescriptor(SYSDEPT_PAGE, "部门管理列表", "sysdept"),
            new PermissionDescriptor(SYSDEPT_GET, "部门管理查询", "sysdept"),
            new PermissionDescriptor(SYSDEPT_ADD, "部门管理新增", "sysdept"),
            new PermissionDescriptor(SYSDEPT_EDIT, "部门管理修改", "sysdept"),
            new PermissionDescriptor(SYSDEPT_DEL, "部门管理删除", "sysdept"),
            new PermissionDescriptor(SYSTENANT_PAGE, "租户管理列表", "systenant"),
            new PermissionDescriptor(SYSTENANT_GET, "租户管理查询", "systenant"),
            new PermissionDescriptor(SYSTENANT_ADD, "租户管理新增", "systenant"),
            new PermissionDescriptor(SYSTENANT_EDIT, "租户管理修改", "systenant"),
            new PermissionDescriptor(SYSTENANT_DEL, "租户管理删除", "systenant"),
            new PermissionDescriptor(TENANT_PACKAGE_PAGE, "租户套餐列表", "tenantpackage"),
            new PermissionDescriptor(TENANT_PACKAGE_GET, "租户套餐查询", "tenantpackage"),
            new PermissionDescriptor(TENANT_PACKAGE_ADD, "租户套餐新增", "tenantpackage"),
            new PermissionDescriptor(TENANT_PACKAGE_EDIT, "租户套餐修改", "tenantpackage"),
            new PermissionDescriptor(TENANT_PACKAGE_DEL, "租户套餐删除", "tenantpackage"),
            new PermissionDescriptor(SYSTEM_PAGE, "系统设置列表", "system"),
            new PermissionDescriptor(SYSTEM_GET, "系统设置查询", "system"),
            new PermissionDescriptor(SYSDICT_PAGE, "字典管理列表", "sysdict"),
            new PermissionDescriptor(SYSDICT_GET, "字典管理查询", "sysdict"),
            new PermissionDescriptor(SYSDICT_ADD, "字典管理新增", "sysdict"),
            new PermissionDescriptor(SYSDICT_EDIT, "字典管理修改", "sysdict"),
            new PermissionDescriptor(SYSDICT_DEL, "字典管理删除", "sysdict"),
            new PermissionDescriptor(SYSCONFIG_PAGE, "系统参数列表", "sysconfig"),
            new PermissionDescriptor(SYSCONFIG_GET, "系统参数查询", "sysconfig"),
            new PermissionDescriptor(SYSCONFIG_ADD, "系统参数新增", "sysconfig"),
            new PermissionDescriptor(SYSCONFIG_EDIT, "系统参数修改", "sysconfig"),
            new PermissionDescriptor(SYSCONFIG_DEL, "系统参数删除", "sysconfig"),
            new PermissionDescriptor(SYSCATEGORY_PAGE, "分类配置列表", "syscategory"),
            new PermissionDescriptor(SYSCATEGORY_GET, "分类配置查询", "syscategory"),
            new PermissionDescriptor(SYSCATEGORY_ADD, "分类配置新增", "syscategory"),
            new PermissionDescriptor(SYSCATEGORY_EDIT, "分类配置修改", "syscategory"),
            new PermissionDescriptor(SYSCATEGORY_DEL, "分类配置删除", "syscategory"),
            new PermissionDescriptor(SYSMAIL_PAGE, "邮件配置列表", "sysmail"),
            new PermissionDescriptor(SYSMAIL_GET, "邮件配置查询", "sysmail"),
            new PermissionDescriptor(SYSMAIL_ADD, "邮件配置新增", "sysmail"),
            new PermissionDescriptor(SYSMAIL_EDIT, "邮件配置修改", "sysmail"),
            new PermissionDescriptor(SYSMAIL_DEL, "邮件配置删除", "sysmail"),
            new PermissionDescriptor(SYSNOTICE_PAGE, "公告管理列表", "sysnotice"),
            new PermissionDescriptor(SYSNOTICE_GET, "公告管理查询", "sysnotice"),
            new PermissionDescriptor(SYSNOTICE_ADD, "公告管理新增", "sysnotice"),
            new PermissionDescriptor(SYSNOTICE_EDIT, "公告管理修改", "sysnotice"),
            new PermissionDescriptor(SYSNOTICE_DEL, "公告管理删除", "sysnotice"),
            new PermissionDescriptor(SECURITY_GET, "安全策略查询", "security"),
            new PermissionDescriptor(SECURITY_EDIT, "安全策略修改", "security"),
            new PermissionDescriptor(OPERATION_LOG_PAGE, "操作日志列表", "operationlog"),
            new PermissionDescriptor(OPERATION_LOG_GET, "操作日志查询", "operationlog"),
            new PermissionDescriptor(LOGIN_LOG_PAGE, "登录日志列表", "loginlog"),
            new PermissionDescriptor(LOGIN_LOG_GET, "登录日志查询", "loginlog"),
            new PermissionDescriptor(FILE_PAGE, "文件管理列表", "file"),
            new PermissionDescriptor(FILE_GET, "文件管理查询", "file"),
            new PermissionDescriptor(FILE_ADD, "文件管理上传", "file"),
            new PermissionDescriptor(FILE_DEL, "文件管理删除", "file"),
            new PermissionDescriptor(WORKFLOW_DESIGNER_PAGE, "流程设计器入口", "workflowdesigner"),
            new PermissionDescriptor(WORKFLOW_DEFINITION_PAGE, "流程定义列表", "workflowdefinition"),
            new PermissionDescriptor(WORKFLOW_DEFINITION_GET, "流程定义查询", "workflowdefinition"),
            new PermissionDescriptor(WORKFLOW_DEFINITION_ADD, "流程定义新增", "workflowdefinition"),
            new PermissionDescriptor(WORKFLOW_DEFINITION_EDIT, "流程定义修改", "workflowdefinition"),
            new PermissionDescriptor(WORKFLOW_DEFINITION_DEPLOY, "流程定义部署", "workflowdefinition"),
            new PermissionDescriptor(WORKFLOW_INSTANCE_PAGE, "流程实例列表", "workflowinstance"),
            new PermissionDescriptor(WORKFLOW_INSTANCE_GET, "流程实例查询", "workflowinstance"),
            new PermissionDescriptor(WORKFLOW_INSTANCE_ADD, "流程实例发起", "workflowinstance"),
            new PermissionDescriptor(WORKFLOW_INSTANCE_EDIT, "流程实例处理", "workflowinstance"),
            new PermissionDescriptor(WORKFLOW_INSTANCE_DEL, "流程实例终止", "workflowinstance"),
            new PermissionDescriptor(WORKFLOW_TODO_PAGE, "我的待办列表", "workflowtodo"),
            new PermissionDescriptor(WORKFLOW_TODO_GET, "我的待办查询", "workflowtodo"),
            new PermissionDescriptor(WORKFLOW_TODO_EDIT, "我的待办处理", "workflowtodo"),
            new PermissionDescriptor(WORKFLOW_DONE_PAGE, "我的已办列表", "workflowdone"),
            new PermissionDescriptor(WORKFLOW_DONE_GET, "我的已办查询", "workflowdone"),
            new PermissionDescriptor(CODEGEN_PAGE, "代码生成列表", "codegen"),
            new PermissionDescriptor(CODEGEN_GET, "代码生成查询", "codegen"),
            new PermissionDescriptor(CODEGEN_ADD, "代码生成新增", "codegen"),
            new PermissionDescriptor(CODEGEN_EDIT, "代码生成修改", "codegen"),
            new PermissionDescriptor(CODEGEN_DEL, "代码生成删除", "codegen"),
            new PermissionDescriptor(CODEGEN_DOWNLOAD, "代码生成下载", "codegen"),
            new PermissionDescriptor(CODEGEN_DATASOURCE_PAGE, "数据源列表", "codegen-datasource"),
            new PermissionDescriptor(CODEGEN_DATASOURCE_GET, "数据源查询", "codegen-datasource"),
            new PermissionDescriptor(CODEGEN_DATASOURCE_ADD, "数据源新增", "codegen-datasource"),
            new PermissionDescriptor(CODEGEN_DATASOURCE_EDIT, "数据源修改", "codegen-datasource"),
            new PermissionDescriptor(CODEGEN_DATASOURCE_DEL, "数据源删除", "codegen-datasource"),
            new PermissionDescriptor(CODEGEN_TABLE_PAGE, "数据表列表", "codegen-table"),
            new PermissionDescriptor(CODEGEN_TABLE_GET, "数据表查询", "codegen-table"),
            new PermissionDescriptor(CODEGEN_TABLE_ADD, "数据表导入", "codegen-table"),
            new PermissionDescriptor(CODEGEN_TABLE_EDIT, "数据表修改", "codegen-table"),
            new PermissionDescriptor(CODEGEN_TABLE_DEL, "数据表删除", "codegen-table"),
            new PermissionDescriptor(CODEGEN_TABLE_DOWNLOAD, "代码生成下载", "codegen-table"),
            new PermissionDescriptor(SYSMENU_PAGE, "菜单管理列表", "sysmenu"),
            new PermissionDescriptor(SYSMENU_GET, "菜单管理查询", "sysmenu"),
            new PermissionDescriptor(SYSMENU_ADD, "菜单管理新增", "sysmenu"),
            new PermissionDescriptor(SYSMENU_EDIT, "菜单管理修改", "sysmenu"),
            new PermissionDescriptor(SYSMENU_DEL, "菜单管理删除", "sysmenu"),
            new PermissionDescriptor(SESSION_PAGE, "在线用户列表", "session"),
            new PermissionDescriptor(SESSION_GET, "在线用户查询", "session"),
            new PermissionDescriptor(SESSION_KICK, "在线用户强制下线", "session")
    );

    private PermissionCodes() {
    }

    public record PermissionDescriptor(String code, String label, String module) {
    }
}
