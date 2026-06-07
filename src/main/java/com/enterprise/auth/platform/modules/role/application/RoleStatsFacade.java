package com.enterprise.auth.platform.modules.role.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleEntity;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMapper;
import org.springframework.stereotype.Service;

@Service
public class RoleStatsFacade {

    private final SysRoleMapper sysRoleMapper;

    public RoleStatsFacade(SysRoleMapper sysRoleMapper) {
        this.sysRoleMapper = sysRoleMapper;
    }

    public long countRoles(String tenantId, boolean platformScope) {
        LambdaQueryWrapper<SysRoleEntity> wrapper = new LambdaQueryWrapper<SysRoleEntity>()
                .eq(SysRoleEntity::getDeleted, 0);
        if (!platformScope) {
            wrapper.eq(SysRoleEntity::getTenantId, tenantId);
        }
        return sysRoleMapper.selectCount(wrapper);
    }
}