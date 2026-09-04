package com.enterprise.auth.platform.modules.user.api;

/** User-owned contract for validating cross-tenant administration targets. */
public interface UserTenantReferencePort {

    boolean tenantExists(String tenantId);
}
