package com.enterprise.auth.platform.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.auth.platform.common.cache.CacheNames;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.menu.application.MenuTemplateQueryService;
import com.enterprise.auth.platform.modules.menu.infrastructure.entity.SysMenuEntity;
import com.enterprise.auth.platform.modules.menu.infrastructure.mapper.SysMenuMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.TenantProperties;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig
@ContextConfiguration(classes = MenuTemplateQueryServiceTest.TestConfig.class)
class MenuTemplateQueryServiceTest {

    @Autowired
    private MenuTemplateQueryService queryService;

    @Autowired
    private SysMenuMapper sysMenuMapper;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        reset(sysMenuMapper);
        cacheManager.getCache(CacheNames.MENU_TEMPLATE).clear();
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void shouldCacheTemplateReadsAcrossCallsAndRestoreTenantContext() {
        SysMenuEntity menu = new SysMenuEntity();
        menu.setId(1L);
        when(sysMenuMapper.selectList(any())).thenReturn(List.of(menu));
        TenantContext.setTenantId("tenant-a");

        List<SysMenuEntity> first = queryService.listTemplateMenus();
        List<SysMenuEntity> second = queryService.listTemplateMenus();

        assertThat(first).containsExactly(menu);
        assertThat(second).isSameAs(first);
        assertThat(TenantContext.getTenantId()).isEqualTo("tenant-a");
        verify(sysMenuMapper, times(1)).selectList(any());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    static class TestConfig {

        @Bean
        SysMenuMapper sysMenuMapper() {
            return mock(SysMenuMapper.class);
        }

        @Bean
        TenantProperties tenantProperties() {
            return new TenantProperties("X-Tenant-Id", "platform", true, List.of());
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(CacheNames.MENU_TEMPLATE);
        }

        @Bean
        MenuTemplateQueryService menuTemplateQueryService(
                SysMenuMapper sysMenuMapper,
                TenantProperties tenantProperties
        ) {
            return new MenuTemplateQueryService(sysMenuMapper, tenantProperties);
        }
    }
}
