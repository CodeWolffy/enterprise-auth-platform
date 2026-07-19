package com.enterprise.auth.platform.modules.role.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleEntity;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleMenuEntity;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMapper;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMenuMapper;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
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
            Set<Long> existingMenuIds = sysRoleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenuEntity>()
                            .select(SysRoleMenuEntity::getMenuId)
                            .eq(SysRoleMenuEntity::getTenantId, tenantId)
                            .eq(SysRoleMenuEntity::getRoleId, role.getId())
                            .in(SysRoleMenuEntity::getMenuId, normalizedMenuIds))
                    .stream()
                    .map(SysRoleMenuEntity::getMenuId)
                    .collect(java.util.stream.Collectors.toSet());
            List<SysRoleMenuEntity> relations = normalizedMenuIds.stream()
                    .filter(menuId -> !existingMenuIds.contains(menuId))
                    .map(menuId -> roleMenu(tenantId, role.getId(), menuId))
                    .toList();
            if (!relations.isEmpty()) {
                sysRoleMenuMapper.insert(relations);
            }
            return null;
        });
    }

    private SysRoleMenuEntity roleMenu(String tenantId, Long roleId, Long menuId) {
        SysRoleMenuEntity relation = new SysRoleMenuEntity();
        relation.setTenantId(tenantId);
        relation.setRoleId(roleId);
        relation.setMenuId(menuId);
        return relation;
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
