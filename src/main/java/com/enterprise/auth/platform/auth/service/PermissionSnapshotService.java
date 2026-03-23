package com.enterprise.auth.platform.auth.service;

import com.enterprise.auth.platform.auth.dto.PermissionSnapshotResponse;
import com.enterprise.auth.platform.catalog.CatalogService;
import com.enterprise.auth.platform.security.PlatformAdminSupport;
import com.enterprise.auth.platform.tenant.TenantContext;
import com.enterprise.auth.platform.user.model.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PermissionSnapshotService {

    private final CatalogService catalogService;
    private final PlatformAdminSupport platformAdminSupport;

    public PermissionSnapshotService(CatalogService catalogService, PlatformAdminSupport platformAdminSupport) {
        this.catalogService = catalogService;
        this.platformAdminSupport = platformAdminSupport;
    }

    public PermissionSnapshotResponse build(UserAccount user) {
        String activeTenantId = TenantContext.getTenantId();
        if (!StringUtils.hasText(activeTenantId)) {
            activeTenantId = user.tenantId();
        }
        return new PermissionSnapshotResponse(
                user.id(),
                user.username(),
                activeTenantId,
                user.tenantId(),
                user.roles(),
                user.permissions(),
                user.dataScopeType(),
                user.customDeptIds(),
                catalogService.menusFor(user.permissions()),
                platformAdminSupport.isPlatformSuperAdmin(user)
        );
    }
}
