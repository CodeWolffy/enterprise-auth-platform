package com.enterprise.auth.platform.modules.menu.api;

import java.util.List;
import java.util.Set;

/** Menu-owned contract for resolving grants and grantable menu identifiers. */
public interface MenuGrantQueryPort {

    Set<String> resolveGrantKeys(String activeTenantId, Set<Long> grantedMenuIds, boolean superAdmin);

    List<MenuNode> resolveMenuTree(String activeTenantId, Set<Long> grantedMenuIds, boolean superAdmin);

    Set<Long> expandMenuIdsWithAncestors(String tenantId, Set<Long> menuIds);

    Set<Long> filterGrantableMenuIds(String tenantId, Set<Long> menuIds);
}
