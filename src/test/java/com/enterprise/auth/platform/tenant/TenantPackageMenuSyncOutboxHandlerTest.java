package com.enterprise.auth.platform.tenant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.auth.platform.common.outbox.OutboxEventEnvelope;
import com.enterprise.auth.platform.modules.tenant.api.TenantPackageMenuSyncEvent;
import com.enterprise.auth.platform.modules.tenant.application.TenantMenuService;
import com.enterprise.auth.platform.modules.tenant.application.TenantPackageMenuSyncOutboxHandler;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class TenantPackageMenuSyncOutboxHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldResolveMenusOnceAndIgnoreDuplicateTenantIds() throws Exception {
        SysTenantMapper tenantMapper = mock(SysTenantMapper.class);
        TenantMenuService tenantMenuService = mock(TenantMenuService.class);
        when(tenantMapper.selectTenantIdsByPackageCode("standard"))
                .thenReturn(List.of("tenant-a", " tenant-b ", "tenant-a"));
        when(tenantMenuService.menuIdsForPackageCode("standard")).thenReturn(Set.of(10L, 20L));
        TenantPackageMenuSyncOutboxHandler handler = new TenantPackageMenuSyncOutboxHandler(
                objectMapper,
                tenantMapper,
                tenantMenuService
        );

        handler.handle(event("standard"));

        verify(tenantMenuService).menuIdsForPackageCode("standard");
        verify(tenantMenuService).saveTenantMenu("tenant-a", Set.of(10L, 20L));
        verify(tenantMenuService).saveTenantMenu("tenant-b", Set.of(10L, 20L));
    }

    @Test
    void retryAfterPartialFailureShouldSafelyReplayCompletedTenants() throws Exception {
        SysTenantMapper tenantMapper = mock(SysTenantMapper.class);
        TenantMenuService tenantMenuService = mock(TenantMenuService.class);
        Set<Long> menuIds = Set.of(10L);
        when(tenantMapper.selectTenantIdsByPackageCode("standard"))
                .thenReturn(List.of("tenant-a", "tenant-b", "tenant-c"));
        when(tenantMenuService.menuIdsForPackageCode("standard")).thenReturn(menuIds);
        doThrow(new IllegalStateException("temporary database failure"))
                .doNothing()
                .when(tenantMenuService).saveTenantMenu("tenant-b", menuIds);
        TenantPackageMenuSyncOutboxHandler handler = new TenantPackageMenuSyncOutboxHandler(
                objectMapper,
                tenantMapper,
                tenantMenuService
        );

        assertThrows(IllegalStateException.class, () -> handler.handle(event("standard")));
        handler.handle(event("standard"));

        InOrder order = inOrder(tenantMenuService);
        order.verify(tenantMenuService).saveTenantMenu("tenant-a", menuIds);
        order.verify(tenantMenuService).saveTenantMenu("tenant-b", menuIds);
        order.verify(tenantMenuService).saveTenantMenu("tenant-a", menuIds);
        order.verify(tenantMenuService).saveTenantMenu("tenant-b", menuIds);
        order.verify(tenantMenuService).saveTenantMenu("tenant-c", menuIds);
        verify(tenantMenuService, times(2)).menuIdsForPackageCode("standard");
    }

    private OutboxEventEnvelope event(String packageCode) throws Exception {
        return new OutboxEventEnvelope(
                1L,
                "platform",
                TenantPackageMenuSyncEvent.TYPE,
                TenantPackageMenuSyncEvent.AGGREGATE_TYPE,
                packageCode,
                objectMapper.writeValueAsString(new TenantPackageMenuSyncEvent(packageCode))
        );
    }
}
