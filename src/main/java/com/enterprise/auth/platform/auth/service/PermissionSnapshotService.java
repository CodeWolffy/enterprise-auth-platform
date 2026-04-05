package com.enterprise.auth.platform.auth.service;

import com.enterprise.auth.platform.auth.dto.PermissionSnapshotResponse;
import com.enterprise.auth.platform.resource.service.ResourceService;
import com.enterprise.auth.platform.security.PlatformAdminSupport;
import com.enterprise.auth.platform.tenant.TenantContext;
import com.enterprise.auth.platform.user.model.UserAccount;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PermissionSnapshotService {

    private final ResourceService resourceService;
    private final PlatformAdminSupport platformAdminSupport;

    public PermissionSnapshotService(ResourceService resourceService, PlatformAdminSupport platformAdminSupport) {
        this.resourceService = resourceService;
        this.platformAdminSupport = platformAdminSupport;
    }

    public PermissionSnapshotResponse build(UserAccount user) {
        String activeTenantId = TenantContext.getTenantId();
        if (!StringUtils.hasText(activeTenantId)) {
            activeTenantId = user.tenantId();
        }
        boolean superAdmin = platformAdminSupport.isPlatformSuperAdmin(user);
        Set<String> grants = resourceService.resolveGrantKeys(activeTenantId, user.roles(), superAdmin);
        List<com.enterprise.auth.platform.common.model.MenuNode> menus = resourceService.resolveMenuTree(activeTenantId, user.roles(), superAdmin);
        return new PermissionSnapshotResponse(
                user.id(),
                user.username(),
                activeTenantId,
                user.tenantId(),
                user.roles(),
                grants,
                user.dataScopeType(),
                user.customDeptIds(),
                menus,
                superAdmin
        );
    }
}