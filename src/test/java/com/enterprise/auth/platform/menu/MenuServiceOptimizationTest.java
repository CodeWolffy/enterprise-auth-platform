package com.enterprise.auth.platform.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.auth.application.AuthPermissionSnapshotInvalidationService;
import com.enterprise.auth.platform.modules.menu.application.MenuService;
import com.enterprise.auth.platform.modules.menu.application.MenuTemplateQueryService;
import com.enterprise.auth.platform.modules.menu.application.RoleMenuReferencePort;
import com.enterprise.auth.platform.modules.menu.domain.MenuTreeNode;
import com.enterprise.auth.platform.modules.menu.domain.MenuType;
import com.enterprise.auth.platform.modules.menu.infrastructure.entity.SysMenuEntity;
import com.enterprise.auth.platform.modules.menu.infrastructure.mapper.SysMenuMapper;
import com.enterprise.auth.platform.modules.tenant.application.TenantMenuService;
import com.enterprise.auth.platform.modules.tenant.infrastructure.TenantProperties;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class MenuServiceOptimizationTest {

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void batchCreateActionsShouldReuseSingleTemplateSnapshot() {
        SysMenuMapper mapper = mock(SysMenuMapper.class);
        MenuTemplateQueryService templateQueryService = mock(MenuTemplateQueryService.class);
        AuthPermissionSnapshotInvalidationService invalidationService =
                mock(AuthPermissionSnapshotInvalidationService.class);
        MenuService menuService = new MenuService(
                mapper,
                mock(RoleMenuReferencePort.class),
                mock(ApplicationEventPublisher.class),
                mock(TenantMenuService.class),
                invalidationService,
                new TenantProperties("X-Tenant-Id", "platform", true, List.of()),
                templateQueryService
        );
        SysMenuEntity parent = menu(10L, null, MenuType.MENU, null, "/user", 1);
        SysMenuEntity existingButton = menu(11L, 10L, MenuType.BUTTON, "upms:user:get", null, 7);
        when(mapper.selectOne(any())).thenReturn(parent);
        when(templateQueryService.listTemplateMenus()).thenReturn(List.of(parent, existingButton));
        TenantContext.setTenantId("platform");

        List<MenuTreeNode> created = menuService.batchCreateActions(10L, List.of("edit", "del"));

        assertThat(created).extracting(MenuTreeNode::permission)
                .containsExactly("upms:user:edit", "upms:user:del");
        assertThat(created).extracting(MenuTreeNode::sort).containsExactly(8, 9);
        verify(templateQueryService, times(1)).listTemplateMenus();
        verify(mapper, never()).selectList(any());
        ArgumentCaptor<SysMenuEntity> inserted = ArgumentCaptor.forClass(SysMenuEntity.class);
        verify(mapper, times(2)).insert(inserted.capture());
        assertThat(inserted.getAllValues()).extracting(SysMenuEntity::getPermission)
                .containsExactly("upms:user:edit", "upms:user:del");
        verify(invalidationService).invalidateAll();
    }

    private static SysMenuEntity menu(
            Long id,
            Long parentId,
            MenuType type,
            String permission,
            String path,
            Integer sort
    ) {
        SysMenuEntity entity = new SysMenuEntity();
        entity.setId(id);
        entity.setParentId(parentId);
        entity.setType(type.value());
        entity.setName("测试菜单");
        entity.setPermission(permission);
        entity.setPath(path);
        entity.setSort(sort);
        entity.setDeleted(0);
        return entity;
    }
}
