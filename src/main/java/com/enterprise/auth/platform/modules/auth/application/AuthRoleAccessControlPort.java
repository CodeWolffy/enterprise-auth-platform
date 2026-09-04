package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.modules.role.api.RoleAccessControlPort;
import org.springframework.stereotype.Component;

/** Exposes auth-owned platform administrator checks to the role module. */
@Component
public final class AuthRoleAccessControlPort implements RoleAccessControlPort {

    private final DataScopeService dataScopeService;

    public AuthRoleAccessControlPort(DataScopeService dataScopeService) {
        this.dataScopeService = dataScopeService;
    }

    @Override
    public boolean isPlatformSuperAdmin() {
        return dataScopeService.isPlatformSuperAdmin();
    }
}
