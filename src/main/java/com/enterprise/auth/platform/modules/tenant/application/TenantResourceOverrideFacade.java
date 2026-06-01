package com.enterprise.auth.platform.modules.tenant.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantResourceOverrideEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantResourceOverrideMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantResourceOverrideFacade {

    private final SysTenantResourceOverrideMapper sysTenantResourceOverrideMapper;

    public TenantResourceOverrideFacade(SysTenantResourceOverrideMapper sysTenantResourceOverrideMapper) {
        this.sysTenantResourceOverrideMapper = sysTenantResourceOverrideMapper;
    }

    public List<SysTenantResourceOverrideEntity> listOverrides(String tenantId) {
        return sysTenantResourceOverrideMapper.selectList(new LambdaQueryWrapper<SysTenantResourceOverrideEntity>()
                .eq(SysTenantResourceOverrideEntity::getTenantId, tenantId)
                .orderByAsc(SysTenantResourceOverrideEntity::getId));
    }

    public SysTenantResourceOverrideEntity findByResourceId(String tenantId, Long resourceId) {
        return sysTenantResourceOverrideMapper.selectOne(new LambdaQueryWrapper<SysTenantResourceOverrideEntity>()
                .eq(SysTenantResourceOverrideEntity::getTenantId, tenantId)
                .eq(SysTenantResourceOverrideEntity::getResourceId, resourceId)
                .last("limit 1"));
    }

    @Transactional
    public void insert(SysTenantResourceOverrideEntity entity) {
        sysTenantResourceOverrideMapper.insert(entity);
    }

    @Transactional
    public void updateById(SysTenantResourceOverrideEntity entity) {
        sysTenantResourceOverrideMapper.updateById(entity);
    }

    @Transactional
    public void deleteById(Long id) {
        sysTenantResourceOverrideMapper.deleteById(id);
    }
}