package com.enterprise.auth.platform.modules.auth.application;

import cn.dev33.satoken.exception.SaTokenContextException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.modules.auth.domain.SessionPrincipal;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.common.authz.PlatformAdminSupport;
import com.enterprise.auth.platform.common.context.AuthContextHolder;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.user.application.AuthenticationUser;
import com.enterprise.auth.platform.modules.user.application.UserAuthenticationFacade;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CurrentUserService {

    private final ObjectProvider<UserAuthenticationFacade> userAuthenticationFacadeProvider;
    private final PlatformAdminSupport platformAdminSupport;
    private final SessionIndexService sessionIndexService;

    public CurrentUserService(
            ObjectProvider<UserAuthenticationFacade> userAuthenticationFacadeProvider,
            PlatformAdminSupport platformAdminSupport,
            SessionIndexService sessionIndexService
    ) {
        this.userAuthenticationFacadeProvider = userAuthenticationFacadeProvider;
        this.platformAdminSupport = platformAdminSupport;
        this.sessionIndexService = sessionIndexService;
    }

    public Optional<UserAccount> currentUser() {
        return AuthContextHolder.currentUser().or(this::loadLoggedInUser);
    }

    public UserAccount requireCurrentUser() {
        return currentUser().orElseThrow(() -> new BusinessException("UNAUTHORIZED", "User is not logged in"));
    }

    public SessionPrincipal bindRequestContext(HttpServletRequest request) {
        UserAccount user = loadLoggedInUser()
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "User is not logged in"));
        SaSession tokenSession = StpUtil.getTokenSession();
        String sessionTenantId = sessionString(tokenSession, "activeTenantId");
        if (!StringUtils.hasText(sessionTenantId)) {
            sessionTenantId = sessionString(tokenSession, "tenantId");
        }
        String effectiveTenantId = platformAdminSupport.resolveEffectiveTenant(user, sessionTenantId);
        if (!effectiveTenantId.equals(sessionString(tokenSession, "activeTenantId"))) {
            tokenSession.set("activeTenantId", effectiveTenantId);
        }
        UserAccount effectiveUser = mergeSessionAuthorities(user, tokenSession);
        SessionPrincipal principal = new SessionPrincipal(StpUtil.getTokenValue(), effectiveTenantId, effectiveUser.tenantId());
        AuthContextHolder.set(effectiveUser, principal);
        return principal;
    }

    private Optional<UserAccount> loadLoggedInUser() {
        try {
            if (!StpUtil.isLogin()) {
                return Optional.empty();
            }
            long userId = StpUtil.getLoginIdAsLong();
            SaSession tokenSession = StpUtil.getTokenSession();
            String loginTenantId = sessionString(tokenSession, "tenantId");
            AuthenticationUser authenticationUser = runWithTenant(loginTenantId, () -> userAuthenticationFacadeProvider.getObject().findById(userId))
                    .orElseThrow(() -> {
                        kickoutCurrentToken();
                        StpUtil.checkLogin();
                        return new BusinessException("USER_NOT_FOUND", "User not found");
                    });
            UserAccount user = toUserAccount(authenticationUser);
            UserAccount effectiveUser = mergeSessionAuthorities(user, tokenSession);
            if (!effectiveUser.enabled()) {
                StpUtil.kickout(userId);
                StpUtil.checkLogin();
            }
            int tokenSessionVersion = sessionInt(tokenSession, "sessionVersion", effectiveUser.sessionVersion());
            if (tokenSessionVersion != effectiveUser.sessionVersion()) {
                kickoutCurrentToken();
                StpUtil.checkLogin();
            }
            return Optional.of(effectiveUser);
        } catch (SaTokenContextException ignored) {
            return Optional.empty();
        }
    }

    private void kickoutCurrentToken() {
        String tokenValue = StpUtil.getTokenValue();
        if (StringUtils.hasText(tokenValue)) {
            StpUtil.kickoutByTokenValue(tokenValue);
            sessionIndexService.remove(tokenValue);
        }
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

    private UserAccount mergeSessionAuthorities(UserAccount user, SaSession tokenSession) {
        Set<String> roles = mergeStringSet(user.roles(), tokenSession.get("roles"));
        Set<String> permissions = mergeStringSet(user.permissions(), tokenSession.get("permissions"));
        return new UserAccount(
                user.id(),
                user.tenantId(),
                user.username(),
                user.password(),
                user.enabled(),
                roles,
                permissions,
                user.customDeptIds(),
                user.dataScopeType(),
                user.sessionVersion()
        );
    }

    private Set<String> mergeStringSet(Set<String> base, Object sessionValue) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(base);
        if (sessionValue instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null) {
                    String value = String.valueOf(item).trim();
                    if (StringUtils.hasText(value)) {
                        merged.add(value);
                    }
                }
            }
        }
        return Set.copyOf(merged);
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

    private int sessionInt(SaSession session, String key, int fallback) {
        Object value = session.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String sessionString(SaSession session, String key) {
        Object value = session.get(key);
        return value == null ? null : String.valueOf(value);
    }
}