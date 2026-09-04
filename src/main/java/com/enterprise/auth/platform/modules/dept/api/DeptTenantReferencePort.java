package com.enterprise.auth.platform.modules.dept.api;

/** Department-owned contract for validating cross-tenant administration targets. */
public interface DeptTenantReferencePort {

    boolean tenantExists(String tenantId);
}
