package com.enterprise.auth.platform.modules.role.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleEntity;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleMenuEntity;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMapper;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMenuMapper;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RoleMenuMutationFacade {

    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;

    public RoleMenuMutationFacade(SysRoleMapper sysRoleMapper, SysRoleMenuMapper sysRoleMenuMapper) {
        this.sysRoleMapper = sysRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
    }

    public void assignMenuIdsToRoleCode(String tenantId, String roleCode, Collection<Long> menuIds) {
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(roleCode) || menuIds == null || menuIds.isEmpty()) {
            return;
        }
        withTenant(tenantId, () -> {
            SysRoleEntity role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRoleEntity>()
                    .eq(SysRoleEntity::getTenantId, tenantId)
                    .eq(SysRoleEntity::getRoleCode, roleCode)
                    .eq(SysRoleEntity::getDeleted, 0)
                    .last("limit 1"));
            if (role == null) {
                return null;
            }
            Set<Long> normalizedMenuIds = menuIds.stream()
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            for (Long menuId : normalizedMenuIds) {
                Long existing = sysRoleMenuMapper.selectCount(new LambdaQueryWrapper<SysRoleMenuEntity>()
                        .eq(SysRoleMenuEntity::getTenantId, tenantId)
                        .eq(SysRoleMenuEntity::getRoleId, role.getId())
                        .eq(SysRoleMenuEntity::getMenuId, menuId));
                if (existing != null && existing > 0) {
                    continue;
                }
                SysRoleMenuEntity relation = new SysRoleMenuEntity();
                relation.setTenantId(tenantId);
                relation.setRoleId(role.getId());
                relation.setMenuId(menuId);
                sysRoleMenuMapper.insert(relation);
            }
            return null;
        });
    }

    private <T> T withTenant(String tenantId, Supplier<T> supplier) {
        String previousTenantId = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenantId);
            return supplier.get();
        } finally {
            if (StringUtils.hasText(previousTenantId)) {
                TenantContext.setTenantId(previousTenantId);
            } else {
                TenantContext.clear();
            }
        }
    }
}