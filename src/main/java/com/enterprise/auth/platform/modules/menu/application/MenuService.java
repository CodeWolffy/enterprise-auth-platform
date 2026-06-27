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
                .filter(menu -> readMenuType(menu) == MenuType.BUTTON)
                .map(this::readPermission)
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
        Set<Long> activeIds = tenantCapabilityResourceScopeFacade.visibleMenuIds(activeTenantId, template);
        if (activeIds.isEmpty()) {
            return List.of();
        }
        expanded.retainAll(activeIds);
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
        validateMenuShape(request.type(), parent, request.permission());
        validatePermission(request.type(), request.permission());
        validateUniqueKeys(null, request.permission(), request.path());

        SysMenuEntity entity = new SysMenuEntity();
        entity.setParentId(request.parentId());
        applyMenuPayload(entity, request.type(), request.name(), request.permission(), request.path(),
                request.component(), request.redirect(), request.icon(), request.sort(),
                request.outerStatus(), request.applicationKey());
        entity.setDelFlag("0");

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
                request.type(),
                request.name(),
                request.permission(),
                request.path(),
                request.component(),
                request.redirect(),
                request.icon(),
                request.sort(),
                request.outerStatus(),
                request.applicationKey()
        );
        validateSystemMenuMutation(entity, normalizedRequest);

        SysMenuEntity parent = nextParentId == null ? null : getMenu(nextParentId);
        if (parent != null && parent.getId().equals(menuId)) {
            throw new BusinessException("父节点不能是自身");
        }
        if (parent != null && isDescendant(menuId, parent.getId())) {
            throw new BusinessException("父节点不能是当前节点的子孙节点");
        }
        validateMenuShape(normalizedRequest.type(), parent, normalizedRequest.permission());
        validatePermission(normalizedRequest.type(), normalizedRequest.permission());
        validateUniqueKeys(menuId, normalizedRequest.permission(), normalizedRequest.path());

        entity.setParentId(normalizedRequest.parentId());
        applyMenuPayload(entity, normalizedRequest.type(), normalizedRequest.name(), normalizedRequest.permission(), normalizedRequest.path(),
                normalizedRequest.component(), normalizedRequest.redirect(), normalizedRequest.icon(), normalizedRequest.sort(),
                normalizedRequest.outerStatus(), normalizedRequest.applicationKey());

        try {
            runWithPlatformTenant(() -> {
                sysMenuMapper.updateById(entity);
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
    public List<MenuTreeNode> batchCreateActions(Long menuId, List<String> actions) {
        requirePlatformTenant();
        SysMenuEntity parent = getMenu(menuId);
        if (readMenuType(parent) != MenuType.MENU) {
            throw new BusinessException("只有菜单节点可以批量生成按钮权限");
        }
        List<String> normalizedActions = normalizeActions(actions);
        if (normalizedActions.isEmpty()) {
            throw new BusinessException("请选择要生成的按钮权限");
        }
        List<MenuTreeNode> created = new ArrayList<>();
        int sort = nextChildSort(menuId);
        for (String action : normalizedActions) {
            String permission = resolveActionPermission(parent, action);
            validatePermission(MenuType.BUTTON, permission);
            ensureActionNotExists(permission);
            SysMenuEntity entity = new SysMenuEntity();
            entity.setParentId(parent.getId());
            entity.setType(MenuType.BUTTON.value());
            entity.setName(readMenuName(parent) + ACTION_LABELS.getOrDefault(action, action));
            entity.setPermission(permission);
            entity.setPath(null);
            entity.setComponent(null);
            entity.setRedirect(null);
            entity.setIcon(null);
            entity.setSort(sort);
            sort++;
            entity.setOuterStatus(0);
            entity.setApplicationKey(parent.getApplicationKey());
            entity.setDelFlag("0");
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
        getMenu(menuId);

        long children = runWithPlatformTenant(() ->
                sysMenuMapper.selectCount(new LambdaQueryWrapper<SysMenuEntity>()
                        .eq(SysMenuEntity::getDelFlag, "0")
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
    public MenuTreeNode updateSort(Long menuId, Integer sort) {
        requirePlatformTenant();
        validateSort(sort);
        SysMenuEntity entity = getMenu(menuId);
        entity.setSort(sort == null ? 0 : sort);
        runWithPlatformTenant(() -> {
            sysMenuMapper.updateById(entity);
            return null;
        });
        evictPrincipalSnapshots();
        return toMenuNode(entity, List.of());
    }

    @Cacheable(value = CacheNames.MENU_TEMPLATE, unless = "#result.isEmpty()")
    public List<SysMenuEntity> listTemplateMenus() {
        return runWithPlatformTenant(() ->
                sysMenuMapper.selectList(new LambdaQueryWrapper<SysMenuEntity>()
                        .eq(SysMenuEntity::getDelFlag, "0")
                        .orderByAsc(SysMenuEntity::getSort)
                        .orderByAsc(SysMenuEntity::getId)));
    }

    private SysMenuEntity getMenu(Long menuId) {
        SysMenuEntity entity = runWithPlatformTenant(() ->
                sysMenuMapper.selectOne(new LambdaQueryWrapper<SysMenuEntity>()
                        .eq(SysMenuEntity::getId, menuId)
                        .eq(SysMenuEntity::getDelFlag, "0")
                        .last("limit 1")));
        if (entity == null) {
            throw new BusinessException("菜单权限节点不存在");
        }
        return entity;
    }

    private MenuTreeNode toMenuNode(SysMenuEntity entity, List<MenuTreeNode> children) {
        return new MenuTreeNode(
                entity.getId(), readMenuType(entity).value(), readMenuName(entity),
                entity.getParentId(), readPermission(entity),
                entity.getPath(), entity.getComponent(),
                entity.getRedirect(), entity.getIcon(), readSort(entity),
                readOuterStatus(entity),
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

    private int nextChildSort(Long menuId) {
        return runWithPlatformTenant(() -> sysMenuMapper.selectList(new LambdaQueryWrapper<SysMenuEntity>()
                        .eq(SysMenuEntity::getDelFlag, "0")
                        .eq(SysMenuEntity::getParentId, menuId))
                .stream()
                .map(SysMenuEntity::getSort)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .map(value -> value + 1)
                .orElse(1));
    }

    private String resolveActionPermission(SysMenuEntity parent, String action) {
        String existingPrefix = findExistingActionPermissionPrefix(parent.getId());
        if (existingPrefix != null) {
            return existingPrefix + ":" + action;
        }
        String parentPermission = blankToNull(parent.getPermission());
        if (parentPermission != null) {
            return parentPermission + ":" + action;
        }
        String path = blankToNull(parent.getPath());
        if (path == null) {
            throw new BusinessException("菜单路径不合法，无法生成按钮权限");
        }
        String normalized = path.replaceAll("^/+", "").replace('/', ':').replace('-', '_');
        if (!normalized.matches("^[a-zA-Z0-9_:]+$")) {
            throw new BusinessException("菜单路径不合法，无法生成按钮权限");
        }
        return normalized.contains(":") ? normalized + ":" + action : "upms:" + normalized + ":" + action;
    }

    private String findExistingActionPermissionPrefix(Long parentId) {
        return listTemplateMenus().stream()
                .filter(menu -> Objects.equals(parentId, menu.getParentId()))
                .filter(menu -> Objects.equals(MenuType.BUTTON.value(), readMenuType(menu).value()))
                .map(SysMenuEntity::getPermission)
                .map(MenuService::blankToNull)
                .filter(Objects::nonNull)
                .map(permission -> {
                    int lastSeparator = permission.lastIndexOf(':');
                    return lastSeparator > 0 ? permission.substring(0, lastSeparator) : null;
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private void ensureActionNotExists(String permission) {
        String normalizedPermission = blankToNull(permission);
        boolean exists = listTemplateMenus().stream().anyMatch(menu ->
                Objects.equals(MenuType.BUTTON.value(), readMenuType(menu).value())
                        && Objects.equals(normalizedPermission, blankToNull(readPermission(menu)))
        );
        if (exists) {
            throw new BusinessException("按钮权限已存在，请勿重复生成");
        }
    }

    private void validateMenuShape(MenuType childType, SysMenuEntity parent, String permission) {
        if (childType == MenuType.BUTTON) {
            if (parent == null || readMenuType(parent) != MenuType.MENU) {
                throw new BusinessException("按钮权限必须挂在菜单节点下");
            }
            if (!StringUtils.hasText(permission)) {
                throw new BusinessException("按钮权限必须配置权限标识");
            }
            return;
        }
        if (StringUtils.hasText(permission)) {
            throw new BusinessException("菜单节点不承载按钮权限");
        }
        if (parent != null && readMenuType(parent) == MenuType.BUTTON) {
            throw new BusinessException("按钮权限不能作为父节点");
        }
    }

    private void validatePermission(MenuType menuType, String permission) {
        if (!StringUtils.hasText(permission)) {
            return;
        }
        if (menuType != MenuType.BUTTON) {
            throw new BusinessException("只有按钮节点可以配置权限标识");
        }
        if (!permission.matches("^[a-zA-Z0-9]+(:[a-zA-Z0-9_-]+)+$")) {
            throw new BusinessException("权限标识格式不合法");
        }
    }

    private void validateUniqueKeys(Long currentMenuId, String permission, String path) {
        String normalizedPermission = blankToNull(permission);
        String normalizedPath = blankToNull(path);
        for (SysMenuEntity menu : listTemplateMenus()) {
            if (currentMenuId != null && Objects.equals(menu.getId(), currentMenuId)) {
                continue;
            }
            if (normalizedPermission != null && normalizedPermission.equals(blankToNull(menu.getPermission()))) {
                throw new BusinessException("权限标识已存在");
            }
            if (normalizedPath != null && normalizedPath.equals(blankToNull(menu.getPath()))) {
                throw new BusinessException("访问路径已存在");
            }
        }
    }

    private void validateSystemMenuMutation(SysMenuEntity entity, CreateMenuRequest request) {
        if (!Objects.equals(readMenuType(entity), request.type())) {
            throw new BusinessException("系统节点不允许修改类型");
        }
        if (!Objects.equals(entity.getParentId(), request.parentId())) {
            throw new BusinessException("系统节点不允许修改父节点");
        }
        if (!Objects.equals(blankToNull(readPermission(entity)), blankToNull(request.permission()))) {
            throw new BusinessException("系统节点不允许修改权限标识");
        }
        if (!Objects.equals(blankToNull(entity.getPath()), blankToNull(request.path()))) {
            throw new BusinessException("系统节点不允许修改访问路径");
        }
        if (!Objects.equals(blankToNull(entity.getComponent()), blankToNull(request.component()))) {
            throw new BusinessException("系统节点不允许修改组件名");
        }
    }

    private boolean isDescendant(Long ancestorId, Long candidateId) {
        Map<Long, SysMenuEntity> menuById = toMenuMap(listTemplateMenus());
        Long parentId = candidateId;
        while (parentId != null) {
            if (Objects.equals(parentId, ancestorId)) {
                return true;
            }
            SysMenuEntity current = menuById.get(parentId);
            parentId = current == null ? null : current.getParentId();
        }
        return false;
    }

    private void validateSort(Integer sort) {
        if (sort == null) {
            return;
        }
        if (sort < 0 || sort > 9999) {
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
        return readMenuType(menu) == MenuType.MENU;
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

    private boolean hierarchyVisible(SysMenuEntity menu, Map<Long, SysMenuEntity> menuById) {
        return hierarchyEnabled(menu, menuById);
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

    private Map<Long, SysMenuEntity> toMenuMap(List<SysMenuEntity> menus) {
        return menus.stream().collect(Collectors.toMap(
                SysMenuEntity::getId,
                value -> value,
                (left, right) -> right,
                LinkedHashMap::new
        ));
    }

    private void applyMenuPayload(
            SysMenuEntity entity,
            MenuType menuType,
            String name,
            String permission,
            String path,
            String component,
            String redirect,
            String icon,
            Integer sort,
            Boolean outerStatus,
            String applicationKey
    ) {
        String normalizedName = name == null ? null : name.trim();
        String normalizedPermission = menuType == MenuType.BUTTON ? blankToNull(permission) : null;
        String normalizedPath = menuType == MenuType.MENU ? blankToNull(path) : null;
        String normalizedComponent = menuType == MenuType.MENU ? blankToNull(component) : null;
        String normalizedRedirect = menuType == MenuType.MENU ? blankToNull(redirect) : null;
        String normalizedIcon = menuType == MenuType.MENU ? blankToNull(icon) : null;
        Integer normalizedSort = sort == null ? 0 : sort;

        entity.setType(menuType.value());
        entity.setName(normalizedName);
        entity.setPermission(normalizedPermission);
        entity.setPath(normalizedPath);
        entity.setComponent(normalizedComponent);
        entity.setRedirect(normalizedRedirect);
        entity.setIcon(normalizedIcon);
        entity.setSort(normalizedSort);
        entity.setOuterStatus(Boolean.TRUE.equals(outerStatus) ? 1 : 0);
        entity.setApplicationKey(applicationKey);
        entity.setDelFlag("0");
    }

    private MenuType readMenuType(SysMenuEntity entity) {
        return parseType(entity.getType());
    }

    private String readMenuName(SysMenuEntity entity) {
        return entity.getName();
    }

    private String readPermission(SysMenuEntity entity) {
        return entity.getPermission();
    }

    private Integer readSort(SysMenuEntity entity) {
        return entity.getSort();
    }

    private boolean isActive(SysMenuEntity entity) {
        return entity.getDelFlag() == null || "0".equals(entity.getDelFlag());
    }

    private boolean readOuterStatus(SysMenuEntity entity) {
        return entity.getOuterStatus() != null && entity.getOuterStatus() == 1;
    }

    private MenuType parseType(String value) {
        return MenuType.fromValue(value);
    }

    private <T> T runWithPlatformTenant(Supplier<T> supplier) {
        return runWithTenant(platformTenantId(), supplier);
    }

    private <T> T runWithTenant(String tenantId, Supplier<T> supplier) {
        return TenantContext.runWithTenant(StringUtils.hasText(tenantId) ? tenantId : platformTenantId(), supplier);
    }

    private void sortRuntimeChildrenRecursively(RuntimeMenuNodeBuilder node, Comparator<RuntimeMenuNodeBuilder> comparator) {
        node.children.sort(comparator);
        for (RuntimeMenuNodeBuilder child : node.children) {
            sortRuntimeChildrenRecursively(child, comparator);
        }
    }

    private RuntimeMenuNodeBuilder toRuntimeMenuNode(SysMenuEntity menu) {
        return new RuntimeMenuNodeBuilder(
                menu.getId(),
                menu.getParentId(),
                menu.getType(),
                readMenuName(menu),
                menu.getPath(),
                menu.getComponent(),
                readPermission(menu),
                menu.getIcon(),
                readSort(menu),
                readOuterStatus(menu)
        );
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private final class RuntimeMenuNodeBuilder {
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
            return new MenuNode(id, code, title, title, path, component, permission, icon, sort, outerStatus, children.stream().map(RuntimeMenuNodeBuilder::toMenuNode).toList());
        }
    }
}