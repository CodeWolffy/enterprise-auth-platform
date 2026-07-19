package com.enterprise.auth.platform.modules.menu.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.auth.platform.modules.auth.interfaces.MenuNode;
import com.enterprise.auth.platform.modules.menu.domain.MenuType;
import com.enterprise.auth.platform.modules.menu.infrastructure.entity.SysMenuEntity;
import com.enterprise.auth.platform.modules.tenant.application.TenantMenuService;
import com.enterprise.auth.platform.modules.tenant.infrastructure.TenantProperties;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MenuTreeResolverTest {

    @Test
    void shouldExpandAncestorsAndKeepTenantScopedButtonGrant() {
        TenantMenuService tenantMenuService = mock(TenantMenuService.class);
        MenuTreeResolver resolver = new MenuTreeResolver(
                tenantMenuService,
                new TenantProperties("X-Tenant-Id", "platform", true, List.of())
        );
        SysMenuEntity root = menu(1L, null, MenuType.MENU, null, "root", 1);
        SysMenuEntity customer = menu(2L, 1L, MenuType.MENU, null, "customer", 2);
        SysMenuEntity read = menu(3L, 2L, MenuType.BUTTON, "crm:customer:read", null, 1);
        List<SysMenuEntity> template = List.of(root, customer, read);
        when(tenantMenuService.findTenantMenuIds("tenant-a")).thenReturn(Set.of(1L, 2L, 3L));

        assertThat(resolver.resolveGrantKeys(template, "tenant-a", Set.of(3L), false))
                .containsExactly("crm:customer:read");

        List<MenuNode> tree = resolver.resolveMenuTree(template, "tenant-a", Set.of(3L), false);
        assertThat(tree).singleElement().satisfies(node -> {
            assertThat(node.code()).isEqualTo(MenuType.MENU.value());
            assertThat(node.children()).singleElement().satisfies(customerNode -> {
                assertThat(customerNode.code()).isEqualTo(MenuType.MENU.value());
                assertThat(customerNode.children()).isEmpty();
            });
        });
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
        entity.setName(path == null ? "按钮" : path);
        entity.setPermission(permission);
        entity.setPath(path);
        entity.setSort(sort);
        entity.setDeleted(0);
        return entity;
    }
}
