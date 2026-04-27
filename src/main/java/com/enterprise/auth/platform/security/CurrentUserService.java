package com.enterprise.auth.platform.security;

import cn.dev33.satoken.exception.SaTokenContextException;
import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.auth.model.SessionPrincipal;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.tenant.TenantProperties;
import com.enterprise.auth.platform.user.model.UserAccount;
import com.enterprise.auth.platform.user.repository.UserRepository;
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

    public CurrentUserService(
            ObjectProvider<UserRepository> userRepository,
            TenantProperties tenantProperties,
            PlatformAdminSupport platformAdminSupport
    ) {
        this.userRepository = userRepository;
        this.tenantProperties = tenantProperties;
        this.platformAdminSupport = platformAdminSupport;
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
            UserAccount user = Optional.ofNullable((UserAccount) StpUtil.getTokenSession().get("testUser"))
                    .or(() -> userRepository.getObject().findById(userId))
                    .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));
            if (!user.enabled()) {
                StpUtil.logout(userId);
                throw new BusinessException("USER_DISABLED", "User is disabled");
            }
            return Optional.of(user);
        } catch (SaTokenContextException ignored) {
            return Optional.empty();
        }
    }

    private String resolveRequestedTenant(HttpServletRequest request) {
        String tenantId = request.getHeader(tenantProperties.headerName());
        if (!StringUtils.hasText(tenantId)) {
            tenantId = request.getParameter(TENANT_ID_PARAM);
        }
        return tenantId;
    }
}
