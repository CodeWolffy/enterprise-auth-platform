package com.enterprise.auth.platform.modules.tenant.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.auth.application.AuthPermissionSnapshotInvalidationService;
import com.enterprise.auth.platform.modules.menu.application.MenuTemplateQueryFacade;
import com.enterprise.auth.platform.modules.menu.application.MenuTemplateQueryFacade.MenuTemplateItem;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantPackageEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantMenuEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantPackageMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantMenuMapper;
import java.util.Collections;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TenantMenuService {

    private final SysTenantMenuMapper sysTenantMenuMapper;
    private final MenuTemplateQueryFacade menuTemplateQueryFacade;
    private final SysTenantPackageMapper sysTenantPackageMapper;
    private final AuthPermissionSnapshotInvalidationService permissionSnapshotInvalidationService;

    public TenantMenuService(
            SysTenantMenuMapper sysTenantMenuMapper,
            MenuTemplateQueryFacade menuTemplateQueryFacade,
            SysTenantPackageMapper sysTenantPackageMapper,
            AuthPermissionSnapshotInvalidationService permissionSnapshotInvalidationService
    ) {
        this.sysTenantMenuMapper = sysTenantMenuMapper;
        this.menuTemplateQueryFacade = menuTemplateQueryFacade;
        this.sysTenantPackageMapper = sysTenantPackageMapper;
        this.permissionSnapshotInvalidationService = permissionSnapshotInvalidationService;
    }

    /**
     * 查询指定租户分配的菜单 ID 集合。
     * 空结果表示该租户无任何菜单分配（与 null 区分）。
     */
    public Set<Long> findTenantMenuIds(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return Collections.emptySet();
        }
        return runWithTenant(tenantId, () ->
                sysTenantMenuMapper.selectList(new LambdaQueryWrapper<SysTenantMenuEntity>()
                                .eq(SysTenantMenuEntity::getTenantId, tenantId))
                        .stream()
                        .map(SysTenantMenuEntity::getMenuId)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    /**
     * 为指定租户分配菜单（全量替换，先删后批量插入）。
     * 保存租户菜单授权：传入的 menuIds 即为该租户的全部菜单。
     */
    @Transactional
    public void saveTenantMenu(String tenantId, Set<Long> menuIds) {
        if (!StringUtils.hasText(tenantId)) {
            return;
        }
        Set<Long> normalizedMenuIds = validateTenantMenuIds(tenantId, menuIds);
        runWithTenant(tenantId, () -> {
            sysTenantMenuMapper.delete(new LambdaQueryWrapper<SysTenantMenuEntity>()
                    .eq(SysTenantMenuEntity::getTenantId, tenantId));
            if (!normalizedMenuIds.isEmpty()) {
                List<SysTenantMenuEntity> entities = normalizedMenuIds.stream().map(menuId -> {
                    SysTenantMenuEntity e = new SysTenantMenuEntity();
                    e.setTenantId(tenantId);
                    e.setMenuId(menuId);
                    return e;
                }).toList();
            for (SysTenantMenuEntity entity : entities) {
                    sysTenantMenuMapper.insert(entity);
                }
            }
            sysTenantMenuMapper.deleteRoleMenusOutsideTenantMenus(tenantId);
            return null;
        });
        permissionSnapshotInvalidationService.invalidateTenant(tenantId);
    }

    @Transactional
    public Set<Long> saveTenantMenuByPackage(String tenantId, String packageCode) {
        Set<Long> menuIds = menuIdsForPackageCode(packageCode);
        saveTenantMenu(tenantId, menuIds);
        return menuIds;
    }

    public Set<Long> menuIdsForPackageCode(String packageCode) {
        if (!StringUtils.hasText(packageCode)) {
            return Set.of();
        }
        SysTenantPackageEntity pkg = TenantContext.runWithTenant("platform", () ->
                sysTenantPackageMapper.selectOne(new LambdaQueryWrapper<SysTenantPackageEntity>()
                        .eq(SysTenantPackageEntity::getTenantId, "platform")
                        .eq(SysTenantPackageEntity::getPackageCode, packageCode.trim())
                        .eq(SysTenantPackageEntity::getDeleted, 0)
                        .last("limit 1")));
        if (pkg == null) {
            throw new BusinessException("租户套餐不存在: " + packageCode);
        }
        Set<String> appKeys = appKeys(pkg.getAppKey());
        if (appKeys.isEmpty()) {
            return Set.of();
        }
        List<MenuTemplateItem> menus = templateMenus();
        Set<Long> selected = menus.stream()
                .filter(menu -> menu.id() != null)
                .filter(menu -> appKeys.contains(normalizeKey(menu.applicationKey())))
                .map(MenuTemplateItem::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return includeAncestors(selected, menus);
    }

    private Set<Long> validateTenantMenuIds(String tenantId, Set<Long> menuIds) {
        Set<Long> normalizedMenuIds = normalizeMenuIds(menuIds);
        List<MenuTemplateItem> templateMenus = templateMenus();
        Map<Long, MenuTemplateItem> menuById = templateMenus.stream()
                .filter(menu -> menu.id() != null)
                .collect(Collectors.toMap(
                        MenuTemplateItem::id,
                        menu -> menu,
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
        for (Long menuId : normalizedMenuIds) {
            if (!menuById.containsKey(menuId)) {
                throw new BusinessException("VALIDATION_ERROR", "存在无效的菜单 ID");
            }
        }

        return normalizedMenuIds;
    }

    private List<MenuTemplateItem> templateMenus() {
        return menuTemplateQueryFacade.listActiveTemplateMenus();
    }

    private Set<Long> normalizeMenuIds(Set<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return Set.of();
        }
        if (menuIds.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException("VALIDATION_ERROR", "菜单 ID 不能为空");
        }
        return menuIds.stream().collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> includeAncestors(Set<Long> selected, List<MenuTemplateItem> menus) {
        if (selected.isEmpty()) {
            return Set.of();
        }
        Map<Long, MenuTemplateItem> menuById = menus.stream()
                .filter(menu -> menu.id() != null)
                .collect(Collectors.toMap(
                        MenuTemplateItem::id,
                        menu -> menu,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        LinkedHashSet<Long> expanded = new LinkedHashSet<>(selected);
        for (Long menuId : selected) {
            MenuTemplateItem current = menuById.get(menuId);
            while (current != null && current.parentId() != null) {
                Long parentId = current.parentId();
                if (!expanded.add(parentId)) {
                    break;
                }
                current = menuById.get(parentId);
            }
        }
        return expanded;
    }

    private Set<String> appKeys(String appKeyText) {
        if (!StringUtils.hasText(appKeyText)) {
            return Set.of();
        }
        return Arrays.stream(appKeyText.split("[,;\\s]+"))
                .map(this::normalizeKey)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeKey(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private <T> T runWithTenant(String tenantId, Supplier<T> supplier) {
        String previous = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenantId);
            return supplier.get();
        } finally {
            if (StringUtils.hasText(previous)) {
                TenantContext.setTenantId(previous);
            } else {
                TenantContext.clear();
            }
        }
    }
}
