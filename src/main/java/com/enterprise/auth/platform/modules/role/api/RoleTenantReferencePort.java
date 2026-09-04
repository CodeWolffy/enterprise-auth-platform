package com.enterprise.auth.platform.modules.role.api;

/** Role-owned contract for validating cross-tenant administration targets. */
public interface RoleTenantReferencePort {

    boolean tenantExists(String tenantId);
}
