package com.enterprise.auth.platform.modules.role.application;

import com.enterprise.auth.platform.modules.menu.application.MenuDeletedEvent;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMenuMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RoleMenuGrantCleanupListener {

    private final SysRoleMenuMapper sysRoleMenuMapper;

    public RoleMenuGrantCleanupListener(SysRoleMenuMapper sysRoleMenuMapper) {
        this.sysRoleMenuMapper = sysRoleMenuMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void cleanupMenuGrant(MenuDeletedEvent event) {
        sysRoleMenuMapper.deleteByMenuIdAcrossTenants(event.menuId());
    }
}