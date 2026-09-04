package com.enterprise.auth.platform.modules.tenant.application;

import com.enterprise.auth.platform.modules.auth.api.AuthTenantQueryPort;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Exposes tenant status and switch summaries through the auth-owned contract. */
@Component
public final class TenantAuthQueryAdapter implements AuthTenantQueryPort {

    private final TenantProfileFacade tenantProfileFacade;

    public TenantAuthQueryAdapter(TenantProfileFacade tenantProfileFacade) {
        this.tenantProfileFacade = tenantProfileFacade;
    }

    @Override
    public void ensureTenantAccessible(String tenantId) {
        tenantProfileFacade.ensureTenantAccessible(tenantId);
    }

    @Override
    public Optional<TenantSummary> findByTenantId(String tenantId) {
        return tenantProfileFacade.findByTenantId(tenantId)
                .map(tenant -> new TenantSummary(
                        tenant.getTenantId(),
                        tenant.getTenantName(),
                        tenant.getPlatformLevel(),
                        tenant.getTenantStatus(),
                        tenant.getAuthBeginAt(),
                        tenant.getExpireAt()
                ));
    }

    @Override
    public List<TenantSummary> listTenantRecords() {
        return tenantProfileFacade.listTenantRecords().stream()
                .map(tenant -> new TenantSummary(
                        tenant.tenantId(),
                        tenant.tenantName(),
                        tenant.platformLevel(),
                        tenant.tenantStatus(),
                        tenant.authBeginAt(),
                        tenant.expireAt()
                ))
                .toList();
    }
}
