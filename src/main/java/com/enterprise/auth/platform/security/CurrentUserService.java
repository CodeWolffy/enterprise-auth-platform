package com.enterprise.auth.platform.security;

import cn.dev33.satoken.exception.SaTokenContextException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.dto.model.SessionPrincipal;
import com.enterprise.auth.platform.service.SessionIndexService;
import com.enterprise.auth.platform.common.convention.exception.BusinessException;
import com.enterprise.auth.platform.config.TenantProperties;
import com.enterprise.auth.platform.dto.model.UserAccount;
import com.enterprise.auth.platform.dao.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CurrentUserService {

    private static final String TENANT_ID_PARAM = "tenantId";

    private final ObjectProvider<UserRepository> userRepository;
    private final TenantProperties tenantProperties;
    private final PlatformAdminSupport platformAdminSupport;
    private final SessionIndexService sessionIndexService;

    public CurrentUserService(
            ObjectProvider<UserRepository> userRepository,
            TenantProperties tenantProperties,
            PlatformAdminSupport platformAdminSupport,
            SessionIndexService sessionIndexService
    ) {
        this.userRepository = userRepository;
        this.tenantProperties = tenantProperties;
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
        String requestedTenantId = resolveRequestedTenant(request);
        String effectiveTenantId = platformAdminSupport.resolveEffectiveTenant(user, requestedTenantId);
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
            UserAccount user = userRepository.getObject().findById(userId)
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

    private String resolveRequestedTenant(HttpServletRequest request) {
        String tenantId = request.getHeader(tenantProperties.headerName());
        if (!StringUtils.hasText(tenantId)) {
            tenantId = request.getParameter(TENANT_ID_PARAM);
        }
        return tenantId;
    }
}
