package com.enterprise.auth.platform.modules.role.application;

import com.enterprise.auth.platform.modules.menu.application.RoleMenuReferencePort;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMenuMapper;
import org.springframework.stereotype.Service;

@Service
public class RoleMenuReferenceFacade implements RoleMenuReferencePort {

    private final SysRoleMenuMapper sysRoleMenuMapper;

    public RoleMenuReferenceFacade(SysRoleMenuMapper sysRoleMenuMapper) {
        this.sysRoleMenuMapper = sysRoleMenuMapper;
    }

    @Override
    public long countMenuReferencesAcrossTenants(Long menuId) {
        if (menuId == null) {
            return 0L;
        }
        return sysRoleMenuMapper.countByMenuIdAcrossTenants(menuId);
    }
}