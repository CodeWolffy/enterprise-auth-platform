package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.modules.security.api.SecurityAccessControlPort;
import org.springframework.stereotype.Component;

/** Exposes auth-owned platform administrator checks to the security module. */
@Component
public final class AuthSecurityAccessControlPort implements SecurityAccessControlPort {

    private final DataScopeService dataScopeService;

    public AuthSecurityAccessControlPort(DataScopeService dataScopeService) {
        this.dataScopeService = dataScopeService;
    }

    @Override
    public boolean isPlatformSuperAdmin() {
        return dataScopeService.isPlatformSuperAdmin();
    }
}
