package com.enterprise.auth.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.session.SaSession;
import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.modules.auth.application.AuthzVersionService;
import com.enterprise.auth.platform.modules.auth.application.CurrentUserService;
import com.enterprise.auth.platform.modules.auth.application.PlatformAdminSupport;
import com.enterprise.auth.platform.modules.auth.application.SessionIndexService;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.user.application.UserAuthenticationFacade;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

class CurrentUserServiceTest {

    @Test
    void legacySnapshotWithoutVersionsShouldBeReplacedByAuthoritativeAuthorities() {
        AuthzVersionService authzVersionService = mock(AuthzVersionService.class);
        when(authzVersionService.currentVersionsFresh("tenant-a"))
                .thenReturn(new AuthzVersionService.Versions(7L, 11L));
        CurrentUserService service = service(authzVersionService);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("activeTenantId", "tenant-a");
        attributes.put("permissionsTenantId", "tenant-a");
        attributes.put("roles", List.of("OLD_ADMIN"));
        attributes.put("permissions", List.of("system:dangerous:delete"));
        SaSession session = session(attributes);
        UserAccount authoritative = user(Set.of("USER"), Set.of("profile:read"));

        UserAccount effective = merge(service, authoritative, session);

        assertThat(effective.roles()).containsExactly("USER");
        assertThat(effective.permissions()).containsExactly("profile:read");
        assertThat(attributes.get("roles")).isEqualTo(List.of("USER"));
        assertThat(attributes.get("permissions")).isEqualTo(List.of("profile:read"));
        assertThat(attributes.get("authzGlobalVersion")).isEqualTo(7L);
        assertThat(attributes.get("authzTenantVersion")).isEqualTo(11L);
        verify(authzVersionService).currentVersionsFresh("tenant-a");
    }

    @Test
    void versionedMatchingSnapshotShouldRemainCompatible() {
        AuthzVersionService authzVersionService = mock(AuthzVersionService.class);
        when(authzVersionService.currentVersions("tenant-a"))
                .thenReturn(new AuthzVersionService.Versions(7L, 11L));
        CurrentUserService service = service(authzVersionService);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("activeTenantId", "tenant-a");
        attributes.put("permissionsTenantId", "tenant-a");
        attributes.put("roles", List.of("TENANT_OPERATOR"));
        attributes.put("permissions", List.of("orders:read"));
        attributes.put("authzGlobalVersion", 7L);
        attributes.put("authzTenantVersion", 11L);

        UserAccount effective = merge(service, user(Set.of("USER"), Set.of("profile:read")), session(attributes));

        assertThat(effective.roles()).containsExactlyInAnyOrder("USER", "TENANT_OPERATOR");
        assertThat(effective.permissions()).containsExactlyInAnyOrder("profile:read", "orders:read");
        verify(authzVersionService, never()).currentVersionsFresh("tenant-a");
    }

    @SuppressWarnings("unchecked")
    private CurrentUserService service(AuthzVersionService authzVersionService) {
        return new CurrentUserService(
                mock(ObjectProvider.class),
                mock(PlatformAdminSupport.class),
                mock(SessionIndexService.class),
                authzVersionService
        );
    }

    private SaSession session(Map<String, Object> attributes) {
        SaSession session = mock(SaSession.class);
        when(session.get(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> attributes.get(invocation.getArgument(0, String.class)));
        when(session.set(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    attributes.put(invocation.getArgument(0, String.class), invocation.getArgument(1));
                    return session;
                });
        return session;
    }

    private UserAccount user(Set<String> roles, Set<String> permissions) {
        return new UserAccount(1L, "tenant-a", "alice", "secret", true, roles, permissions,
                Set.of(), DataScopeType.SELF, 3);
    }

    private UserAccount merge(CurrentUserService service, UserAccount user, SaSession session) {
        return (UserAccount) ReflectionTestUtils.invokeMethod(service, "mergeSessionAuthorities", user, session);
    }
}
