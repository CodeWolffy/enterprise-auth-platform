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

    public List<RoleItem> listRoleItems(String tenantId) {
        return listRoles(tenantId).stream()
                .map(r -> new RoleItem(r.getId(), r.getRoleCode(), r.getRoleName(), r.getRoleDesc(),
                        r.getDataScopeType(), r.getDataScopeValueJson()))
                .toList();
    }

    public RolePayloadCodec payloadCodec() {
        return rolePayloadCodec;
    }

    public record RoleItem(Long id, String roleCode, String roleName, String roleDesc,
                           String dataScopeType, String dataScopeValueJson) {}
}