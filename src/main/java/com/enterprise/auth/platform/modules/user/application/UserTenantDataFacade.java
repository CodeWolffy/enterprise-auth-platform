package com.enterprise.auth.platform.modules.user.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserTenantDataFacade {

    private final SysUserMapper sysUserMapper;

    public UserTenantDataFacade(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    public boolean hasActiveUsers(String tenantId) {
        return withTenant(tenantId, () -> sysUserMapper.selectCount(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getTenantId, tenantId)
                .eq(SysUserEntity::getDeleted, 0)) > 0);
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