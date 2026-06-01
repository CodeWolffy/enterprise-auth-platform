package com.enterprise.auth.platform.modules.resource.application;

import com.enterprise.auth.platform.modules.auth.interfaces.MenuNode;
import com.enterprise.auth.platform.modules.resource.application.ResourceService;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ResourceQueryFacade {

    private final ResourceService resourceService;

    public ResourceQueryFacade(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    public List<MenuNode> resolveMenuTree(String activeTenantId, Set<String> roleCodes, boolean superAdmin) {
        return resourceService.resolveMenuTree(activeTenantId, roleCodes, superAdmin);
    }
}