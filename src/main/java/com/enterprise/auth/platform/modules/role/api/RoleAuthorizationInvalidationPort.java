package com.enterprise.auth.platform.modules.role.api;

/** Role-owned contract for invalidating affected user authorization state. */
public interface RoleAuthorizationInvalidationPort {

    void invalidateUser(Long userId, String tenantId, String username);
}
