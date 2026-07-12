package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 平台超管判定。不依赖 tenant 模块 infrastructure，仅读取配置中的平台租户 ID。
 */
@Component
public class PlatformAdminSupport {

    private static final Set<String> PLATFORM_ADMIN_ROLE_CODES = Set.of("ADMIN", "PLATFORM_ADMIN", "TENANT_ADMIN");
    private static final Set<String> PLATFORM_ADMIN_PERMISSION_CODES = Set.of(
            PermissionCodes.SYSTENANT_PAGE,
            PermissionCodes.SYSTENANT_GET,
            PermissionCodes.SYSTENANT_ADD,
            PermissionCodes.SYSTENANT_EDIT,
            PermissionCodes.SYSTENANT_DEL
    );

    private final String platformTenantId;

    public PlatformAdminSupport(
            @Value("${app.tenant.platform-tenant-id:platform}") String platformTenantId
    ) {
        this.platformTenantId = StringUtils.hasText(platformTenantId) ? platformTenantId.trim() : "platform";
    }

    public boolean isPlatformSuperAdmin(UserAccount user) {
        if (user == null || !StringUtils.hasText(user.tenantId())) {
            return false;
        }
        if (!platformTenantId.equals(user.tenantId())) {
            return false;
        }
        if (user.roles() != null && user.roles().stream().anyMatch(PLATFORM_ADMIN_ROLE_CODES::contains)) {
            return true;
        }
        Set<String> permissions = user.permissions();
        return permissions != null && permissions.stream().anyMatch(PLATFORM_ADMIN_PERMISSION_CODES::contains);
    }

    public boolean usesGlobalTenantScope(UserAccount user, String activeTenantId) {
        if (!isPlatformSuperAdmin(user)) {
            return false;
        }
        return !StringUtils.hasText(activeTenantId) || platformTenantId.equals(activeTenantId);
    }

    public String platformTenantId() {
        return platformTenantId;
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