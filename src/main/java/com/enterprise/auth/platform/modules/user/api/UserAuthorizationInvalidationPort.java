package com.enterprise.auth.platform.modules.user.api;

/** User-owned port for invalidating cached authorization state after user changes. */
public interface UserAuthorizationInvalidationPort {

    void invalidateUser(Long userId, String tenantId, String username);
}
