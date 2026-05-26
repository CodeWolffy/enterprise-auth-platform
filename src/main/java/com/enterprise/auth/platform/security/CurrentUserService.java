package com.enterprise.auth.platform.security;

import cn.dev33.satoken.exception.SaTokenContextException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.dto.model.SessionPrincipal;
import com.enterprise.auth.platform.service.SessionIndexService;
import com.enterprise.auth.platform.common.authz.PlatformAdminSupport;
import com.enterprise.auth.platform.common.context.AuthContextHolder;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.dto.model.UserAccount;
import com.enterprise.auth.platform.dao.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CurrentUserService {

    private final ObjectProvider<UserRepository> userRepository;
    private final PlatformAdminSupport platformAdminSupport;
    private final SessionIndexService sessionIndexService;

    public CurrentUserService(
            ObjectProvider<UserRepository> userRepository,
            PlatformAdminSupport platformAdminSupport,
            SessionIndexService sessionIndexService
    ) {
        this.userRepository = userRepository;
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
        SessionPrincipal principal = new SessionPrincipal(StpUtil.getTokenValue(), effectiveTenantId, user.tenantId());
        AuthContextHolder.set(user, principal);
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
            UserAccount user = runWithTenant(loginTenantId, () -> userRepository.getObject().findById(userId))
                    .orElseThrow(() -> {
                        kickoutCurrentToken();
                        StpUtil.checkLogin();
                        return new BusinessException("USER_NOT_FOUND", "User not found");
            });
            if (!user.enabled()) {
                StpUtil.kickout(userId);
                StpUtil.checkLogin();
            }
            int tokenSessionVersion = sessionInt(tokenSession, "sessionVersion", user.sessionVersion());
            if (tokenSessionVersion != user.sessionVersion()) {
                kickoutCurrentToken();
                StpUtil.checkLogin();
            }
            return Optional.of(user);
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

    private int sessionInt(SaSession session, String key, int fallback) {
        Object value = session.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
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

    private String sessionString(SaSession session, String key) {
        Object value = session.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
