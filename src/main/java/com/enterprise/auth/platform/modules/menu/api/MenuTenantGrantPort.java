package com.enterprise.auth.platform.modules.menu.api;

import java.util.Set;

/** Menu-owned contract for resolving the template IDs assigned to a tenant. */
public interface MenuTenantGrantPort {

    Set<Long> findTenantMenuIds(String tenantId);
}
