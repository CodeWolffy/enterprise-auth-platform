package com.enterprise.auth.platform.modules.dept.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.dept.infrastructure.entity.SysDeptEntity;
import com.enterprise.auth.platform.modules.dept.infrastructure.mapper.SysDeptMapper;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DeptTenantDataFacade {

    private final SysDeptMapper sysDeptMapper;

    public DeptTenantDataFacade(SysDeptMapper sysDeptMapper) {
        this.sysDeptMapper = sysDeptMapper;
    }

    public boolean hasActiveDepartments(String tenantId) {
        return withTenant(tenantId, () -> sysDeptMapper.selectCount(new LambdaQueryWrapper<SysDeptEntity>()
                .eq(SysDeptEntity::getTenantId, tenantId)
                .eq(SysDeptEntity::getDeleted, 0)) > 0);
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