package com.enterprise.auth.platform.modules.menu.api;

/** Menu-owned contract for invalidating authorization state after template changes. */
public interface MenuAuthorizationInvalidationPort {

    void invalidateAll();
}
