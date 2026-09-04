package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.modules.menu.api.MenuAccessControlPort;
import org.springframework.stereotype.Component;

/** Exposes auth-owned platform administrator checks to the menu module. */
@Component
public final class AuthMenuAccessControlPort implements MenuAccessControlPort {

    private final DataScopeService dataScopeService;

    public AuthMenuAccessControlPort(DataScopeService dataScopeService) {
        this.dataScopeService = dataScopeService;
    }

    @Override
    public boolean isPlatformSuperAdmin() {
        return dataScopeService.isPlatformSuperAdmin();
    }
}
