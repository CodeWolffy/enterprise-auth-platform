package com.enterprise.auth.platform.modules.security.api;

/** Security-owned contract for platform policy administration. */
public interface SecurityAccessControlPort {

    boolean isPlatformSuperAdmin();
}
