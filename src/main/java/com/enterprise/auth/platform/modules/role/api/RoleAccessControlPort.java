package com.enterprise.auth.platform.modules.role.api;

/** Role-owned contract for platform-level administration checks. */
public interface RoleAccessControlPort {

    boolean isPlatformSuperAdmin();
}
