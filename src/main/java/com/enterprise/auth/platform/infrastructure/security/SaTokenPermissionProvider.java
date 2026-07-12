package com.enterprise.auth.platform.infrastructure.security;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.modules.auth.application.PlatformAdminSupport;
import com.enterprise.auth.platform.modules.auth.domain.AuthContextHolder;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.auth.application.AuthzVersionService;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.role.application.RoleGrantQueryFacade;
import com.enterprise.auth.platform.modules.user.application.AuthenticationUser;
import com.enterprise.auth.platform.modules.user.application.UserAuthenticationFacade;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SaTokenPermissionProvider implements StpInterface {

    private final UserAuthenticationFacade userAuthenticationFacade;
    private final RoleGrantQueryFacade roleGrantQueryFacade;
    private final PlatformAdminSupport platformAdminSupport;
    private final AuthzVersionService authzVersionService;

    public SaTokenPermissionProvider(
            UserAuthenticationFacade userAuthenticationFacade,
            RoleGrantQueryFacade roleGrantQueryFacade,
            PlatformAdminSupport platformAdminSupport,
            AuthzVersionService authzVersionService
    ) {
        this.userAuthenticationFacade = userAuthenticationFacade;
        this.roleGrantQueryFacade = roleGrantQueryFacade;
        this.platformAdminSupport = platformAdminSupport;
        this.authzVersionService = authzVersionService;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        Optional<UserAccount> bound = AuthContextHolder.currentUser().filter(user -> matchesLoginId(user, loginId));
        if (bound.isPresent()) {
            UserAccount user = bound.get();
            Optional<List<String>> sessionPermissions = currentTokenSession()
                    .filter(session -> sessionAuthoritiesFresh(session, activeTenantId(user)))
                    .map(session -> sessionStringList(session, "permissions"));
            if (sessionPermissions.isPresent() && sessionPermissions.get() != null) {
                return sessionPermissions.get();
            }
            if (user.permissions() != null && !user.permissions().isEmpty()) {
                return new ArrayList<>(user.permissions());
            }
            return new ArrayList<>(roleGrantQueryFacade.resolveGrantKeys(
                    activeTenantId(user),
                    user.roles(),
                    platformAdminSupport.isPlatformSuperAdmin(user)
            ));
        }

        Optional<AuthenticationUser> user = loadUser(loginId);
        Optional<List<String>> sessionPermissions = user.flatMap(loadedUser -> currentTokenSession()
                .filter(session -> sessionAuthoritiesFresh(session, activeTenantId(loadedUser)))
                .map(session -> sessionStringList(session, "permissions")));
        if (sessionPermissions.isPresent() && sessionPermissions.get() != null) {
            return sessionPermissions.get();
        }
        return user
                .map(loadedUser -> new ArrayList<>(roleGrantQueryFacade.resolveGrantKeys(
                        activeTenantId(loadedUser),
                        loadedUser.roles(),
                        platformAdminSupport.isPlatformSuperAdmin(toUserAccount(loadedUser))
                )))
                .orElseGet(ArrayList::new);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Optional<UserAccount> bound = AuthContextHolder.currentUser().filter(user -> matchesLoginId(user, loginId));
        if (bound.isPresent()) {
            UserAccount user = bound.get();
            Optional<List<String>> sessionRoles = currentTokenSession()
                    .filter(session -> sessionAuthoritiesFresh(session, activeTenantId(user)))
                    .map(session -> sessionStringList(session, "roles"));
            if (sessionRoles.isPresent() && sessionRoles.get() != null) {
                return sessionRoles.get();
            }
            return new ArrayList<>(user.roles());
        }

        Optional<AuthenticationUser> user = loadUser(loginId);
        Optional<List<String>> sessionRoles = user.flatMap(loadedUser -> currentTokenSession()
                .filter(session -> sessionAuthoritiesFresh(session, activeTenantId(loadedUser)))
                .map(session -> sessionStringList(session, "roles")));
        if (sessionRoles.isPresent() && sessionRoles.get() != null) {
            return sessionRoles.get();
        }
        return user
                .map(loadedUser -> new ArrayList<>(loadedUser.roles()))
                .orElseGet(ArrayList::new);
    }

    private boolean matchesLoginId(UserAccount user, Object loginId) {
        if (user == null || loginId == null) {
            return false;
        }
        return String.valueOf(user.id()).equals(String.valueOf(loginId));
    }

    private boolean sessionAuthoritiesFresh(SaSession session, String activeTenantId) {
        if (!StringUtils.hasText(activeTenantId)) {
            return false;
        }
        String permissionsTenantId = sessionString(session, "permissionsTenantId");
        if (!activeTenantId.equals(permissionsTenantId)) {
            return false;
        }
        long globalVersion = sessionLong(session, "authzGlobalVersion", -1L);
        long tenantVersion = sessionLong(session, "authzTenantVersion", -1L);
        AuthzVersionService.Versions currentVersions = authzVersionService.currentVersions(activeTenantId);
        return globalVersion == currentVersions.global() && tenantVersion == currentVersions.tenant();
    }

    private Optional<AuthenticationUser> loadUser(Object loginId) {
        if (loginId == null) {
            return Optional.empty();
        }
        try {
            long userId = Long.parseLong(String.valueOf(loginId));
            String loginTenantId = currentTokenSession()
                    .map(session -> sessionString(session, "tenantId"))
                    .orElse(null);
            return runWithTenant(loginTenantId, () -> userAuthenticationFacade.findById(userId));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private Optional<SaSession> currentTokenSession() {
        try {
            String tokenValue = StpUtil.getTokenValue();
            if (!StringUtils.hasText(tokenValue)) {
                return Optional.empty();
            }
            return Optional.of(StpUtil.getTokenSessionByToken(tokenValue));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private <T> T runWithTenant(String tenantId, Supplier<T> supplier) {
        if (!StringUtils.hasText(tenantId)) {
            return supplier.get();
        }
        String previousTenantId = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenantId);
            return supplier.get();
        } finally {
            if (StringUtils.hasText(previousTenantId)) {
                TenantContext.setTenantId(previousTenantId);
            } else {
                TenantContext.clear();
            }
        }
    }

    private List<String> sessionStringList(SaSession session, String key) {
        Object value = session.get(key);
        if (value instanceof Iterable<?> iterable) {
            List<String> values = new ArrayList<>();
            iterable.forEach(item -> {
                if (item != null) {
                    values.add(String.valueOf(item));
                }
            });
            return values;
        }
        return null;
    }

    private String sessionString(SaSession session, String key) {
        Object value = session.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private long sessionLong(SaSession session, String key, long fallback) {
        Object value = session.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private UserAccount toUserAccount(AuthenticationUser user) {
        return new UserAccount(
                user.id(),
                user.tenantId(),
                user.username(),
                user.password(),
                user.enabled(),
                user.roles(),
                user.permissions(),
                user.customDeptIds(),
                user.dataScopeType(),
                user.sessionVersion()
        );
    }

    private String activeTenantId(AuthenticationUser user) {
        return currentTokenSession()
                .map(session -> {
                    String sessionTenantId = sessionString(session, "activeTenantId");
                    if (!StringUtils.hasText(sessionTenantId)) {
                        return "";
                    }
                    UserAccount account = toUserAccount(user);
                    return platformAdminSupport.resolveEffectiveTenant(account, sessionTenantId);
                })
                .orElseGet(user::tenantId);
    }

    private String activeTenantId(UserAccount user) {
        return AuthContextHolder.currentSession()
                .map(session -> session.tenantId())
                .or(() -> currentTokenSession().map(session -> sessionString(session, "activeTenantId")))
                .filter(StringUtils::hasText)
                .map(tenantId -> platformAdminSupport.resolveEffectiveTenant(user, tenantId))
                .orElseGet(user::tenantId);
    }
}
