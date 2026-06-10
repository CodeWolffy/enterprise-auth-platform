package com.enterprise.auth.platform.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.system.interfaces.DictCrudRequest;
import com.enterprise.auth.platform.modules.system.application.DictApplicationService;
import com.enterprise.auth.platform.modules.system.application.SystemViewModels;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DictApplicationServiceTest {

    @Autowired
    private DictApplicationService dictApplicationService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void shouldCreateAndDeleteDictInDatabaseMode() {
        TenantContext.setTenantId("platform");
        String dictType = "demo_" + System.nanoTime();
        SystemViewModels.DictView created = dictApplicationService.createDict(
                new DictCrudRequest(dictType, null, "演示字典")
        );

        assertThat(dictApplicationService.dicts(null, null, null, 1, 50, "createdAt", "asc").records())
                .extracting(SystemViewModels.DictView::dictType)
                .contains(dictType);

        dictApplicationService.deleteDict(created.id());

        assertThat(dictApplicationService.dicts(null, null, null, 1, 50, "createdAt", "asc").records())
                .extracting(SystemViewModels.DictView::dictType)
                .doesNotContain(dictType);
    }
}