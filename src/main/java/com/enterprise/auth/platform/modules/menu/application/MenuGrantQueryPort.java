package com.enterprise.auth.platform.modules.menu.application;

import com.enterprise.auth.platform.modules.auth.interfaces.MenuNode;
import java.util.List;
import java.util.Set;

/**
 * 菜单授权查询端口：role 模块只依赖此接口，避免 role↔menu 实现环。
 */
public interface MenuGrantQueryPort {

    Set<String> resolveGrantKeys(String activeTenantId, Set<Long> grantedMenuIds, boolean superAdmin);

    List<MenuNode> resolveMenuTree(String activeTenantId, Set<Long> grantedMenuIds, boolean superAdmin);

    Set<Long> expandMenuIdsWithAncestors(String tenantId, Set<Long> menuIds);

    Set<Long> filterGrantableMenuIds(String tenantId, Set<Long> menuIds);
}