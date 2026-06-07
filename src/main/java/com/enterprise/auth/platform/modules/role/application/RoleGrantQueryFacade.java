package com.enterprise.auth.platform.modules.role.application;

import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.menu.application.MenuService;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class RoleGrantQueryFacade {

    private final MenuService menuService;
    private final RoleManagementService roleManagementService;

    public RoleGrantQueryFacade(MenuService menuService, RoleManagementService roleManagementService) {
        this.menuService = menuService;
        this.roleManagementService = roleManagementService;
    }

    public Set<String> resolveGrantKeys(String activeTenantId, Set<String> roleCodes, boolean superAdmin) {
        Set<Long> menuIds = superAdmin ? Set.of() : roleManagementService.listMenuIdsByRoleCodes(activeTenantId, roleCodes);
        return menuService.resolveGrantKeys(activeTenantId, menuIds, superAdmin);
    }

    public List<com.enterprise.auth.platform.modules.auth.interfaces.MenuNode> resolveMenuTree(String activeTenantId, Set<String> roleCodes, boolean superAdmin) {
        Set<Long> menuIds = superAdmin ? Set.of() : roleManagementService.listMenuIdsByRoleCodes(activeTenantId, roleCodes);
        return menuService.resolveMenuTree(activeTenantId, menuIds, superAdmin);
    }

    public Set<Long> listRoleResourceIds(Long roleId) {
        return menuService.filterGrantableMenuIds(currentTenantId(), roleManagementService.listAssignedMenus(roleId));
    }

    public Set<Long> listRoleMenuIds(Long roleId) {
        return menuService.filterGrantableMenuIds(currentTenantId(), roleManagementService.listRoleMenuIds(currentTenantId(), roleId));
    }

    private String currentTenantId() {
        String tenantId = TenantContext.getTenantId();
        return org.springframework.util.StringUtils.hasText(tenantId) ? tenantId : "platform";
    }
}