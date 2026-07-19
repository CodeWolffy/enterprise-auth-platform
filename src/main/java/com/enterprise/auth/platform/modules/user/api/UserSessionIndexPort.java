package com.enterprise.auth.platform.modules.user.api;

/**
 * Narrow user-owned port for invalidating all sessions belonging to a user.
 *
 * <p>The user module depends on this capability instead of the auth module's
 * concrete session-index implementation.</p>
 */
public interface UserSessionIndexPort {

    void removeUser(Long userId);
}
