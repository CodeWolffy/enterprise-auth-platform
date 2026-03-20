package com.enterprise.auth.platform.permission;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.auth.platform.catalog.CatalogService;
import com.enterprise.auth.platform.permission.dto.CreatePermissionRequest;
import com.enterprise.auth.platform.permission.service.PermissionManagementService;
import com.enterprise.auth.platform.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PermissionManagementServiceTest {

    @Autowired
    private PermissionManagementService permissionManagementService;

    @Autowired
    private CatalogService catalogService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void shouldCreateAndDeletePermissionInDatabaseMode() {
        TenantContext.setTenantId("platform");
        assertThat(catalogService.permissions()).isNotEmpty();
        String permissionCode = "demo:write:" + System.nanoTime();
        CatalogService.PermissionView created = permissionManagementService.create(
                new CreatePermissionRequest("demo", "write", "tenant", "演示写入", permissionCode)
        );

        assertThat(catalogService.permissions()).extracting(CatalogService.PermissionView::permissionCode).contains(permissionCode);

        permissionManagementService.delete(created.id());

        assertThat(catalogService.permissions()).extracting(CatalogService.PermissionView::permissionCode).doesNotContain(permissionCode);
    }
}
