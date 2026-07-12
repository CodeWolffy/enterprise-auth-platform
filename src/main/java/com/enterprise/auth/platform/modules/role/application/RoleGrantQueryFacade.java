package com.enterprise.auth.platform.modules.role.application;

import com.enterprise.auth.platform.modules.auth.interfaces.MenuNode;
import com.enterprise.auth.platform.modules.menu.application.MenuGrantQueryPort;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class RoleGrantQueryFacade {

    private final MenuGrantQueryPort menuGrantQueryPort;
    private final RoleManagementService roleManagementService;

    public RoleGrantQueryFacade(MenuGrantQueryPort menuGrantQueryPort, RoleManagementService roleManagementService) {
        this.menuGrantQueryPort = menuGrantQueryPort;
        this.roleManagementService = roleManagementService;
    }

    public Set<String> resolveGrantKeys(String activeTenantId, Set<String> roleCodes, boolean superAdmin) {
        Set<Long> menuIds = superAdmin ? Set.of() : roleManagementService.listMenuIdsByRoleCodes(activeTenantId, roleCodes);
        return menuGrantQueryPort.resolveGrantKeys(activeTenantId, menuIds, superAdmin);
    }

    public List<MenuNode> resolveMenuTree(String activeTenantId, Set<String> roleCodes, boolean superAdmin) {
        Set<Long> menuIds = superAdmin ? Set.of() : roleManagementService.listMenuIdsByRoleCodes(activeTenantId, roleCodes);
        return menuGrantQueryPort.resolveMenuTree(activeTenantId, menuIds, superAdmin);
    }

    public Set<Long> listRoleMenuIds(Long roleId) {
        return roleManagementService.listAssignedMenus(roleId);
    }
}