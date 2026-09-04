package com.enterprise.auth.platform.modules.menu.application;

import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.menu.api.MenuNode;
import com.enterprise.auth.platform.modules.menu.api.MenuTenantGrantPort;
import com.enterprise.auth.platform.modules.menu.domain.MenuTreeNode;
import com.enterprise.auth.platform.modules.menu.domain.MenuType;
import com.enterprise.auth.platform.modules.menu.infrastructure.entity.SysMenuEntity;
import com.enterprise.auth.platform.common.context.TenantProperties;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

/**
 * Builds runtime menu trees and grant sets from the platform menu template.
 *
 * <p>This class deliberately has no persistence or cache side effects. Keeping
 * the tree algorithm separate makes the mutation service easier to review and
 * gives callers a single place for tenant-scope and ancestor semantics.</p>
 */
final class MenuTreeResolver {

    private static final String PLATFORM_TENANT = "platform";

    private final MenuTenantGrantPort tenantGrants;
    private final TenantProperties tenantProperties;

    MenuTreeResolver(MenuTenantGrantPort tenantGrants, TenantProperties tenantProperties) {
        this.tenantGrants = tenantGrants;
        this.tenantProperties = tenantProperties;
    }

    List<MenuTreeNode> templateTree(List<SysMenuEntity> template) {
        return template.stream().map(menu -> toMenuNode(menu, List.of())).toList();
    }

