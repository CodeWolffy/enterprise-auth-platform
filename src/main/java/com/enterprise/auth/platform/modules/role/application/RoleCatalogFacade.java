package com.enterprise.auth.platform.modules.role.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleEntity;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RoleCatalogFacade {

    private final SysRoleMapper sysRoleMapper;
    private final RolePayloadCodec rolePayloadCodec;

    public RoleCatalogFacade(SysRoleMapper sysRoleMapper, RolePayloadCodec rolePayloadCodec) {
        this.sysRoleMapper = sysRoleMapper;
        this.rolePayloadCodec = rolePayloadCodec;
    }

    public List<SysRoleEntity> listRoles(String tenantId) {
        return sysRoleMapper.selectList(new LambdaQueryWrapper<SysRoleEntity>()
                .eq(SysRoleEntity::getTenantId, tenantId)
                .eq(SysRoleEntity::getDeleted, 0)
                .orderByAsc(SysRoleEntity::getId));
    }

    public RolePayloadCodec payloadCodec() {
        return rolePayloadCodec;
    }
}