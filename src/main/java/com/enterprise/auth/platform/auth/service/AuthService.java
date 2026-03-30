package com.enterprise.auth.platform.auth.service;

import com.enterprise.auth.platform.audit.service.AuditService;
import com.enterprise.auth.platform.auth.dto.CookieSessionResponse;
import com.enterprise.auth.platform.auth.dto.LoginRequest;
import com.enterprise.auth.platform.auth.dto.UserSessionResponse;
import com.enterprise.auth.platform.auth.model.UserSession;
import com.enterprise.auth.platform.auth.service.LoginAttemptService.LoginFailureResult;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.time.TimeSupport;
import com.enterprise.auth.platform.common.validator.PasswordValidator;
import com.enterprise.auth.platform.config.PersistenceProperties;
import com.enterprise.auth.platform.persistence.entity.SysUserEntity;
import com.enterprise.auth.platform.persistence.mapper.SysUserMapper;
import com.enterprise.auth.platform.security.DataScopeService;
import com.enterprise.auth.platform.tenant.TenantContext;
import com.enterprise.auth.platform.user.dto.CreateUserRequest;
import com.enterprise.auth.platform.user.dto.RegisterRequest;
import com.enterprise.auth.platform.user.model.UserAccount;
import com.enterprise.auth.platform.user.model.UserSummary;
import com.enterprise.auth.platform.user.repository.UserRepository;
import com.enterprise.auth.platform.user.service.management.UserManagementService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private final CaptchaService captchaService;
    private final SessionService sessionService;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final PersistenceProperties persistenceProperties;
    private final SysUserMapper sysUserMapper;
    private final DataScopeService dataScopeService;
    private final LoginAttemptService loginAttemptService;
    private final RegisterAttemptService registerAttemptService;
    private final RegistrationPolicyService registrationPolicyService;
    private final UserManagementService userManagementService;

    public AuthService(
            CaptchaService captchaService,
            SessionService sessionService,
            PasswordEncoder passwordEncoder,
            UserRepository userRepository,
            AuditService auditService,
            PersistenceProperties persistenceProperties,
            @Nullable SysUserMapper sysUserMapper,
            DataScopeService dataScopeService,
            LoginAttemptService loginAttemptService,
            RegisterAttemptService registerAttemptService,
            RegistrationPolicyService registrationPolicyService,
            UserManagementService userManagementService
    ) {
        this.captchaService = captchaService;
        this.sessionService = sessionService;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.persistenceProperties = persistenceProperties;
        this.sysUserMapper = sysUserMapper;
        this.dataScopeService = dataScopeService;
        this.loginAttemptService = loginAttemptService;
        this.registerAttemptService = registerAttemptService;
        this.registrationPolicyService = registrationPolicyService;
        this.userManagementService = userManagementService;
    }

    public CookieSessionResponse login(LoginRequest request, HttpServletRequest servletRequest) {
        captchaService.validate(request.captchaId(), request.captchaCode());
        String tenantId = StringUtils.hasText(request.tenantId()) ? request.tenantId() : TenantContext.getTenantId();
        if (!StringUtils.hasText(tenantId)) {
            tenantId = registrationPolicyService.resolveDefaultTenantId();
        }
        String clientIp = clientIp(servletRequest);

        if (loginAttemptService.isLocked(tenantId, request.username())) {
            loginAttemptService.recordBlockedAttempt(tenantId, request.username(), clientIp);
            throw new BusinessException("ACCOUNT_LOCKED", "账户已锁定，请稍后再试");
        }

        UserAccount user = userRepository.findByUsername(tenantId, request.username())
                .orElse(null);
        if (user == null) {
            throw buildLoginFailure(tenantId, request.username(), "user_not_found", clientIp);
        }
        if (!passwordEncoder.matches(request.password(), user.password())) {
            throw buildLoginFailure(tenantId, request.username(), "bad_credentials", clientIp);
        }
        if (!user.enabled()) {
            auditService.record("LOGIN_FAILED", user.username(), tenantId,
                    Map.of("reason", "disabled", "clientIp", clientIp));
            throw new BusinessException("USER_DISABLED", "用户已禁用");
        }

        loginAttemptService.clearFailures(tenantId, request.username());
        UserSession session = sessionService.createSession(
                user.id(),
                user.username(),
                user.tenantId(),
                clientIp,
                request.device()
        );
        updateLastLogin(user.id(), clientIp);
        auditService.record("LOGIN_SUCCESS", user.username(), user.tenantId(),
                Map.of("sessionId", session.sessionId(), "clientIp", clientIp));
        return new CookieSessionResponse(
                user.tenantId(),
                session.sessionId(),
                TimeSupport.toEpochMilli(session.expiresAt())
        );
    }

    public void logout(String sessionId, String username, String tenantId) {
        sessionService.deactivate(sessionId);
        auditService.record("LOGOUT", username, tenantId, Map.of("sessionId", sessionId));
    }

    public List<UserSessionResponse> sessions(UserAccount currentUser) {
        return sessionService.listSessions(currentUser.id()).stream()
                .sorted((left, right) -> right.issuedAt().compareTo(left.issuedAt()))
                .map(session -> new UserSessionResponse(
                        session.sessionId(),
                        session.username(),
                        session.tenantId(),
                        session.clientIp(),
                        session.device(),
                        TimeSupport.toEpochMilli(session.issuedAt()),
                        TimeSupport.toEpochMilli(session.expiresAt()),
                        TimeSupport.toEpochMilli(session.lastAccessAt()),
                        session.active()
                ))
                .toList();
    }

    public void forceOffline(UserAccount currentUser, String sessionId) {
        UserSession session = sessionService.findSession(sessionId)
                .orElseThrow(() -> new BusinessException("SESSION_NOT_FOUND", "会话不存在"));
        boolean sameOwner = currentUser.id().equals(session.userId());
        boolean canManage = currentUser.permissions().contains("session:write");
        boolean sameTenant = currentUser.tenantId().equals(session.tenantId());
        boolean visibleTarget = dataScopeService.canAccessUser(currentUser.tenantId(), session.userId());
        if (!sameOwner && (!canManage || !sameTenant || !visibleTarget)) {
            throw new BusinessException("ACCESS_DENIED", "无权操作此会话");
        }
        sessionService.deactivate(sessionId);
        auditService.record("SESSION_FORCED_OFFLINE", currentUser.username(), currentUser.tenantId(),
                Map.of("sessionId", sessionId));
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

            if (userManagementService.existsByUsername(defaultTenantId, request.username())) {
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

    private void updateLastLogin(Long userId, String clientIp) {
        if (!persistenceProperties.databaseEnabled() || sysUserMapper == null || userId == null) {
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
        return new BusinessException(
                "BAD_CREDENTIALS",
                "用户名或密码错误，剩余尝试次数：" + result.remainingAttempts()
        );
    }
}
