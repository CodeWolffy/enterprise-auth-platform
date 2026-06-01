package com.enterprise.auth.platform.modules.auth.application;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.auth.infrastructure.SecurityProperties;
import com.enterprise.auth.platform.modules.user.application.AuthenticationUser;
import com.enterprise.auth.platform.modules.user.application.UserAuthenticationFacade;
import com.enterprise.auth.platform.modules.auth.interfaces.LoginRequest;
import com.enterprise.auth.platform.modules.auth.interfaces.TokenSessionResponse;
import com.enterprise.auth.platform.modules.auth.domain.PasswordHasher;
import com.enterprise.auth.platform.modules.audit.application.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LoginApplicationService {

    private final CaptchaService captchaService;
    private final PasswordHasher passwordHasher;
    private final UserAuthenticationFacade userAuthenticationFacade;
    private final AuditService auditService;
    private final LoginAttemptService loginAttemptService;
    private final RegistrationPolicyService registrationPolicyService;
    private final SecurityProperties securityProperties;
    private final SessionIndexService sessionIndexService;

    public LoginApplicationService(
            CaptchaService captchaService,
            PasswordHasher passwordHasher,
            UserAuthenticationFacade userAuthenticationFacade,
            AuditService auditService,
            LoginAttemptService loginAttemptService,
            RegistrationPolicyService registrationPolicyService,
            SecurityProperties securityProperties,
            SessionIndexService sessionIndexService
    ) {
        this.captchaService = captchaService;
        this.passwordHasher = passwordHasher;
        this.userAuthenticationFacade = userAuthenticationFacade;
        this.auditService = auditService;
        this.loginAttemptService = loginAttemptService;
        this.registrationPolicyService = registrationPolicyService;
        this.securityProperties = securityProperties;
        this.sessionIndexService = sessionIndexService;
    }

    public TokenSessionResponse login(LoginRequest request, HttpServletRequest servletRequest) {
        captchaService.secondaryVerify(request.captchaId());
        String tenantId = resolveLoginTenantId(request);
        String clientIp = clientIp(servletRequest);
        String previousTenantId = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenantId);

            if (loginAttemptService.isLocked(tenantId, request.username())) {
                loginAttemptService.recordBlockedAttempt(tenantId, request.username(), clientIp);
                throw new BusinessException("ACCOUNT_LOCKED", "账户已锁定，请稍后再试");
            }

            AuthenticationUser user = userAuthenticationFacade.findByUsername(tenantId, request.username()).orElse(null);
            if (user == null) {
                throw buildLoginFailure(tenantId, request.username(), "user_not_found", clientIp);
            }
            if (!passwordHasher.matches(request.password(), user.password())) {
                throw buildLoginFailure(tenantId, request.username(), "bad_credentials", clientIp);
            }
            if (!user.enabled()) {
                auditService.record("LOGIN_FAILED", user.username(), tenantId, Map.of("reason", "disabled", "clientIp", clientIp));
                throw new BusinessException("USER_DISABLED", "用户已禁用");
            }

            loginAttemptService.clearFailures(tenantId, request.username());
            long timeoutSeconds = securityProperties.sessionTtl().toSeconds();
            String device = StringUtils.hasText(request.device()) ? request.device() : "unknown";
            StpUtil.login(user.id(), new SaLoginModel()
                    .setDevice(device)
                    .setTimeout(timeoutSeconds));
            String tokenValue = StpUtil.getTokenValue();
            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(timeoutSeconds);
            SaSession tokenSession = StpUtil.getTokenSession();
            tokenSession.set("username", user.username());
            tokenSession.set("userId", user.id());
            tokenSession.set("tenantId", user.tenantId());
            tokenSession.set("activeTenantId", user.tenantId());
            tokenSession.set("sessionVersion", user.sessionVersion());
            tokenSession.set("clientIp", clientIp);
            tokenSession.set("device", device);
            tokenSession.set("issuedAt", now.toEpochMilli());
            tokenSession.set("expiresAt", expiresAt.toEpochMilli());
            sessionIndexService.register(
                    tokenValue,
                    user.id(),
                    user.username(),
                    user.tenantId(),
                    clientIp,
                    device,
                    now.toEpochMilli(),
                    expiresAt.toEpochMilli()
            );

            userAuthenticationFacade.recordLoginSuccess(user.id(), clientIp);
            auditService.record("LOGIN_SUCCESS", user.username(), user.tenantId(), Map.of("sessionId", tokenValue, "clientIp", clientIp));
            return new TokenSessionResponse(user.tenantId(), tokenValue, TimeSupport.toEpochMilli(expiresAt));
        } finally {
            if (StringUtils.hasText(previousTenantId)) {
                TenantContext.setTenantId(previousTenantId);
            } else {
                TenantContext.clear();
            }
        }
    }

    private String resolveLoginTenantId(LoginRequest request) {
        String requestedTenantId = StringUtils.hasText(request.tenantId()) ? request.tenantId().trim() : TenantContext.getTenantId();
        List<String> matchedTenantIds = userAuthenticationFacade.activeTenantIdsByUsername(request.username());
        if (matchedTenantIds.size() == 1) {
            return matchedTenantIds.get(0);
        }
        if (matchedTenantIds.size() > 1) {
            throw new BusinessException("USERNAME_CONFLICT", "用户名数据存在冲突，请联系管理员");
        }
        if (StringUtils.hasText(requestedTenantId)) {
            return requestedTenantId;
        }
        return registrationPolicyService.resolveDefaultTenantId();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private BusinessException buildLoginFailure(
            String tenantId,
            String username,
            String reason,
            String clientIp
    ) {
        LoginAttemptService.LoginFailureResult result = loginAttemptService.recordFailure(tenantId, username, reason, clientIp);
        if (result.locked()) {
            return new BusinessException("ACCOUNT_LOCKED", "账户已锁定，请稍后再试");
        }
        return new BusinessException("BAD_CREDENTIALS", "用户名或密码错误，剩余尝试次数：" + result.remainingAttempts());
    }
}