package com.enterprise.auth.platform.infrastructure.security;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.common.authz.PlatformAdminSupport;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.user.application.AuthenticationUser;
import com.enterprise.auth.platform.modules.user.application.UserAuthenticationFacade;
import com.enterprise.auth.platform.modules.role.application.RoleGrantQueryFacade;
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

    public SaTokenPermissionProvider(
            UserAuthenticationFacade userAuthenticationFacade,
            RoleGrantQueryFacade roleGrantQueryFacade,
            PlatformAdminSupport platformAdminSupport
    ) {
        this.userAuthenticationFacade = userAuthenticationFacade;
        this.roleGrantQueryFacade = roleGrantQueryFacade;
        this.platformAdminSupport = platformAdminSupport;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        Optional<AuthenticationUser> user = loadUser(loginId);
        Optional<List<String>> sessionPermissions = user.flatMap(loadedUser -> currentTokenSession()
                .filter(session -> activeTenantId(loadedUser).equals(sessionString(session, "permissionsTenantId")))
                .map(session -> sessionStringList(session, "permissions")));
        if (sessionPermissions.isPresent()) {
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
        Optional<AuthenticationUser> user = loadUser(loginId);
        Optional<List<String>> sessionRoles = user.flatMap(loadedUser -> currentTokenSession()
                .filter(session -> activeTenantId(loadedUser).equals(sessionString(session, "permissionsTenantId")))
                .map(session -> sessionStringList(session, "roles")));
        if (sessionRoles.isPresent()) {
            return sessionRoles.get();
        }
        return user
                .map(loadedUser -> new ArrayList<>(loadedUser.roles()))
                .orElseGet(ArrayList::new);
    }

    private java.util.Optional<AuthenticationUser> loadUser(Object loginId) {
        if (loginId == null) {
            return java.util.Optional.empty();
        }
        try {
            long userId = Long.parseLong(String.valueOf(loginId));
            String loginTenantId = currentTokenSession()
                    .map(session -> sessionString(session, "tenantId"))
                    .orElse(null);
            return runWithTenant(loginTenantId, () -> userAuthenticationFacade.findById(userId));
        } catch (NumberFormatException ignored) {
            return java.util.Optional.empty();
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
                        sessionTenantId = sessionString(session, "tenantId");
                    }
                    UserAccount account = toUserAccount(user);
                    String effectiveTenantId = platformAdminSupport.resolveEffectiveTenant(account, sessionTenantId);
                    if (!effectiveTenantId.equals(sessionString(session, "activeTenantId"))) {
                        session.set("activeTenantId", effectiveTenantId);
                    }
                    return effectiveTenantId;
                })
                .orElseGet(user::tenantId);
    }
}