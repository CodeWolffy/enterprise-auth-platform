package com.enterprise.auth.platform.modules.tenant.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantMenuEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantMenuMapper;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TenantMenuService {

    private final SysTenantMenuMapper sysTenantMenuMapper;

    public TenantMenuService(SysTenantMenuMapper sysTenantMenuMapper) {
        this.sysTenantMenuMapper = sysTenantMenuMapper;
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
        runWithTenant(tenantId, () -> {
            sysTenantMenuMapper.delete(new LambdaQueryWrapper<SysTenantMenuEntity>()
                    .eq(SysTenantMenuEntity::getTenantId, tenantId));
            if (menuIds != null && !menuIds.isEmpty()) {
                List<SysTenantMenuEntity> entities = menuIds.stream().map(menuId -> {
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