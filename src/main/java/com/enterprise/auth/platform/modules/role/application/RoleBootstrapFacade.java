package com.enterprise.auth.platform.modules.role.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleEntity;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleMenuEntity;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMapper;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMenuMapper;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class RoleBootstrapFacade {

    public static final String TENANT_ADMIN_ROLE_CODE = "TENANT_ADMIN";

    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;

    public RoleBootstrapFacade(SysRoleMapper sysRoleMapper, SysRoleMenuMapper sysRoleMenuMapper) {
        this.sysRoleMapper = sysRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
    }

    public Long ensureTenantAdminRole(String tenantId) {
        SysRoleEntity existing = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRoleEntity>()
                .eq(SysRoleEntity::getTenantId, tenantId)
                .eq(SysRoleEntity::getRoleCode, TENANT_ADMIN_ROLE_CODE)
                .eq(SysRoleEntity::getDeleted, 0)
                .last("limit 1"));
        if (existing != null) {
            return existing.getId();
        }
        SysRoleEntity entity = new SysRoleEntity();
        entity.setTenantId(tenantId);
        entity.setRoleCode(TENANT_ADMIN_ROLE_CODE);
        entity.setRoleName("租户管理员");
        entity.setRoleDesc("租户初始化自动创建的管理员角色");
        entity.setDataScopeType(DataScopeType.ALL.name());
        entity.setDataScopeValueJson(null);
        sysRoleMapper.insert(entity);
        return entity.getId();
    }

    public void grantMenus(String tenantId, Long roleId, Set<Long> menuIds) {
        for (Long menuId : menuIds) {
            Long existing = sysRoleMenuMapper.selectCount(new LambdaQueryWrapper<SysRoleMenuEntity>()
                    .eq(SysRoleMenuEntity::getTenantId, tenantId)
                    .eq(SysRoleMenuEntity::getRoleId, roleId)
                    .eq(SysRoleMenuEntity::getMenuId, menuId));
            if (Optional.ofNullable(existing).orElse(0L) > 0) {
                continue;
            }
            SysRoleMenuEntity relation = new SysRoleMenuEntity();
            relation.setTenantId(tenantId);
            relation.setRoleId(roleId);
            relation.setMenuId(menuId);
            sysRoleMenuMapper.insert(relation);
        }
    }
}
