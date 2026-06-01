package com.enterprise.auth.platform.modules.role.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleEntity;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMapper;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RoleTenantDataFacade {

    private final SysRoleMapper sysRoleMapper;

    public RoleTenantDataFacade(SysRoleMapper sysRoleMapper) {
        this.sysRoleMapper = sysRoleMapper;
    }

    public boolean hasActiveRoles(String tenantId) {
        return withTenant(tenantId, () -> sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRoleEntity>()
                .eq(SysRoleEntity::getTenantId, tenantId)
                .eq(SysRoleEntity::getDeleted, 0)) > 0);
    }

    private <T> T withTenant(String tenantId, Supplier<T> supplier) {
        String previousTenantId = TenantContext.getTenantId();
        TenantContext.setTenantId(tenantId);
        try {
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