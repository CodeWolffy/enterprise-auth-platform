package com.enterprise.auth.platform.modules.menu.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.cache.CacheNames;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.auth.application.AuthPermissionSnapshotInvalidationService;
import com.enterprise.auth.platform.modules.auth.interfaces.MenuNode;
import com.enterprise.auth.platform.modules.menu.domain.MenuTreeNode;
import com.enterprise.auth.platform.modules.menu.domain.MenuType;
import com.enterprise.auth.platform.modules.menu.infrastructure.entity.SysMenuEntity;
import com.enterprise.auth.platform.modules.menu.infrastructure.mapper.SysMenuMapper;
import com.enterprise.auth.platform.modules.menu.interfaces.CreateMenuRequest;
import com.enterprise.auth.platform.modules.role.application.RoleMenuReferenceFacade;
import com.enterprise.auth.platform.modules.tenant.application.TenantCapabilityResourceScopeFacade;
import com.enterprise.auth.platform.modules.tenant.application.TenantMenuService;
import com.enterprise.auth.platform.modules.tenant.infrastructure.TenantProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MenuService {

    private static final String PLATFORM_TENANT = "platform";
    private static final Map<String, String> ACTION_LABELS = Map.of(
            "add", "新增",
            "edit", "修改",
            "del", "删除",
            "page", "列表",
            "get", "查询"
    );

    private final SysMenuMapper sysMenuMapper;
    private final RoleMenuReferenceFacade roleMenuReferenceFacade;
    private final ApplicationEventPublisher eventPublisher;
    private final TenantCapabilityResourceScopeFacade tenantCapabilityResourceScopeFacade;
    private final TenantMenuService tenantMenuService;
    private final AuthPermissionSnapshotInvalidationService permissionSnapshotInvalidationService;
    private final TenantProperties tenantProperties;

    public MenuService(
            SysMenuMapper sysMenuMapper,
            RoleMenuReferenceFacade roleMenuReferenceFacade,
            ApplicationEventPublisher eventPublisher,
            TenantCapabilityResourceScopeFacade tenantCapabilityResourceScopeFacade,
            TenantMenuService tenantMenuService,
            AuthPermissionSnapshotInvalidationService permissionSnapshotInvalidationService,
            TenantProperties tenantProperties
    ) {
        this.sysMenuMapper = sysMenuMapper;
        this.roleMenuReferenceFacade = roleMenuReferenceFacade;
        this.eventPublisher = eventPublisher;
        this.tenantCapabilityResourceScopeFacade = tenantCapabilityResourceScopeFacade;
        this.tenantMenuService = tenantMenuService;
        this.permissionSnapshotInvalidationService = permissionSnapshotInvalidationService;
        this.tenantProperties = tenantProperties;
    }

    public List<MenuTreeNode> templateTree() {
        List<SysMenuEntity> menus = listTemplateMenus();
        return menus.stream()
                .map(m -> toMenuNode(m, List.of()))
                .toList();
    }

    public Set<String> resolveGrantKeys(String activeTenantId, Set<Long> grantedMenuIds, boolean superAdmin) {
        List<SysMenuEntity> template = listTemplateMenus();
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
        Set<Long> grantableIds = tenantCapabilityResourceScopeFacade.grantableMenuIds(activeTenantId, template);
        if (grantableIds.isEmpty()) {
            return Set.of();
        }
        return expandWithAncestors(grantedIds, menuById).stream()
                .filter(grantableIds::contains)
                .map(menuById::get)
                .filter(Objects::nonNull)
                .filter(menu -> hierarchyEnabled(menu, menuById))
                .filter(menu -> parseType(menu.getMenuType()) == MenuType.BUTTON)
                .map(SysMenuEntity::getGrantKey)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<String> resolveGrantKeys(Set<Long> grantedMenuIds, boolean superAdmin) {
        return resolveGrantKeys(platformTenantId(), grantedMenuIds, superAdmin);
    }

    public List<MenuNode> resolveMenuTree(String activeTenantId, Set<Long> grantedMenuIds, boolean superAdmin) {
        List<SysMenuEntity> template = listTemplateMenus();
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
        Set<Long> visibleIds = tenantCapabilityResourceScopeFacade.visibleMenuIds(activeTenantId, template);
        if (visibleIds.isEmpty()) {
            return List.of();
        }
        expanded.retainAll(visibleIds);
        // 非平台租户需与租户分配的菜单取交集（对齐 haorong-mall sys_tenant_menu 模型）
        if (!platformTenantId().equals(activeTenantId)) {
            Set<Long> tenantMenuIds = tenantMenuService.findTenantMenuIds(activeTenantId);
            if (tenantMenuIds.isEmpty()) {
                return List.of();
            }
            expanded.retainAll(tenantMenuIds);
        }
        Map<Long, RuntimeMenuNodeBuilder> nodes = new LinkedHashMap<>();
        for (Long menuId : expanded) {
            SysMenuEntity menu = menuById.get(menuId);
            if (menu == null || !isRouteNode(menu)) {
                continue;
            }
            if (!hierarchyEnabled(menu, menuById) || !hierarchyVisible(menu, menuById)) {
                continue;
            }
            nodes.put(menu.getId(), RuntimeMenuNodeBuilder.from(menu));
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
                .comparingInt((RuntimeMenuNodeBuilder node) -> node.orderNo == null ? Integer.MAX_VALUE : node.orderNo)
                .thenComparingLong(node -> node.id == null ? Long.MAX_VALUE : node.id);
        roots.sort(comparator);
        roots.forEach(root -> sortRuntimeChildrenRecursively(root, comparator));

        List<MenuNode> tree = roots.stream().map(RuntimeMenuNodeBuilder::toMenuNode).toList();
        if (tree.size() == 1 && "root".equals(tree.get(0).code())) {
            return tree.get(0).children();
        }
        return tree;
    }

    public List<MenuNode> resolveMenuTree(Set<Long> grantedMenuIds, boolean superAdmin) {
        return resolveMenuTree(platformTenantId(), grantedMenuIds, superAdmin);
    }

    public List<MenuTreeNode> grantableTree(String activeTenantId) {
        List<SysMenuEntity> template = listTemplateMenus();
        Set<Long> grantableIds = tenantCapabilityResourceScopeFacade.grantableMenuIds(activeTenantId, template);
        if (grantableIds.isEmpty()) {
            return List.of();
        }
        return template.stream()
                .filter(menu -> menu.getId() != null && grantableIds.contains(menu.getId()))
                .map(m -> toMenuNode(m, List.of()))
                .toList();
    }

    public Set<Long> filterGrantableMenuIds(String activeTenantId, Set<Long> menuIds) {
        List<SysMenuEntity> template = listTemplateMenus();
        Set<Long> grantableIds = tenantCapabilityResourceScopeFacade.grantableMenuIds(activeTenantId, template);
        if (grantableIds.isEmpty()) {
            return Set.of();
        }
        return normalizeMenuIds(menuIds).stream()
                .filter(grantableIds::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<Long> expandMenuIdsWithAncestors(String activeTenantId, Set<Long> requestedMenuIds) {
        List<SysMenuEntity> template = listTemplateMenus();
        Map<Long, SysMenuEntity> menuById = toMenuMap(template);
        Set<Long> normalized = normalizeMenuIds(requestedMenuIds);
        Set<Long> grantableIds = tenantCapabilityResourceScopeFacade.grantableMenuIds(activeTenantId, template);
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

    public Set<Long> expandMenuIdsWithAncestors(Set<Long> requestedMenuIds) {
        return expandMenuIdsWithAncestors(platformTenantId(), requestedMenuIds);
    }

    public boolean existsMenu(Long menuId) {
        if (menuId == null) {
            return false;
        }
        return toMenuMap(listTemplateMenus()).containsKey(menuId);
    }

    public MenuTreeNode detail(Long menuId) {
        return toMenuNode(getMenu(menuId), List.of());
    }

    @Transactional
    @CacheEvict(value = CacheNames.MENU_TEMPLATE, allEntries = true)
    public MenuTreeNode create(CreateMenuRequest request) {
        requirePlatformTenant();
        SysMenuEntity parent = request.parentId() == null ? null : getMenu(request.parentId());
        validateMenuShape(request.menuType(), parent, request.grantKey());
        validateGrantKey(request.menuType(), request.grantKey());
        validateUniqueKeys(null, request.resourceKey(), request.routeKey(), request.path());

        SysMenuEntity entity = new SysMenuEntity();
        entity.setTenantId(platformTenantId());
        entity.setParentId(request.parentId());
        entity.setAncestors(resolveAncestors(parent));
        entity.setMenuType(request.menuType().value());
        entity.setResourceKey(request.resourceKey().trim());
        entity.setMenuName(request.menuName().trim());
        entity.setRouteKey(request.menuType() == MenuType.MENU ? blankToNull(request.routeKey()) : null);
        entity.setGrantKey(request.menuType() == MenuType.BUTTON ? blankToNull(request.grantKey()) : null);
        entity.setPath(request.menuType() == MenuType.MENU ? blankToNull(request.path()) : null);
        entity.setComponent(request.menuType() == MenuType.MENU ? blankToNull(request.component()) : null);
        entity.setRedirect(request.menuType() == MenuType.MENU ? blankToNull(request.redirect()) : null);
        entity.setIcon(request.menuType() == MenuType.MENU ? blankToNull(request.icon()) : null);
        entity.setOrderNo(request.orderNo() == null ? 0 : request.orderNo());
        entity.setVisible(Boolean.FALSE.equals(request.visible()) ? 0 : 1);
        entity.setEnabled(Boolean.FALSE.equals(request.enabled()) ? 0 : 1);
        entity.setIsSystem(0);
        entity.setOuterStatus(Boolean.TRUE.equals(request.outerStatus()) ? 1 : 0);
        entity.setApplicationKey(request.applicationKey());

        try {
            runWithPlatformTenant(() -> {
                sysMenuMapper.insert(entity);
                return null;
            });
        } catch (RuntimeException ex) {
            throw new BusinessException("菜单权限键已存在或数据不合法");
        }
        evictPrincipalSnapshots();
        return toMenuNode(entity, List.of());
    }

    @Transactional
    @CacheEvict(value = CacheNames.MENU_TEMPLATE, allEntries = true)
    public MenuTreeNode update(Long menuId, CreateMenuRequest request) {
        return update(menuId, request, true);
    }

    @Transactional
    @CacheEvict(value = CacheNames.MENU_TEMPLATE, allEntries = true)
    public MenuTreeNode update(Long menuId, CreateMenuRequest request, boolean parentIdPresent) {
        requirePlatformTenant();
        SysMenuEntity entity = getMenu(menuId);
        Long nextParentId = parentIdPresent ? request.parentId() : entity.getParentId();
        CreateMenuRequest normalizedRequest = new CreateMenuRequest(
                nextParentId,
                request.menuType(),
                request.resourceKey(),
                request.menuName(),
                request.routeKey(),
                request.grantKey(),
                request.path(),
                request.component(),
                request.redirect(),
                request.icon(),
                request.orderNo(),
                request.visible(),
                request.enabled(),
                request.outerStatus(),
                request.applicationKey()
        );
        validateSystemMenuMutation(entity, normalizedRequest);

        SysMenuEntity parent = nextParentId == null ? null : getMenu(nextParentId);
        if (parent != null && parent.getId().equals(menuId)) {
            throw new BusinessException("父节点不能是自身");
        }
        if (parent != null && containsDescendant(parent.getAncestors(), menuId)) {
            throw new BusinessException("父节点不能是当前节点的子孙节点");
        }
        validateMenuShape(normalizedRequest.menuType(), parent, normalizedRequest.grantKey());
        validateGrantKey(normalizedRequest.menuType(), normalizedRequest.grantKey());
        validateUniqueKeys(menuId, normalizedRequest.resourceKey(), normalizedRequest.routeKey(), normalizedRequest.path());

        entity.setParentId(normalizedRequest.parentId());
        entity.setAncestors(resolveAncestors(parent));
        entity.setMenuType(normalizedRequest.menuType().value());
        entity.setResourceKey(normalizedRequest.resourceKey().trim());
        entity.setMenuName(normalizedRequest.menuName().trim());
        entity.setRouteKey(normalizedRequest.menuType() == MenuType.MENU ? blankToNull(normalizedRequest.routeKey()) : null);
        entity.setGrantKey(normalizedRequest.menuType() == MenuType.BUTTON ? blankToNull(normalizedRequest.grantKey()) : null);
        entity.setPath(normalizedRequest.menuType() == MenuType.MENU ? blankToNull(normalizedRequest.path()) : null);
        entity.setComponent(normalizedRequest.menuType() == MenuType.MENU ? blankToNull(normalizedRequest.component()) : null);
        entity.setRedirect(normalizedRequest.menuType() == MenuType.MENU ? blankToNull(normalizedRequest.redirect()) : null);
        entity.setIcon(normalizedRequest.menuType() == MenuType.MENU ? blankToNull(normalizedRequest.icon()) : null);
        entity.setOrderNo(normalizedRequest.orderNo() == null ? 0 : normalizedRequest.orderNo());
        entity.setVisible(Boolean.FALSE.equals(normalizedRequest.visible()) ? 0 : 1);
        entity.setEnabled(Boolean.FALSE.equals(normalizedRequest.enabled()) ? 0 : 1);
        entity.setOuterStatus(Boolean.TRUE.equals(normalizedRequest.outerStatus()) ? 1 : 0);
        entity.setApplicationKey(normalizedRequest.applicationKey());

        try {
            runWithPlatformTenant(() -> {
                sysMenuMapper.updateById(entity);
                return null;
            });
        } catch (RuntimeException ex) {
            throw new BusinessException("菜单权限键已存在或数据不合法");
        }

        refreshDescendantAncestors(menuId);
        evictPrincipalSnapshots();
        return toMenuNode(entity, List.of());
    }

    @Transactional
    @CacheEvict(value = CacheNames.MENU_TEMPLATE, allEntries = true)
    public List<MenuTreeNode> batchCreateActions(Long menuId, List<String> actions) {
        requirePlatformTenant();
        SysMenuEntity parent = getMenu(menuId);
        if (parseType(parent.getMenuType()) != MenuType.MENU) {
            throw new BusinessException("只有菜单节点可以批量生成按钮权限");
        }
        List<String> normalizedActions = normalizeActions(actions);
        if (normalizedActions.isEmpty()) {
            throw new BusinessException("请选择要生成的按钮权限");
        }
        List<MenuTreeNode> created = new ArrayList<>();
        int orderNo = nextChildOrderNo(menuId);
        for (String action : normalizedActions) {
            String grantKey = resolveActionGrantKey(parent, action);
            validateGrantKey(MenuType.BUTTON, grantKey);
            String resourceKey = resolveActionResourceKey(grantKey, action);
            ensureActionNotExists(resourceKey, grantKey);
            SysMenuEntity entity = new SysMenuEntity();
            entity.setTenantId(platformTenantId());
            entity.setParentId(parent.getId());
            entity.setAncestors(resolveAncestors(parent));
            entity.setMenuType(MenuType.BUTTON.value());
            entity.setResourceKey(resourceKey);
            entity.setMenuName(parent.getMenuName() + ACTION_LABELS.getOrDefault(action, action));
            entity.setRouteKey(null);
            entity.setGrantKey(grantKey);
            entity.setPath(null);
            entity.setComponent(null);
            entity.setRedirect(null);
            entity.setIcon(null);
            entity.setOrderNo(orderNo++);
            entity.setVisible(1);
            entity.setEnabled(1);
            entity.setIsSystem(0);
            entity.setOuterStatus(0);
            entity.setApplicationKey(parent.getApplicationKey());
            runWithPlatformTenant(() -> {
                sysMenuMapper.insert(entity);
                return null;
            });
            created.add(toMenuNode(entity, List.of()));
        }
        evictPrincipalSnapshots();
        return created;
    }

    @Transactional
    @CacheEvict(value = CacheNames.MENU_TEMPLATE, allEntries = true)
    public void delete(Long menuId) {
        requirePlatformTenant();
        SysMenuEntity entity = getMenu(menuId);
        if (entity.getIsSystem() != null && entity.getIsSystem() == 1) {
            throw new BusinessException("系统菜单权限不允许删除");
        }

        long children = runWithPlatformTenant(() ->
                sysMenuMapper.selectCount(new LambdaQueryWrapper<SysMenuEntity>()
                        .eq(SysMenuEntity::getTenantId, platformTenantId())
                        .eq(SysMenuEntity::getDeleted, 0)
                        .eq(SysMenuEntity::getParentId, menuId)));

        if (children > 0) {
            throw new BusinessException("请先删除子节点");
        }
        long roleBindings = roleMenuReferenceFacade.countMenuReferencesAcrossTenants(menuId);
        if (roleBindings > 0) {
            throw new BusinessException("菜单已被角色授权引用，暂不允许删除");
        }

        runWithPlatformTenant(() -> {
            sysMenuMapper.deleteById(menuId);
            return null;
        });
        eventPublisher.publishEvent(new MenuDeletedEvent(menuId));
        evictPrincipalSnapshots();
    }

    @Transactional
    @CacheEvict(value = CacheNames.MENU_TEMPLATE, allEntries = true)
    public MenuTreeNode updateSort(Long menuId, Integer orderNo) {
        requirePlatformTenant();
        validateOrderNo(orderNo);
        SysMenuEntity entity = getMenu(menuId);
        entity.setOrderNo(orderNo == null ? 0 : orderNo);
        runWithPlatformTenant(() -> {
            sysMenuMapper.updateById(entity);
            return null;
        });
        evictPrincipalSnapshots();
        return toMenuNode(entity, List.of());
    }

    @Cacheable(value = CacheNames.MENU_TEMPLATE, unless = "#result.isEmpty()")
    private List<SysMenuEntity> listTemplateMenus() {
        return runWithPlatformTenant(() ->
                sysMenuMapper.selectList(new LambdaQueryWrapper<SysMenuEntity>()
                        .eq(SysMenuEntity::getTenantId, platformTenantId())
                        .eq(SysMenuEntity::getDeleted, 0)
                        .orderByAsc(SysMenuEntity::getOrderNo)
                        .orderByAsc(SysMenuEntity::getId)));
    }

    private SysMenuEntity getMenu(Long menuId) {
        SysMenuEntity entity = runWithPlatformTenant(() ->
                sysMenuMapper.selectOne(new LambdaQueryWrapper<SysMenuEntity>()
                        .eq(SysMenuEntity::getTenantId, platformTenantId())
                        .eq(SysMenuEntity::getId, menuId)
                        .eq(SysMenuEntity::getDeleted, 0)
                        .last("limit 1")));
        if (entity == null) {
            throw new BusinessException("菜单权限节点不存在");
        }
        return entity;
    }

    private MenuTreeNode toMenuNode(SysMenuEntity entity, List<MenuTreeNode> children) {
        return new MenuTreeNode(
                entity.getId(), parseType(entity.getMenuType()).value(), entity.getResourceKey(), entity.getMenuName(),
                entity.getParentId(), entity.getAncestors(),
                entity.getRouteKey(), entity.getGrantKey(), entity.getGrantKey(),
                entity.getPath(), entity.getComponent(),
                entity.getRedirect(), entity.getIcon(), entity.getOrderNo(),
                entity.getVisible() == null || entity.getVisible() == 1,
                entity.getEnabled() == null || entity.getEnabled() == 1,
                entity.getIsSystem() != null && entity.getIsSystem() == 1,
                entity.getOuterStatus() != null && entity.getOuterStatus() == 1,
                entity.getApplicationKey(), children
        );
    }

    private List<String> normalizeActions(List<String> actions) {
        if (actions == null || actions.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String action : actions) {
            String normalized = blankToNull(action);
            if (normalized == null) {
                continue;
            }
            normalized = normalized.toLowerCase();
            if (!ACTION_LABELS.containsKey(normalized)) {
                throw new BusinessException("不支持的按钮动作：" + action);
            }
            if (!result.contains(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    private int nextChildOrderNo(Long menuId) {
        return runWithPlatformTenant(() -> sysMenuMapper.selectList(new LambdaQueryWrapper<SysMenuEntity>()
                        .eq(SysMenuEntity::getTenantId, platformTenantId())
                        .eq(SysMenuEntity::getDeleted, 0)
                        .eq(SysMenuEntity::getParentId, menuId))
                .stream()
                .map(SysMenuEntity::getOrderNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .map(value -> value + 1)
                .orElse(1));
    }

    private String resolveActionGrantKey(SysMenuEntity parent, String action) {
        String existingPrefix = findExistingActionPermissionPrefix(parent.getId());
        if (existingPrefix != null) {
            return existingPrefix + ":" + action;
        }
        String resourceKey = blankToNull(parent.getResourceKey());
        String prefix = permissionPrefixFromResourceKey(resourceKey);
        if (prefix == null) {
            throw new BusinessException("资源标识不合法，无法生成按钮权限");
        }
        return prefix + ":" + action;
    }

    private String resolveActionResourceKey(String grantKey, String action) {
        String normalizedGrantKey = blankToNull(grantKey);
        if (normalizedGrantKey == null) {
            throw new BusinessException("授权键不合法，无法生成资源标识");
        }
        String[] segments = normalizedGrantKey.split(":");
        if (segments.length < 3) {
            throw new BusinessException("授权键不合法，无法生成资源标识");
        }
        return segments[segments.length - 2] + "." + action;
    }

    private String permissionPrefixFromResourceKey(String resourceKey) {
        if (!StringUtils.hasText(resourceKey)) {
            return null;
        }
        String normalized = resourceKey.trim();
        int actionSeparator = normalized.lastIndexOf('.');
        if (actionSeparator > 0) {
            normalized = normalized.substring(0, actionSeparator);
        }
        if (normalized.contains("-")) {
            return null;
        }
        if (!normalized.matches("^[a-zA-Z0-9_]+(?::[a-zA-Z0-9_]+)?$")) {
            return null;
        }
        return normalized.contains(":") ? normalized : "upms:" + normalized;
    }

    private String findExistingActionPermissionPrefix(Long parentId) {
        return listTemplateMenus().stream()
                .filter(menu -> Objects.equals(parentId, menu.getParentId()))
                .filter(menu -> Objects.equals(MenuType.BUTTON.value(), menu.getMenuType()))
                .map(SysMenuEntity::getGrantKey)
                .map(MenuService::blankToNull)
                .filter(Objects::nonNull)
                .map(grantKey -> {
                    int lastSeparator = grantKey.lastIndexOf(':');
                    return lastSeparator > 0 ? grantKey.substring(0, lastSeparator) : null;
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private void ensureActionNotExists(String resourceKey, String grantKey) {
        String normalizedResourceKey = blankToNull(resourceKey);
        String normalizedGrantKey = blankToNull(grantKey);
        boolean exists = listTemplateMenus().stream().anyMatch(menu ->
                Objects.equals(normalizedResourceKey, blankToNull(menu.getResourceKey()))
                        || (Objects.equals(MenuType.BUTTON.value(), menu.getMenuType())
                        && Objects.equals(normalizedGrantKey, blankToNull(menu.getGrantKey())))
        );
        if (exists) {
            throw new BusinessException("按钮权限已存在，请勿重复生成");
        }
    }

    private void validateMenuShape(MenuType childType, SysMenuEntity parent, String grantKey) {
        if (childType == MenuType.BUTTON) {
            if (parent == null || parseType(parent.getMenuType()) != MenuType.MENU) {
                throw new BusinessException("按钮权限必须挂在菜单节点下");
            }
            if (!StringUtils.hasText(grantKey)) {
                throw new BusinessException("按钮权限必须配置授权键");
            }
            return;
        }
        if (StringUtils.hasText(grantKey)) {
            throw new BusinessException("菜单节点不承载按钮权限");
        }
        if (parent != null && parseType(parent.getMenuType()) == MenuType.BUTTON) {
            throw new BusinessException("按钮权限不能作为父节点");
        }
    }

    private void validateGrantKey(MenuType menuType, String grantKey) {
        if (!StringUtils.hasText(grantKey)) {
            return;
        }
        if (menuType != MenuType.BUTTON) {
            throw new BusinessException("只有按钮节点可以配置授权键");
        }
        if (!grantKey.matches("^[a-zA-Z0-9]+:[a-zA-Z0-9]+:[a-zA-Z0-9_-]+$")) {
            throw new BusinessException("授权键格式不合法");
        }
    }

    private void validateUniqueKeys(Long currentMenuId, String resourceKey, String routeKey, String path) {
        if (!StringUtils.hasText(resourceKey)) {
            throw new BusinessException("资源唯一标识不能为空");
        }
        String normalizedResourceKey = resourceKey.trim();
        String normalizedRouteKey = blankToNull(routeKey);
        String normalizedPath = blankToNull(path);
        for (SysMenuEntity menu : listTemplateMenus()) {
            if (currentMenuId != null && Objects.equals(menu.getId(), currentMenuId)) {
                continue;
            }
            if (normalizedResourceKey.equals(blankToNull(menu.getResourceKey()))) {
                throw new BusinessException("资源唯一标识已存在");
            }
            if (normalizedRouteKey != null && normalizedRouteKey.equals(blankToNull(menu.getRouteKey()))) {
                throw new BusinessException("路由标识已存在");
            }
            if (normalizedPath != null && normalizedPath.equals(blankToNull(menu.getPath()))) {
                throw new BusinessException("访问路径已存在");
            }
        }
    }

    private void validateSystemMenuMutation(SysMenuEntity entity, CreateMenuRequest request) {
        if (entity.getIsSystem() == null || entity.getIsSystem() != 1) {
            return;
        }
        if (!Objects.equals(parseType(entity.getMenuType()), request.menuType())) {
            throw new BusinessException("系统节点不允许修改类型");
        }
        if (!Objects.equals(entity.getParentId(), request.parentId())) {
            throw new BusinessException("系统节点不允许修改父节点");
        }
        if (!Objects.equals(blankToNull(entity.getResourceKey()), blankToNull(request.resourceKey()))) {
            throw new BusinessException("系统节点不允许修改资源唯一标识");
        }
        if (!Objects.equals(blankToNull(entity.getRouteKey()), blankToNull(request.routeKey()))) {
            throw new BusinessException("系统节点不允许修改路由标识");
        }
        if (!Objects.equals(blankToNull(entity.getGrantKey()), blankToNull(request.grantKey()))) {
            throw new BusinessException("系统节点不允许修改授权键");
        }
        if (!Objects.equals(blankToNull(entity.getPath()), blankToNull(request.path()))) {
            throw new BusinessException("系统节点不允许修改访问路径");
        }
        if (!Objects.equals(blankToNull(entity.getComponent()), blankToNull(request.component()))) {
            throw new BusinessException("系统节点不允许修改组件名");
        }
    }

    private void validateOrderNo(Integer orderNo) {
        if (orderNo == null) {
            return;
        }
        if (orderNo < 0 || orderNo > 9999) {
            throw new BusinessException("排序值必须在 0 到 9999 之间");
        }
    }

    private void requirePlatformTenant() {
        if (!platformTenantId().equals(currentTenantId())) {
            throw new BusinessException("仅平台租户允许维护菜单模板");
        }
    }

    private void evictPrincipalSnapshots() {
        permissionSnapshotInvalidationService.invalidateAll();
    }

    private String currentTenantId() {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : platformTenantId();
    }

    private String platformTenantId() {
        return StringUtils.hasText(tenantProperties.platformTenantId()) ? tenantProperties.platformTenantId() : PLATFORM_TENANT;
    }

    private boolean isRouteNode(SysMenuEntity menu) {
        return parseType(menu.getMenuType()) == MenuType.MENU;
    }

    private boolean hierarchyEnabled(SysMenuEntity menu, Map<Long, SysMenuEntity> menuById) {
        if (menu.getEnabled() == null || menu.getEnabled() != 1) {
            return false;
        }
        for (Long ancestorId : parseAncestors(menu.getAncestors())) {
            SysMenuEntity ancestor = menuById.get(ancestorId);
            if (ancestor == null || ancestor.getEnabled() == null || ancestor.getEnabled() != 1) {
                return false;
            }
        }
        return true;
    }

    private boolean hierarchyVisible(SysMenuEntity menu, Map<Long, SysMenuEntity> menuById) {
        if (menu.getVisible() == null || menu.getVisible() != 1) {
            return false;
        }
        for (Long ancestorId : parseAncestors(menu.getAncestors())) {
            SysMenuEntity ancestor = menuById.get(ancestorId);
            if (ancestor == null || ancestor.getVisible() == null || ancestor.getVisible() != 1) {
                return false;
            }
        }
        return true;
    }

    private Set<Long> normalizeMenuIds(Set<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return Set.of();
        }
        return menuIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> expandWithAncestors(Collection<Long> menuIds, Map<Long, SysMenuEntity> menuById) {
        Set<Long> expanded = new LinkedHashSet<>();
        for (Long menuId : menuIds) {
            SysMenuEntity menu = menuById.get(menuId);
            if (menu == null) {
                continue;
            }
            expanded.addAll(parseAncestors(menu.getAncestors()));
            expanded.add(menuId);
        }
        return expanded;
    }

    private Map<Long, SysMenuEntity> toMenuMap(List<SysMenuEntity> menus) {
        return menus.stream().collect(Collectors.toMap(
                SysMenuEntity::getId,
                value -> value,
                (left, right) -> right,
                LinkedHashMap::new
        ));
    }

    private String resolveAncestors(SysMenuEntity parent) {
        if (parent == null) {
            return "";
        }
        String p = parent.getAncestors() == null ? "" : parent.getAncestors();
        if (p.isEmpty()) {
            return String.valueOf(parent.getId());
        }
        return p + "," + parent.getId();
    }

    private void refreshDescendantAncestors(Long menuId) {
        List<SysMenuEntity> children = runWithPlatformTenant(() ->
                sysMenuMapper.selectList(new LambdaQueryWrapper<SysMenuEntity>()
                        .eq(SysMenuEntity::getTenantId, platformTenantId())
                        .eq(SysMenuEntity::getParentId, menuId)
                        .eq(SysMenuEntity::getDeleted, 0)));
        if (children.isEmpty()) {
            return;
        }
        SysMenuEntity parent = getMenu(menuId);
        String ancestors = resolveAncestors(parent);
        for (SysMenuEntity child : children) {
            child.setAncestors(ancestors);
            runWithPlatformTenant(() -> {
                sysMenuMapper.updateById(child);
                return null;
            });
            refreshDescendantAncestors(child.getId());
        }
    }

    private boolean containsDescendant(String ancestors, Long menuId) {
        if (ancestors == null || ancestors.isEmpty()) {
            return false;
        }
        return Arrays.stream(ancestors.split(",")).anyMatch(id -> id.equals(String.valueOf(menuId)));
    }

    private Set<Long> parseAncestors(String ancestors) {
        if (!StringUtils.hasText(ancestors)) {
            return Set.of();
        }
        return Arrays.stream(ancestors.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> {
                    try {
                        return Long.parseLong(value);
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private MenuType parseType(String value) {
        return MenuType.fromValue(value);
    }

    private <T> T runWithPlatformTenant(Supplier<T> supplier) {
        return runWithTenant(platformTenantId(), supplier);
    }

    private <T> T runWithTenant(String tenantId, Supplier<T> supplier) {
        String previous = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(StringUtils.hasText(tenantId) ? tenantId : platformTenantId());
            return supplier.get();
        } finally {
            if (StringUtils.hasText(previous)) {
                TenantContext.setTenantId(previous);
            } else {
                TenantContext.clear();
            }
        }
    }

    private void sortRuntimeChildrenRecursively(RuntimeMenuNodeBuilder node, Comparator<RuntimeMenuNodeBuilder> comparator) {
        node.children.sort(comparator);
        for (RuntimeMenuNodeBuilder child : node.children) {
            sortRuntimeChildrenRecursively(child, comparator);
        }
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private static final class RuntimeMenuNodeBuilder {
        private final Long id;
        private final Long parentId;
        private final String code;
        private final String title;
        private final String path;
        private final String component;
        private final String routeKey;
        private final String icon;
        private final Integer orderNo;
        private final boolean visible;
        private final List<RuntimeMenuNodeBuilder> children = new ArrayList<>();

        private RuntimeMenuNodeBuilder(
                Long id,
                Long parentId,
                String code,
                String title,
                String path,
                String component,
                String routeKey,
                String icon,
                Integer orderNo,
                boolean visible
        ) {
            this.id = id;
            this.parentId = parentId;
            this.code = code;
            this.title = title;
            this.path = path;
            this.component = component;
            this.routeKey = routeKey;
            this.icon = icon;
            this.orderNo = orderNo;
            this.visible = visible;
        }

        private static RuntimeMenuNodeBuilder from(SysMenuEntity menu) {
            return new RuntimeMenuNodeBuilder(
                    menu.getId(),
                    menu.getParentId(),
                    menu.getResourceKey(),
                    menu.getMenuName(),
                    menu.getPath(),
                    menu.getComponent(),
                    menu.getRouteKey(),
                    menu.getIcon(),
                    menu.getOrderNo(),
                    menu.getVisible() == null || menu.getVisible() == 1
            );
        }

        private MenuNode toMenuNode() {
            return new MenuNode(id, code, title, path, component, routeKey, icon, orderNo, visible, children.stream().map(RuntimeMenuNodeBuilder::toMenuNode).toList());
        }
    }
}