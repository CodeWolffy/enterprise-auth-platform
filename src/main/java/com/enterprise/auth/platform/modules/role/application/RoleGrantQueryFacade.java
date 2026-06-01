package com.enterprise.auth.platform.modules.role.application;

import com.enterprise.auth.platform.modules.resource.application.ResourceService;
import com.enterprise.auth.platform.modules.role.application.RoleManagementService;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class RoleGrantQueryFacade {

    private final ResourceService resourceService;
    private final RoleManagementService roleManagementService;

    public RoleGrantQueryFacade(
            ResourceService resourceService,
            RoleManagementService roleManagementService
    ) {
        this.resourceService = resourceService;
        this.roleManagementService = roleManagementService;
    }

    public Set<String> resolveGrantKeys(String activeTenantId, Set<String> roleCodes, boolean superAdmin) {
        return resourceService.resolveGrantKeys(activeTenantId, roleCodes, superAdmin);
    }

    public Set<Long> listRoleResourceIds(Long roleId) {
        return roleManagementService.listAssignedResources(roleId);
    }
}
