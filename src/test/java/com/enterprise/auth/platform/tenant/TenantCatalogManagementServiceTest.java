package com.enterprise.auth.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.outbox.OutboxEventPublisher;
import com.enterprise.auth.platform.modules.auth.application.AuthPermissionSnapshotInvalidationService;
import com.enterprise.auth.platform.modules.tenant.application.TenantCatalogManagementService;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantPackageEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantPackageMapper;
import com.enterprise.auth.platform.modules.tenant.interfaces.TenantPackageCrudRequest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantCatalogManagementServiceTest {

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void updatePackageShouldMigrateReferencesAndEnqueueMenuSyncInSameTransaction() {
        SysTenantPackageMapper packageMapper = mock(SysTenantPackageMapper.class);
        SysTenantMapper tenantMapper = mock(SysTenantMapper.class);
        OutboxEventPublisher outboxEventPublisher = mock(OutboxEventPublisher.class);
        AuthPermissionSnapshotInvalidationService invalidationService =
                mock(AuthPermissionSnapshotInvalidationService.class);
        TenantCatalogManagementService service = new TenantCatalogManagementService(
                packageMapper,
                tenantMapper,
                outboxEventPublisher,
                invalidationService
        );
        SysTenantPackageEntity pkg = tenantPackage("legacy", "old_app");
        when(packageMapper.selectOne(any())).thenReturn(pkg);
        when(packageMapper.selectCount(any())).thenReturn(0L);
        when(tenantMapper.updatePackageCodeReferences("legacy", "standard")).thenReturn(2);
        when(tenantMapper.selectList(any())).thenReturn(List.of(
                tenant("tenant-a", "standard"),
                tenant("tenant-b", "standard")
        ));
        TenantContext.setTenantId("platform");

        var result = service.updatePackage(7L, request("standard", "new_app"));

        assertThat(result.packageCode()).isEqualTo("standard");
        assertThat(result.referencedTenantCount()).isEqualTo(2);
        verify(tenantMapper).updatePackageCodeReferences("legacy", "standard");
        verify(tenantMapper, never()).updateById(any(SysTenantEntity.class));
        verify(outboxEventPublisher).enqueue(
                eq("TENANT_PACKAGE_MENU_SYNC"),
                eq("platform"),
                eq("TENANT_PACKAGE"),
                eq("standard"),
                any()
        );
        verify(invalidationService).invalidateAll();
    }

    @Test
    void updatePackageShouldSkipReferenceMigrationWhenCodeIsUnchanged() {
        SysTenantPackageMapper packageMapper = mock(SysTenantPackageMapper.class);
        SysTenantMapper tenantMapper = mock(SysTenantMapper.class);
        OutboxEventPublisher outboxEventPublisher = mock(OutboxEventPublisher.class);
        AuthPermissionSnapshotInvalidationService invalidationService =
                mock(AuthPermissionSnapshotInvalidationService.class);
        TenantCatalogManagementService service = new TenantCatalogManagementService(
                packageMapper,
                tenantMapper,
                outboxEventPublisher,
                invalidationService
        );
        SysTenantPackageEntity pkg = tenantPackage("standard", "old_app");
        when(packageMapper.selectOne(any())).thenReturn(pkg);
        when(packageMapper.selectCount(any())).thenReturn(0L);
        when(tenantMapper.selectList(any())).thenReturn(List.of());
        TenantContext.setTenantId("platform");

        service.updatePackage(7L, request("standard", "new_app"));

        verify(tenantMapper, never()).updatePackageCodeReferences(any(), any());
        verify(outboxEventPublisher).enqueue(
                eq("TENANT_PACKAGE_MENU_SYNC"),
                eq("platform"),
                eq("TENANT_PACKAGE"),
                eq("standard"),
                any()
        );
        verify(invalidationService).invalidateAll();
    }

    private static SysTenantPackageEntity tenantPackage(String packageCode, String appKey) {
        SysTenantPackageEntity entity = new SysTenantPackageEntity();
        entity.setId(7L);
        entity.setTenantId("platform");
        entity.setPackageCode(packageCode);
        entity.setPackageName("套餐");
        entity.setAppKey(appKey);
        entity.setStatus("0");
        entity.setDeleted(0);
        return entity;
    }

    private static SysTenantEntity tenant(String tenantId, String packageCode) {
        SysTenantEntity entity = new SysTenantEntity();
        entity.setTenantId(tenantId);
        entity.setPackageCode(packageCode);
        entity.setDeleted(0);
        return entity;
    }

    private static TenantPackageCrudRequest request(String packageCode, String appKey) {
        return new TenantPackageCrudRequest(
                packageCode,
                "更新套餐",
                null,
                null,
                null,
                null,
                appKey,
                0,
                null,
                "0"
        );
    }
}
