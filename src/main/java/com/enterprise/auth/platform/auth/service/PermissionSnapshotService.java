package com.enterprise.auth.platform.auth.service;

import com.enterprise.auth.platform.auth.dto.PermissionSnapshotResponse;
import com.enterprise.auth.platform.catalog.CatalogService;
import com.enterprise.auth.platform.user.model.UserAccount;
import org.springframework.stereotype.Service;

@Service
public class PermissionSnapshotService {

    private final CatalogService catalogService;

    public PermissionSnapshotService(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    public PermissionSnapshotResponse build(UserAccount user) {
        return new PermissionSnapshotResponse(
                user.id(),
                user.username(),
                user.tenantId(),
                user.roles(),
                user.permissions(),
                user.dataScopeType(),
                user.customDeptIds(),
                catalogService.menusFor(user.permissions())
        );
    }
}