    Set<String> resolveGrantKeys(
            List<SysMenuEntity> template,
            String activeTenantId,
            Set<Long> grantedMenuIds,
            boolean superAdmin
    ) {
        Map<Long, SysMenuEntity> menuById = toMenuMap(template);
        if (menuById.isEmpty()) {
            return Set.of();
        }
        Set<Long> grantedIds = superAdmin
                ? new LinkedHashSet<>(menuById.keySet())
                : normalizeMenuIds(grantedMenuIds);
        if (grantedIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> grantableIds = tenantScopedMenuIds(activeTenantId, template);
        if (grantableIds.isEmpty()) {
            return Set.of();
        }
        return expandWithAncestors(grantedIds, menuById).stream()
                .filter(grantableIds::contains)
                .map(menuById::get)
                .filter(Objects::nonNull)
                .filter(menu -> hierarchyEnabled(menu, menuById))
                .filter(menu -> readMenuType(menu) == MenuType.BUTTON)
                .map(SysMenuEntity::getPermission)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    List<MenuNode> resolveMenuTree(
            List<SysMenuEntity> template,
            String activeTenantId,
            Set<Long> grantedMenuIds,
            boolean superAdmin
    ) {
        Map<Long, SysMenuEntity> menuById = toMenuMap(template);
        if (menuById.isEmpty()) {
            return List.of();
        }
        Set<Long> grantedIds = superAdmin
                ? new LinkedHashSet<>(menuById.keySet())
                : normalizeMenuIds(grantedMenuIds);
        if (grantedIds.isEmpty()) {
            return List.of();
        }

        Set<Long> expanded = expandWithAncestors(grantedIds, menuById);
        Set<Long> activeIds = tenantScopedMenuIds(activeTenantId, template);
        if (activeIds.isEmpty()) {
            return List.of();
        }
        expanded.retainAll(activeIds);

        Map<Long, RuntimeMenuNodeBuilder> nodes = new LinkedHashMap<>();
        for (Long menuId : expanded) {
            SysMenuEntity menu = menuById.get(menuId);
            if (menu == null || readMenuType(menu) != MenuType.MENU) {
                continue;
            }
            if (!hierarchyEnabled(menu, menuById)) {
                continue;
            }
            nodes.put(menu.getId(), toRuntimeMenuNode(menu));
        }
        if (nodes.isEmpty()) {
            return List.of();
        }

        List<RuntimeMenuNodeBuilder> roots = new ArrayList<>();
        for (RuntimeMenuNodeBuilder node : nodes.values()) {
            if (node.parentId == null || !nodes.containsKey(node.parentId)) {
                roots.add(node);
                continue;
            }
            nodes.get(node.parentId).children.add(node);
        }

        Comparator<RuntimeMenuNodeBuilder> comparator = Comparator
                .comparingInt((RuntimeMenuNodeBuilder node) -> node.sort == null ? Integer.MAX_VALUE : node.sort)
                .thenComparingLong(node -> node.id == null ? Long.MAX_VALUE : node.id);
        roots.sort(comparator);
        roots.forEach(root -> sortRuntimeChildrenRecursively(root, comparator));

        List<MenuNode> tree = roots.stream().map(RuntimeMenuNodeBuilder::toMenuNode).toList();
        if (tree.size() == 1 && "root".equals(tree.get(0).code())) {
            return tree.get(0).children();
        }
        return tree;
    }

    List<MenuTreeNode> grantableTree(List<SysMenuEntity> template, String activeTenantId) {
        Set<Long> grantableIds = tenantScopedMenuIds(activeTenantId, template);
        if (grantableIds.isEmpty()) {
            return List.of();
        }
        return template.stream()
                .filter(menu -> menu.getId() != null && grantableIds.contains(menu.getId()))
                .map(menu -> toMenuNode(menu, List.of()))
                .toList();
    }

    Set<Long> filterGrantableMenuIds(
            List<SysMenuEntity> template,
            String activeTenantId,
            Set<Long> menuIds
    ) {
        Set<Long> grantableIds = tenantScopedMenuIds(activeTenantId, template);
        if (grantableIds.isEmpty()) {
            return Set.of();
        }
        return normalizeMenuIds(menuIds).stream()
                .filter(grantableIds::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    Set<Long> expandMenuIdsWithAncestors(
            List<SysMenuEntity> template,
            String activeTenantId,
            Set<Long> requestedMenuIds
    ) {
        Map<Long, SysMenuEntity> menuById = toMenuMap(template);
        Set<Long> normalized = normalizeMenuIds(requestedMenuIds);
        Set<Long> grantableIds = tenantScopedMenuIds(activeTenantId, template);
        for (Long menuId : normalized) {
            if (!menuById.containsKey(menuId)) {
                throw new BusinessException("存在无效的菜单权限 ID");
            }
            if (!grantableIds.contains(menuId)) {
                throw new BusinessException("存在超出租户能力范围的菜单权限 ID");
            }
        }
        return expandWithAncestors(normalized, menuById).stream()
                .filter(grantableIds::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> tenantScopedMenuIds(String activeTenantId, List<SysMenuEntity> template) {
        if (platformTenantId().equals(activeTenantId)) {
            return template.stream()
                    .map(SysMenuEntity::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        return tenantGrants.findTenantMenuIds(activeTenantId);
    }

    private Set<Long> expandWithAncestors(Collection<Long> menuIds, Map<Long, SysMenuEntity> menuById) {
        Set<Long> expanded = new LinkedHashSet<>();
        for (Long menuId : menuIds) {
            SysMenuEntity menu = menuById.get(menuId);
            if (menu == null) {
                continue;
            }
            LinkedHashSet<Long> parentChain = new LinkedHashSet<>();
            Long parentId = menu.getParentId();
            while (parentId != null) {
                SysMenuEntity ancestor = menuById.get(parentId);
                if (ancestor == null) {
                    break;
                }
                parentChain.add(parentId);
                parentId = ancestor.getParentId();
            }
            expanded.addAll(parentChain);
            expanded.add(menuId);
        }
        return expanded;
    }

    private boolean hierarchyEnabled(SysMenuEntity menu, Map<Long, SysMenuEntity> menuById) {
        if (!isActive(menu)) {
            return false;
        }
        Long parentId = menu.getParentId();
        while (parentId != null) {
            SysMenuEntity ancestor = menuById.get(parentId);
            if (ancestor == null || !isActive(ancestor)) {
                return false;
            }
            parentId = ancestor.getParentId();
        }
        return true;
    }

    private Map<Long, SysMenuEntity> toMenuMap(List<SysMenuEntity> menus) {
        return menus.stream().collect(Collectors.toMap(
                SysMenuEntity::getId,
                value -> value,
                (left, right) -> right,
                LinkedHashMap::new
        ));
    }

    private MenuTreeNode toMenuNode(SysMenuEntity entity, List<MenuTreeNode> children) {
        return new MenuTreeNode(
                entity.getId(), readMenuType(entity).value(), entity.getName(),
                entity.getParentId(), entity.getPermission(), entity.getPath(), entity.getComponent(),
                entity.getRedirect(), entity.getIcon(), entity.getSort(),
                entity.getOuterStatus() != null && entity.getOuterStatus() == 1,
                entity.getApplicationKey(), children
        );
    }

    private RuntimeMenuNodeBuilder toRuntimeMenuNode(SysMenuEntity menu) {
        return new RuntimeMenuNodeBuilder(
                menu.getId(), menu.getParentId(), menu.getType(), menu.getName(), menu.getPath(),
                menu.getComponent(), menu.getPermission(), menu.getIcon(), menu.getSort(),
                menu.getOuterStatus() != null && menu.getOuterStatus() == 1
        );
    }

    private void sortRuntimeChildrenRecursively(
            RuntimeMenuNodeBuilder node,
            Comparator<RuntimeMenuNodeBuilder> comparator
    ) {
        node.children.sort(comparator);
        for (RuntimeMenuNodeBuilder child : node.children) {
            sortRuntimeChildrenRecursively(child, comparator);
        }
    }

    private Set<Long> normalizeMenuIds(Set<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return Set.of();
        }
        return menuIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean isActive(SysMenuEntity entity) {
        return entity.getDeleted() == null || entity.getDeleted() == 0;
    }

    private MenuType readMenuType(SysMenuEntity entity) {
        return MenuType.fromValue(entity.getType());
    }

    private String platformTenantId() {
        return StringUtils.hasText(tenantProperties.platformTenantId())
                ? tenantProperties.platformTenantId()
                : PLATFORM_TENANT;
    }

    private static final class RuntimeMenuNodeBuilder {
        private final Long id;
        private final Long parentId;
        private final String code;
        private final String title;
        private final String path;
        private final String component;
        private final String permission;
        private final String icon;
        private final Integer sort;
        private final boolean outerStatus;
        private final List<RuntimeMenuNodeBuilder> children = new ArrayList<>();

        private RuntimeMenuNodeBuilder(
                Long id,
                Long parentId,
                String code,
                String title,
                String path,
                String component,
                String permission,
                String icon,
                Integer sort,
                boolean outerStatus
        ) {
            this.id = id;
            this.parentId = parentId;
            this.code = code;
            this.title = title;
            this.path = path;
            this.component = component;
            this.permission = permission;
            this.icon = icon;
            this.sort = sort;
            this.outerStatus = outerStatus;
        }

        private MenuNode toMenuNode() {
            return new MenuNode(
                    id, code, title, title, path, component, permission, icon, sort, outerStatus,
                    children.stream().map(RuntimeMenuNodeBuilder::toMenuNode).toList()
            );
        }
    }
}
