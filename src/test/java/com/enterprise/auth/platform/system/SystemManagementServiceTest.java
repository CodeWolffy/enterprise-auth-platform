package com.enterprise.auth.platform.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.auth.platform.system.dto.DictCrudRequest;
import com.enterprise.auth.platform.system.service.SystemManagementService;
import com.enterprise.auth.platform.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SystemManagementServiceTest {

    @Autowired
    private SystemManagementService systemManagementService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void shouldCreateAndDeleteDictInDatabaseMode() {
        TenantContext.setTenantId("platform");
        String dictCode = "k" + System.nanoTime();
        SystemManagementService.DictView created = systemManagementService.createDict(
                new DictCrudRequest("demo", dictCode, "v")
        );

        assertThat(systemManagementService.dicts()).extracting(SystemManagementService.DictView::dictCode).contains(dictCode);

        systemManagementService.deleteDict(created.id());

        assertThat(systemManagementService.dicts()).extracting(SystemManagementService.DictView::dictCode).doesNotContain(dictCode);
    }
}
