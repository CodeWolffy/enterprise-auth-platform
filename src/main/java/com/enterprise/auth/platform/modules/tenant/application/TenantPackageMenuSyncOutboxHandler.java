package com.enterprise.auth.platform.modules.tenant.application;

import com.enterprise.auth.platform.common.outbox.OutboxEventEnvelope;
import com.enterprise.auth.platform.common.outbox.OutboxEventHandler;
import com.enterprise.auth.platform.modules.tenant.api.TenantPackageMenuSyncEvent;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Rebuilds package-derived tenant menus outside the package update transaction.
 * Each tenant update has its own transaction; replay after a partial failure is safe because assignments are replaced.
 */
@Component
public class TenantPackageMenuSyncOutboxHandler implements OutboxEventHandler {

    static final int TENANT_BATCH_SIZE = 100;

    private final ObjectMapper objectMapper;
    private final SysTenantMapper sysTenantMapper;
    private final TenantMenuService tenantMenuService;

    public TenantPackageMenuSyncOutboxHandler(
            ObjectMapper objectMapper,
            SysTenantMapper sysTenantMapper,
            TenantMenuService tenantMenuService
    ) {
        this.objectMapper = objectMapper;
        this.sysTenantMapper = sysTenantMapper;
        this.tenantMenuService = tenantMenuService;
    }

    @Override
    public String eventType() {
        return TenantPackageMenuSyncEvent.TYPE;
    }

    @Override
    public void handle(OutboxEventEnvelope envelope) throws Exception {
        TenantPackageMenuSyncEvent event = objectMapper.readValue(
                envelope.payloadJson(),
                TenantPackageMenuSyncEvent.class
        );
        if (!StringUtils.hasText(event.packageCode())) {
            throw new IllegalArgumentException("tenant package menu sync packageCode must not be blank");
        }
        String packageCode = event.packageCode().trim();
        List<String> tenantIds = sysTenantMapper.selectTenantIdsByPackageCode(packageCode).stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (tenantIds.isEmpty()) {
            return;
        }

        Set<Long> menuIds = tenantMenuService.menuIdsForPackageCode(packageCode);
        for (int offset = 0; offset < tenantIds.size(); offset += TENANT_BATCH_SIZE) {
            int end = Math.min(offset + TENANT_BATCH_SIZE, tenantIds.size());
            syncBatch(tenantIds.subList(offset, end), menuIds);
        }
    }

    private void syncBatch(List<String> tenantIds, Set<Long> menuIds) {
        for (String tenantId : tenantIds) {
            tenantMenuService.saveTenantMenu(tenantId, menuIds);
        }
    }
}
