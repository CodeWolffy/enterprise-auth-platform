package com.enterprise.auth.platform.common.authz;

import java.util.List;
import java.util.Set;

/**
 * 平台权限码登记入口。
 */
public final class PermissionCodes {

    public static final String USER_READ = "user:read";
    public static final String USER_WRITE = "user:write";
    public static final String ROLE_READ = "role:read";
    public static final String ROLE_WRITE = "role:write";
    public static final String DEPT_READ = "dept:read";
    public static final String DEPT_WRITE = "dept:write";
    public static final String TENANT_READ = "tenant:read";
    public static final String TENANT_WRITE = "tenant:write";
    public static final String SYSTEM_READ = "system:read";
    public static final String SYSTEM_WRITE = "system:write";
    public static final String AUDIT_READ = "audit:read";
    public static final String AUDIT_WRITE = "audit:write";
    public static final String SESSION_WRITE = "session:write";

    public static final Set<String> ALL = Set.of(
            USER_READ,
            USER_WRITE,
            ROLE_READ,
            ROLE_WRITE,
            DEPT_READ,
            DEPT_WRITE,
            TENANT_READ,
            TENANT_WRITE,
            SYSTEM_READ,
            SYSTEM_WRITE,
            AUDIT_READ,
            AUDIT_WRITE,
            SESSION_WRITE
    );

    public static final List<PermissionDescriptor> CATALOG = List.of(
            new PermissionDescriptor(USER_READ, "用户查看", "user"),
            new PermissionDescriptor(USER_WRITE, "用户管理", "user"),
            new PermissionDescriptor(ROLE_READ, "角色查看", "role"),
            new PermissionDescriptor(ROLE_WRITE, "角色管理", "role"),
            new PermissionDescriptor(DEPT_READ, "部门查看", "dept"),
            new PermissionDescriptor(DEPT_WRITE, "部门管理", "dept"),
            new PermissionDescriptor(TENANT_READ, "租户查看", "tenant"),
            new PermissionDescriptor(TENANT_WRITE, "租户管理", "tenant"),
            new PermissionDescriptor(SYSTEM_READ, "系统查看", "system"),
            new PermissionDescriptor(SYSTEM_WRITE, "系统管理", "system"),
            new PermissionDescriptor(AUDIT_READ, "审计查看", "audit"),
            new PermissionDescriptor(AUDIT_WRITE, "审计管理", "audit"),
            new PermissionDescriptor(SESSION_WRITE, "会话管理", "session")
    );

    private PermissionCodes() {
    }

    public record PermissionDescriptor(String code, String label, String module) {
    }
}