package com.enterprise.auth.platform.modules.auth.application;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.auth.infrastructure.SecurityProperties;
import com.enterprise.auth.platform.modules.security.application.SecurityPolicyApplicationService;
import com.enterprise.auth.platform.modules.tenant.application.TenantProfileFacade;
import com.enterprise.auth.platform.modules.user.application.AuthenticationUser;
import com.enterprise.auth.platform.modules.user.application.UserAuthenticationFacade;
import com.enterprise.auth.platform.modules.auth.interfaces.LoginRequest;
import com.enterprise.auth.platform.modules.auth.interfaces.TokenSessionResponse;
import com.enterprise.auth.platform.modules.auth.domain.PasswordHasher;
import com.enterprise.auth.platform.modules.log.application.LogPublisher;
import com.enterprise.auth.platform.modules.log.domain.event.LoginLogEvent;
import com.enterprise.auth.platform.common.web.ClientIpResolver;
import com.enterprise.auth.platform.common.web.IpLocationResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LoginApplicationService {

    private final CaptchaService captchaService;
    private final PasswordHasher passwordHasher;
    private final UserAuthenticationFacade userAuthenticationFacade;
    private final LogPublisher logPublisher;
    private final LoginAttemptService loginAttemptService;
    private final RegistrationPolicyService registrationPolicyService;
    private final SecurityProperties securityProperties;
    private final SessionIndexService sessionIndexService;
    private final ClientIpResolver clientIpResolver;
    private final IpLocationResolver ipLocationResolver;
    private final SecurityPolicyApplicationService securityPolicyApplicationService;
    private final TenantProfileFacade tenantProfileFacade;

    public LoginApplicationService(
            CaptchaService captchaService,
            PasswordHasher passwordHasher,
            UserAuthenticationFacade userAuthenticationFacade,
            LogPublisher logPublisher,
            LoginAttemptService loginAttemptService,
            RegistrationPolicyService registrationPolicyService,
            SecurityProperties securityProperties,
            SessionIndexService sessionIndexService,
            ClientIpResolver clientIpResolver,
            IpLocationResolver ipLocationResolver,
            SecurityPolicyApplicationService securityPolicyApplicationService,
            TenantProfileFacade tenantProfileFacade
    ) {
        this.captchaService = captchaService;
        this.passwordHasher = passwordHasher;
        this.userAuthenticationFacade = userAuthenticationFacade;
        this.logPublisher = logPublisher;
        this.loginAttemptService = loginAttemptService;
        this.registrationPolicyService = registrationPolicyService;
        this.securityProperties = securityProperties;
        this.sessionIndexService = sessionIndexService;
        this.clientIpResolver = clientIpResolver;
        this.ipLocationResolver = ipLocationResolver;
        this.securityPolicyApplicationService = securityPolicyApplicationService;
            this.tenantProfileFacade = tenantProfileFacade;
    }

    public TokenSessionResponse login(LoginRequest request, HttpServletRequest servletRequest) {
        // 先验证验证码token是否存在（不消耗），确保用户已完成滑块验证
        if (!captchaService.secondaryVerifyWithoutRemoval(request.captchaId())) {
            throw new BusinessException("CAPTCHA_INVALID", "验证码未通过校验或已过期");
        }
        String tenantId = resolveLoginTenantId(request);
        tenantProfileFacade.ensureTenantAccessible(tenantId);
        String clientIp = clientIpResolver.resolve(servletRequest);
        String userAgent = servletRequest.getHeader("User-Agent");
        String browser = parseBrowser(userAgent);
        String os = parseOs(userAgent);
        String location = ipLocationResolver.resolve(clientIp);
        String previousTenantId = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenantId);

            if (loginAttemptService.isLocked(tenantId, request.username())) {
                loginAttemptService.recordBlockedAttempt(tenantId, request.username(), clientIp, browser, os, location);
                throw new BusinessException("ACCOUNT_LOCKED", "账户已锁定，请稍后再试");
            }

            AuthenticationUser user = userAuthenticationFacade.findByUsername(tenantId, request.username()).orElse(null);
            if (user == null) {
                throw buildLoginFailure(tenantId, request.username(), "user_not_found", clientIp, browser, os, location);
            }
            if (!passwordHasher.matches(request.password(), user.password())) {
                throw buildLoginFailure(tenantId, request.username(), "bad_credentials", clientIp, browser, os, location);
            }
            if (!user.enabled()) {
                logPublisher.publish(new LoginLogEvent(user.username(), tenantId, "FAILED", "用户已禁用",
                        clientIp, location, browser, os));
                throw new BusinessException("USER_DISABLED", "用户已禁用");
            }

            // 密码验证成功后，才真正消耗验证码token（防止重放攻击）
            captchaService.secondaryVerify(request.captchaId());

            loginAttemptService.clearFailures(tenantId, request.username());
            long timeoutSeconds = securityProperties.sessionTtl().toSeconds();
            String device = StringUtils.hasText(request.device()) ? request.device() : "unknown";
            StpUtil.login(user.id(), new SaLoginParameter()
                    .setDeviceType(device)
                    .setTimeout(timeoutSeconds));
            String tokenValue = StpUtil.getTokenValue();
            PasswordChangeState passwordChangeState = resolvePasswordChangeState(user, clientIp, browser, os, location);
            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(timeoutSeconds);
            SaSession tokenSession = StpUtil.getTokenSession();
            tokenSession.set("username", user.username());
            tokenSession.set("userId", user.id());
            tokenSession.set("tenantId", user.tenantId());
            tokenSession.set("activeTenantId", user.tenantId());
            tokenSession.set("sessionVersion", user.sessionVersion());
            tokenSession.set("passwordChangeRequired", passwordChangeState.required());
            tokenSession.set("passwordChangeReason", passwordChangeState.reason());
            tokenSession.set("clientIp", clientIp);
            tokenSession.set("loginLocation", location);
            tokenSession.set("device", device);
            tokenSession.set("issuedAt", now.toEpochMilli());
            tokenSession.set("expiresAt", expiresAt.toEpochMilli());
            sessionIndexService.register(
                    tokenValue,
                    user.id(),
                    user.username(),
                    user.tenantId(),
                    clientIp,
                    location,
                    device,
                    now.toEpochMilli(),
                    expiresAt.toEpochMilli()
            );

            userAuthenticationFacade.recordLoginSuccess(user.id(), clientIp);
            logPublisher.publish(new LoginLogEvent(user.username(), user.tenantId(), "SUCCESS", "登录成功",
                    clientIp, location, browser, os));
            return new TokenSessionResponse(
                    user.tenantId(),
                    tokenValue,
                    TimeSupport.toEpochMilli(expiresAt),
                    passwordChangeState.required(),
                    passwordChangeState.reason()
            );
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

    private PasswordChangeState resolvePasswordChangeState(AuthenticationUser user, String clientIp, String browser, String os, String location) {
        if (user.mustChangePassword()) {
            return new PasswordChangeState(true, "FORCE_CHANGE");
        }
        int passwordExpireDays = securityPolicyApplicationService.effectivePolicy(user.tenantId()).passwordExpireDays();
        if (passwordExpireDays > 0 && user.passwordUpdatedAt() != null) {
            LocalDateTime expiresAt = user.passwordUpdatedAt().plus(passwordExpireDays, ChronoUnit.DAYS);
            if (!expiresAt.isAfter(TimeSupport.utcNowDateTime())) {
                logPublisher.publish(new LoginLogEvent(user.username(), user.tenantId(), "FAILED", "密码已过期",
                        clientIp, location, browser, os));
                return new PasswordChangeState(true, "PASSWORD_EXPIRED");
            }
        }
        return new PasswordChangeState(false, "");
    }

    private static String parseBrowser(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) return null;
        String ua = userAgent;
        if (ua.contains("Edg/")) return "Edge";
        if (ua.contains("Chrome/")) return "Chrome";
        if (ua.contains("Safari/") && !ua.contains("Chrome")) return "Safari";
        if (ua.contains("Firefox/")) return "Firefox";
        if (ua.contains("OPR/") || ua.contains("Opera/")) return "Opera";
        return null;
    }

    private static String parseOs(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) return null;
        String ua = userAgent;
        if (ua.contains("Windows NT 10")) return "Windows 10";
        if (ua.contains("Windows NT 6.3")) return "Windows 8.1";
        if (ua.contains("Windows NT 6.1")) return "Windows 7";
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Mac OS X")) return "macOS";
        if (ua.contains("Linux") && !ua.contains("Android")) return "Linux";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad")) return "iOS";
        return null;
    }

    private record PasswordChangeState(boolean required, String reason) {
    }

    private BusinessException buildLoginFailure(
            String tenantId,
            String username,
            String reason,
            String clientIp,
            String browser,
            String os,
            String location
    ) {
        LoginAttemptService.LoginFailureResult result = loginAttemptService.recordFailure(tenantId, username, reason, clientIp, browser, os, location);
        if (result.locked()) {
            return new BusinessException("ACCOUNT_LOCKED", "账户已锁定，请稍后再试");
        }
        return new BusinessException("BAD_CREDENTIALS", "用户名或密码错误，剩余尝试次数：" + result.remainingAttempts());
    }
}