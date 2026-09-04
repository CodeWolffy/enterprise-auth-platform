package com.enterprise.auth.platform.modules.menu.api;

/** Menu-owned contract for platform-level administration checks. */
public interface MenuAccessControlPort {

    boolean isPlatformSuperAdmin();
}
