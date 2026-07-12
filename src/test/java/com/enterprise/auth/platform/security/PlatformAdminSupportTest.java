package com.enterprise.auth.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.modules.auth.application.PlatformAdminSupport;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformAdminSupportTest {

    private final PlatformAdminSupport support = new PlatformAdminSupport("platform");

    @Test
    void platformTenantAdminRoleShouldBePlatformSuperAdmin() {
        UserAccount user = user("platform", Set.of("TENANT_ADMIN"), Set.of());

        assertThat(support.isPlatformSuperAdmin(user)).isTrue();
    }

    @Test
    void platformTenantManagementPermissionShouldBePlatformSuperAdmin() {
        UserAccount user = user("platform", Set.of(), Set.of(PermissionCodes.SYSTENANT_PAGE));

        assertThat(support.isPlatformSuperAdmin(user)).isTrue();
    }

    @Test
    void tenantAdminOutsidePlatformTenantShouldNotBePlatformSuperAdmin() {
        UserAccount user = user("tenant-a", Set.of("TENANT_ADMIN"), Set.of(PermissionCodes.SYSTENANT_PAGE));

        assertThat(support.isPlatformSuperAdmin(user)).isFalse();
    }

    private static UserAccount user(String tenantId, Set<String> roles, Set<String> permissions) {
        return new UserAccount(
                1L,
                tenantId,
                "admin",
                "{noop}ignored",
                true,
                roles,
                permissions,
                Set.of(),
                DataScopeType.ALL,
                1
        );
    }
}