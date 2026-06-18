package com.enterprise.auth.platform.modules.tenant.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.menu.infrastructure.entity.SysMenuEntity;
import com.enterprise.auth.platform.modules.menu.infrastructure.mapper.SysMenuMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantMenuEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantMenuMapper;
import java.util.Collections;
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
    private final SysMenuMapper sysMenuMapper;
    private final TenantCapabilityResourceScopeFacade tenantCapabilityResourceScopeFacade;

    public TenantMenuService(
            SysTenantMenuMapper sysTenantMenuMapper,
            SysMenuMapper sysMenuMapper,
            TenantCapabilityResourceScopeFacade tenantCapabilityResourceScopeFacade
    ) {
        this.sysTenantMenuMapper = sysTenantMenuMapper;
        this.sysMenuMapper = sysMenuMapper;
        this.tenantCapabilityResourceScopeFacade = tenantCapabilityResourceScopeFacade;
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
     * 对齐 haorong-mall saveTenantMenu 的语义：传入的 menuIds 即为该租户的全部菜单。
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
            return null;
        });
    }

    private Set<Long> validateTenantMenuIds(String tenantId, Set<Long> menuIds) {
        Set<Long> normalizedMenuIds = normalizeMenuIds(menuIds);
        List<SysMenuEntity> templateMenus = templateMenus();
        Map<Long, SysMenuEntity> menuById = templateMenus.stream()
                .filter(menu -> menu.getId() != null)
                .collect(Collectors.toMap(
                        SysMenuEntity::getId,
                        menu -> menu,
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
        for (Long menuId : normalizedMenuIds) {
            if (!menuById.containsKey(menuId)) {
                throw new BusinessException("VALIDATION_ERROR", "存在无效的菜单 ID");
            }
        }

        Set<Long> visibleMenuIds = tenantCapabilityResourceScopeFacade.visibleMenuIds(tenantId, templateMenus);
        for (Long menuId : normalizedMenuIds) {
            if (!visibleMenuIds.contains(menuId)) {
                throw new BusinessException("VALIDATION_ERROR", "存在超出租户能力范围的菜单 ID");
            }
        }
        return normalizedMenuIds;
    }

    private List<SysMenuEntity> templateMenus() {
        return TenantContext.runWithTenant("platform", () -> sysMenuMapper.selectList(new LambdaQueryWrapper<SysMenuEntity>()
                .eq(SysMenuEntity::getDelFlag, "0")
                .orderByAsc(SysMenuEntity::getSort)
                .orderByAsc(SysMenuEntity::getId)));
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