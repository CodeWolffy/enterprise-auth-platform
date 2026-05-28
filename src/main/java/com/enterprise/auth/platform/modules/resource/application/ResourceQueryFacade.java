package com.enterprise.auth.platform.modules.resource.application;

import com.enterprise.auth.platform.dto.resp.MenuNode;
import com.enterprise.auth.platform.service.ResourceService;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ResourceQueryFacade {

    private final ResourceService resourceService;

    public ResourceQueryFacade(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    public Set<String> resolveGrantKeys(String activeTenantId, Set<String> roleCodes, boolean superAdmin) {
        return resourceService.resolveGrantKeys(activeTenantId, roleCodes, superAdmin);
    }

    public List<MenuNode> resolveMenuTree(String activeTenantId, Set<String> roleCodes, boolean superAdmin) {
        return resourceService.resolveMenuTree(activeTenantId, roleCodes, superAdmin);
    }
}