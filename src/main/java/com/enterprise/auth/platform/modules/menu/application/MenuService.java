package com.enterprise.auth.platform.modules.menu.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.cache.CacheNames;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.context.TenantContextSupport;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.menu.api.MenuAuthorizationInvalidationPort;
import com.enterprise.auth.platform.modules.menu.api.MenuDeletedEvent;
import com.enterprise.auth.platform.modules.menu.api.MenuGrantQueryPort;
import com.enterprise.auth.platform.modules.menu.api.MenuNode;
import com.enterprise.auth.platform.modules.menu.api.RoleMenuReferencePort;
import com.enterprise.auth.platform.modules.menu.api.MenuTenantGrantPort;
import com.enterprise.auth.platform.modules.menu.domain.MenuTreeNode;
import com.enterprise.auth.platform.modules.menu.domain.MenuType;
import com.enterprise.auth.platform.modules.menu.infrastructure.entity.SysMenuEntity;
import com.enterprise.auth.platform.modules.menu.infrastructure.mapper.SysMenuMapper;
import com.enterprise.auth.platform.modules.menu.interfaces.CreateMenuRequest;
import com.enterprise.auth.platform.common.context.TenantProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MenuService implements MenuGrantQueryPort {

    private static final String PLATFORM_TENANT = "platform";
    private static final Map<String, String> ACTION_LABELS = Map.of(
            "add", "新增",
            "edit", "修改",
            "del", "删除",
            "page", "列表",
            "get", "查询"
    );

    private final SysMenuMapper sysMenuMapper;
    private final RoleMenuReferencePort roleMenuReferencePort;
    private final ApplicationEventPublisher eventPublisher;
    private final MenuAuthorizationInvalidationPort authorizationInvalidation;
    private final TenantProperties tenantProperties;
    private final MenuTemplateQueryService menuTemplateQueryService;
    private final MenuTreeResolver menuTreeResolver;

    public MenuService(
            SysMenuMapper sysMenuMapper,
            RoleMenuReferencePort roleMenuReferencePort,
            ApplicationEventPublisher eventPublisher,
            MenuTenantGrantPort tenantGrants,
            MenuAuthorizationInvalidationPort authorizationInvalidation,
            TenantProperties tenantProperties,
            MenuTemplateQueryService menuTemplateQueryService
    ) {
        this.sysMenuMapper = sysMenuMapper;
        this.roleMenuReferencePort = roleMenuReferencePort;
        this.eventPublisher = eventPublisher;
        this.authorizationInvalidation = authorizationInvalidation;
        this.tenantProperties = tenantProperties;
        this.menuTemplateQueryService = menuTemplateQueryService;
        this.menuTreeResolver = new MenuTreeResolver(tenantGrants, tenantProperties);
    }

    public List<MenuTreeNode> templateTree() {
        return menuTreeResolver.templateTree(listTemplateMenus());
    }

    public Set<String> resolveGrantKeys(String activeTenantId, Set<Long> grantedMenuIds, boolean superAdmin) {
        return menuTreeResolver.resolveGrantKeys(listTemplateMenus(), activeTenantId, grantedMenuIds, superAdmin);
    }

    public Set<String> resolveGrantKeys(Set<Long> grantedMenuIds, boolean superAdmin) {
        return resolveGrantKeys(platformTenantId(), grantedMenuIds, superAdmin);
    }

    public List<MenuNode> resolveMenuTree(String activeTenantId, Set<Long> grantedMenuIds, boolean superAdmin) {
        return menuTreeResolver.resolveMenuTree(listTemplateMenus(), activeTenantId, grantedMenuIds, superAdmin);
    }

    public List<MenuNode> resolveMenuTree(Set<Long> grantedMenuIds, boolean superAdmin) {
        return resolveMenuTree(platformTenantId(), grantedMenuIds, superAdmin);
    }

    public List<MenuTreeNode> grantableTree(String activeTenantId) {
        return menuTreeResolver.grantableTree(listTemplateMenus(), activeTenantId);
    }

    public Set<Long> filterGrantableMenuIds(String activeTenantId, Set<Long> menuIds) {
        return menuTreeResolver.filterGrantableMenuIds(listTemplateMenus(), activeTenantId, menuIds);
    }

    public Set<Long> expandMenuIdsWithAncestors(String activeTenantId, Set<Long> requestedMenuIds) {
        return menuTreeResolver.expandMenuIdsWithAncestors(listTemplateMenus(), activeTenantId, requestedMenuIds);
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
        SysMenuEntity parent = (request.parentId() == null || request.parentId() == 0L) ? null : getMenu(request.parentId());
        validateMenuShape(request.type(), parent, request.permission());
        validatePermission(request.type(), request.permission());
        validateUniqueKeys(null, request.permission(), request.path());

        SysMenuEntity entity = new SysMenuEntity();
        entity.setParentId(request.parentId());
        applyMenuPayload(entity, request.type(), request.name(), request.permission(), request.path(),
                request.component(), request.redirect(), request.icon(), request.sort(),
                request.outerStatus(), request.applicationKey());
        entity.setDeleted(0);

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
        Long rawParentId = parentIdPresent ? request.parentId() : entity.getParentId();
        Long nextParentId = (rawParentId == null || rawParentId == 0L) ? null : rawParentId;
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
        List<SysMenuEntity> template = listTemplateMenus();
        String permissionPrefix = findExistingActionPermissionPrefix(parent.getId(), template);
        Set<String> existingPermissions = template.stream()
                .filter(menu -> readMenuType(menu) == MenuType.BUTTON)
                .map(this::readPermission)
                .map(MenuService::blankToNull)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<MenuTreeNode> created = new ArrayList<>();
        int sort = nextChildSort(menuId, template);
        for (String action : normalizedActions) {
            String permission = resolveActionPermission(parent, action, permissionPrefix);
            validatePermission(MenuType.BUTTON, permission);
            if (!existingPermissions.add(permission)) {
                throw new BusinessException("按钮权限已存在，请勿重复生成");
            }
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
            entity.setDeleted(0);
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
                        .eq(SysMenuEntity::getDeleted, 0)
                        .eq(SysMenuEntity::getParentId, menuId)));

        if (children > 0) {
            throw new BusinessException("请先删除子节点");
        }
        long roleBindings = roleMenuReferencePort.countMenuReferencesAcrossTenants(menuId);
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

    public List<SysMenuEntity> listTemplateMenus() {
        return menuTemplateQueryService.listTemplateMenus();
    }

    private SysMenuEntity getMenu(Long menuId) {
        SysMenuEntity entity = runWithPlatformTenant(() ->
                sysMenuMapper.selectOne(new LambdaQueryWrapper<SysMenuEntity>()
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

    private int nextChildSort(Long menuId, List<SysMenuEntity> template) {
        return template.stream()
                .filter(menu -> Objects.equals(menuId, menu.getParentId()))
                .map(SysMenuEntity::getSort)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .map(value -> value + 1)
                .orElse(1);
    }

    private String resolveActionPermission(SysMenuEntity parent, String action, String existingPrefix) {
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

    private String findExistingActionPermissionPrefix(Long parentId, List<SysMenuEntity> template) {
        return template.stream()
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
        if (!platformTenantId().equals(TenantContextSupport.currentTenantIdOr(platformTenantId()))) {
            throw new BusinessException("仅平台租户允许维护菜单模板");
        }
    }

    private void evictPrincipalSnapshots() {
        authorizationInvalidation.invalidateAll();
    }

    private String platformTenantId() {
        return StringUtils.hasText(tenantProperties.platformTenantId()) ? tenantProperties.platformTenantId() : PLATFORM_TENANT;
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
        entity.setDeleted(0);
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

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

}
