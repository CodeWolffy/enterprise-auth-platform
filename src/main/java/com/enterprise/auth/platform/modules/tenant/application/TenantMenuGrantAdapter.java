package com.enterprise.auth.platform.modules.tenant.application;

import com.enterprise.auth.platform.modules.menu.api.MenuTenantGrantPort;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Exposes tenant menu assignments through the menu-owned query contract. */
@Component
public final class TenantMenuGrantAdapter implements MenuTenantGrantPort {

    private final TenantMenuService tenantMenuService;

    public TenantMenuGrantAdapter(TenantMenuService tenantMenuService) {
        this.tenantMenuService = tenantMenuService;
    }

    @Override
    public Set<Long> findTenantMenuIds(String tenantId) {
        return tenantMenuService.findTenantMenuIds(tenantId);
    }
}
