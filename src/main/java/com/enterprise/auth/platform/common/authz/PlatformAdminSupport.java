package com.enterprise.auth.platform.common.authz;

import com.enterprise.auth.platform.config.TenantProperties;
import com.enterprise.auth.platform.dto.model.UserAccount;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PlatformAdminSupport {

    private final TenantProperties tenantProperties;

    public PlatformAdminSupport(TenantProperties tenantProperties) {
        this.tenantProperties = tenantProperties;
    }

    public boolean isPlatformSuperAdmin(UserAccount user) {
        if (user == null || !StringUtils.hasText(user.tenantId())) {
            return false;
        }
        if (!tenantProperties.platformTenantId().equals(user.tenantId())) {
            return false;
        }
        if (user.roles() != null && user.roles().contains("ADMIN")) {
            return true;
        }
        Set<String> permissions = user.permissions();
        return permissions != null && (permissions.contains("tenant:write") || permissions.contains("tenant:read"));
    }

    public boolean canSwitchTenant(UserAccount user, String requestedTenantId) {
        if (!StringUtils.hasText(requestedTenantId)) {
            return false;
        }
        return requestedTenantId.equals(user.tenantId()) || isPlatformSuperAdmin(user);
    }

    public String resolveEffectiveTenant(UserAccount user, String requestedTenantId) {
        if (canSwitchTenant(user, requestedTenantId)) {
            return requestedTenantId;
        }
        return user.tenantId();
    }
}