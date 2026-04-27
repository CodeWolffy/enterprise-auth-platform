package com.enterprise.auth.platform.auth.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.audit.service.AuditService;
import com.enterprise.auth.platform.auth.dto.LoginRequest;
import com.enterprise.auth.platform.auth.dto.TokenSessionResponse;
import com.enterprise.auth.platform.auth.dto.UserSessionResponse;
import com.enterprise.auth.platform.auth.service.LoginAttemptService.LoginFailureResult;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.time.TimeSupport;
import com.enterprise.auth.platform.common.validator.PasswordValidator;
import com.enterprise.auth.platform.config.SecurityProperties;
import com.enterprise.auth.platform.persistence.entity.SysUserEntity;
import com.enterprise.auth.platform.persistence.mapper.SysUserMapper;
import com.enterprise.auth.platform.security.DataScopeService;
import com.enterprise.auth.platform.security.PasswordHasher;
import com.enterprise.auth.platform.tenant.TenantContext;
import com.enterprise.auth.platform.user.dto.CreateUserRequest;
import com.enterprise.auth.platform.user.dto.RegisterRequest;
import com.enterprise.auth.platform.user.model.UserAccount;
import com.enterprise.auth.platform.user.model.UserSummary;
import com.enterprise.auth.platform.user.repository.UserRepository;
import com.enterprise.auth.platform.user.service.management.UserManagementService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private final CaptchaService captchaService;
    private final PasswordHasher passwordHasher;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final SysUserMapper sysUserMapper;
    private final DataScopeService dataScopeService;
    private final LoginAttemptService loginAttemptService;
    private final RegisterAttemptService registerAttemptService;
    private final RegistrationPolicyService registrationPolicyService;
    private final UserManagementService userManagementService;
    private final SecurityProperties securityProperties;

    public AuthService(
            CaptchaService captchaService,
            PasswordHasher passwordHasher,
            UserRepository userRepository,
            AuditService auditService,
            SysUserMapper sysUserMapper,
            DataScopeService dataScopeService,
            LoginAttemptService loginAttemptService,
            RegisterAttemptService registerAttemptService,
            RegistrationPolicyService registrationPolicyService,
            UserManagementService userManagementService,
            SecurityProperties securityProperties
    ) {
        this.captchaService = captchaService;
        this.passwordHasher = passwordHasher;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.sysUserMapper = sysUserMapper;
        this.dataScopeService = dataScopeService;
        this.loginAttemptService = loginAttemptService;
        this.registerAttemptService = registerAttemptService;
        this.registrationPolicyService = registrationPolicyService;
        this.userManagementService = userManagementService;
        this.securityProperties = securityProperties;
    }

    public TokenSessionResponse login(LoginRequest request, HttpServletRequest servletRequest) {
        captchaService.validate(request.captchaId(), request.captchaCode());
        String tenantId = resolveLoginTenantId(request);
        String clientIp = clientIp(servletRequest);
        String previousTenantId = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenantId);

            if (loginAttemptService.isLocked(tenantId, request.username())) {
                loginAttemptService.recordBlockedAttempt(tenantId, request.username(), clientIp);
                throw new BusinessException("ACCOUNT_LOCKED", "账户已锁定，请稍后再试");
            }

            UserAccount user = userRepository.findByUsername(tenantId, request.username()).orElse(null);
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
            StpUtil.login(user.id(), new SaLoginModel()
                    .setDevice(StringUtils.hasText(request.device()) ? request.device() : "unknown")
                    .setTimeout(timeoutSeconds));
            String tokenValue = StpUtil.getTokenValue();
            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(timeoutSeconds);
            SaSession tokenSession = StpUtil.getTokenSession();
            tokenSession.set("username", user.username());
            tokenSession.set("tenantId", user.tenantId());
            tokenSession.set("clientIp", clientIp);
            tokenSession.set("device", StringUtils.hasText(request.device()) ? request.device() : "unknown");
            tokenSession.set("issuedAt", now.toEpochMilli());
            tokenSession.set("expiresAt", expiresAt.toEpochMilli());

            updateLastLogin(user.id(), clientIp);
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

    public void logout(String sessionId, String username, String tenantId) {
        StpUtil.logoutByTokenValue(sessionId);
        auditService.record("LOGOUT", username, tenantId, Map.of("sessionId", sessionId));
    }

    public List<UserSessionResponse> sessions(UserAccount currentUser) {
        return StpUtil.getTokenValueListByLoginId(currentUser.id()).stream()
                .map(token -> toSessionResponse(token, currentUser))
                .toList();
    }

    public void forceOffline(UserAccount currentUser, String sessionId) {
        Long targetUserId = resolveLoginId(sessionId);
        String targetTenantId = sessionAttribute(sessionId, "tenantId", currentUser.tenantId());
        boolean sameOwner = currentUser.id().equals(targetUserId);
        boolean canManage = currentUser.permissions().contains("session:write");
        boolean sameTenant = currentUser.tenantId().equals(targetTenantId);
        boolean visibleTarget = dataScopeService.canAccessUser(currentUser.tenantId(), targetUserId);
        if (!sameOwner && (!canManage || !sameTenant || !visibleTarget)) {
            throw new BusinessException("ACCESS_DENIED", "无权操作此会话");
        }
        StpUtil.kickoutByTokenValue(sessionId);
        auditService.record("SESSION_FORCED_OFFLINE", currentUser.username(), currentUser.tenantId(), Map.of("sessionId", sessionId));
    }

    public UserSummary register(RegisterRequest request, HttpServletRequest servletRequest) {
        String clientIp = clientIp(servletRequest);
        registerAttemptService.checkRateLimit(request.username(), clientIp);
        String defaultTenantId = registrationPolicyService.resolveDefaultTenantId();
        String previousTenantId = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(defaultTenantId);
            Set<String> defaultRoleCodes = registrationPolicyService.resolveDefaultRoleCodes();

            PasswordValidator.validate(request.password());

            if (userManagementService.existsByUsername(request.username())) {
                throw new BusinessException("USERNAME_EXISTS", "用户名已存在");
            }

            CreateUserRequest createRequest = new CreateUserRequest(
                    request.username(),
                    request.displayName(),
                    request.mobile(),
                    request.email(),
                    request.password(),
                    null,
                    true,
                    defaultRoleCodes
            );
            return userManagementService.createUser(defaultTenantId, createRequest, "system");
        } finally {
            if (StringUtils.hasText(previousTenantId)) {
                TenantContext.setTenantId(previousTenantId);
            } else {
                TenantContext.clear();
            }
        }
    }

    private UserSessionResponse toSessionResponse(String token, UserAccount fallbackUser) {
        SaSession tokenSession = StpUtil.getTokenSessionByToken(token);
        long issuedAt = sessionLong(tokenSession, "issuedAt", 0L);
        long expiresAt = sessionLong(tokenSession, "expiresAt", 0L);
        long lastAccessAt = sessionLong(tokenSession, "lastAccessAt", issuedAt);
        return new UserSessionResponse(
                token,
                sessionString(tokenSession, "username", fallbackUser.username()),
                sessionString(tokenSession, "tenantId", fallbackUser.tenantId()),
                sessionString(tokenSession, "clientIp", ""),
                sessionString(tokenSession, "device", "unknown"),
                issuedAt,
                expiresAt,
                lastAccessAt,
                StpUtil.stpLogic.getLoginIdByToken(token) != null
        );
    }

    private Long resolveLoginId(String token) {
        Object loginId = StpUtil.stpLogic.getLoginIdByToken(token);
        if (loginId == null) {
            throw new BusinessException("SESSION_NOT_FOUND", "会话不存在");
        }
        return Long.parseLong(String.valueOf(loginId));
    }

    private String sessionAttribute(String token, String key, String fallback) {
        return sessionString(StpUtil.getTokenSessionByToken(token), key, fallback);
    }

    private String sessionString(SaSession session, String key, String fallback) {
        Object value = session.get(key);
        return value == null ? fallback : String.valueOf(value);
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

    private void updateLastLogin(Long userId, String clientIp) {
        if (userId == null) {
            return;
        }
        SysUserEntity entity = sysUserMapper.selectById(userId);
        if (entity == null || (entity.getDeleted() != null && entity.getDeleted() == 1)) {
            return;
        }
        entity.setLastLoginAt(TimeSupport.utcNowDateTime());
        entity.setLastLoginIp(clientIp);
        entity.setUpdatedBy(entity.getUsername());
        sysUserMapper.updateById(entity);
    }

    private String resolveLoginTenantId(LoginRequest request) {
        String requestedTenantId = StringUtils.hasText(request.tenantId()) ? request.tenantId().trim() : TenantContext.getTenantId();
        List<String> matchedTenantIds = sysUserMapper.selectActiveTenantIdsByUsername(request.username()).stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
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
        LoginFailureResult result = loginAttemptService.recordFailure(tenantId, username, reason, clientIp);
        if (result.locked()) {
            return new BusinessException("ACCOUNT_LOCKED", "账户已锁定，请稍后再试");
        }
        return new BusinessException("BAD_CREDENTIALS", "用户名或密码错误，剩余尝试次数：" + result.remainingAttempts());
    }
}
