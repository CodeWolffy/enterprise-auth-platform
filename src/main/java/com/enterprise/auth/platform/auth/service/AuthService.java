package com.enterprise.auth.platform.auth.service;

import com.enterprise.auth.platform.audit.service.AuditService;
import com.enterprise.auth.platform.auth.dto.LoginRequest;
import com.enterprise.auth.platform.auth.dto.TokenResponse;
import com.enterprise.auth.platform.auth.dto.UserSessionResponse;
import com.enterprise.auth.platform.auth.model.TokenClaims;
import com.enterprise.auth.platform.auth.model.UserSession;
import com.enterprise.auth.platform.auth.store.SessionStore;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.config.PersistenceProperties;
import com.enterprise.auth.platform.config.SecurityProperties;
import com.enterprise.auth.platform.persistence.entity.SysUserEntity;
import com.enterprise.auth.platform.persistence.mapper.SysUserMapper;
import com.enterprise.auth.platform.security.DataScopeService;
import com.enterprise.auth.platform.tenant.TenantContext;
import com.enterprise.auth.platform.user.model.UserAccount;
import com.enterprise.auth.platform.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private static final long MAX_LOGIN_FAILURES = 5;
    private static final Duration LOGIN_FAILURE_WINDOW = Duration.ofMinutes(15);

    private final CaptchaService captchaService;
    private final SessionStore sessionStore;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final SecurityProperties securityProperties;
    private final PersistenceProperties persistenceProperties;
    private final SysUserMapper sysUserMapper;
    private final DataScopeService dataScopeService;
    private final StringRedisTemplate stringRedisTemplate;
    private final AuthorizationSessionService authorizationSessionService;

    public AuthService(
            CaptchaService captchaService,
            SessionStore sessionStore,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            UserRepository userRepository,
            AuditService auditService,
            SecurityProperties securityProperties,
            PersistenceProperties persistenceProperties,
            @Nullable SysUserMapper sysUserMapper,
            DataScopeService dataScopeService,
            StringRedisTemplate stringRedisTemplate,
            AuthorizationSessionService authorizationSessionService
    ) {
        this.captchaService = captchaService;
        this.sessionStore = sessionStore;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.securityProperties = securityProperties;
        this.persistenceProperties = persistenceProperties;
        this.sysUserMapper = sysUserMapper;
        this.dataScopeService = dataScopeService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.authorizationSessionService = authorizationSessionService;
    }

    public TokenResponse login(LoginRequest request, HttpServletRequest servletRequest) {
        captchaService.validate(request.captchaId(), request.captchaCode());
        String tenantId = StringUtils.hasText(request.tenantId()) ? request.tenantId() : TenantContext.getTenantId();
        String clientIp = clientIp(servletRequest);

        String lockKey = lockKey(tenantId, request.username());
        String failKey = failKey(tenantId, request.username());

        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey))) {
            auditService.record("LOGIN_BLOCKED", request.username(), tenantId,
                    Map.of("reason", "account_locked", "clientIp", clientIp));
            throw new BusinessException("ACCOUNT_LOCKED", "Account is locked. Try again later.");
        }

        UserAccount user = userRepository.findByUsername(tenantId, request.username())
                .orElse(null);
        if (user == null) {
            throw buildLoginFailure(tenantId, request.username(), failKey, lockKey, "user_not_found", clientIp);
        }

        if (!passwordEncoder.matches(request.password(), user.password())) {
            throw buildLoginFailure(tenantId, request.username(), failKey, lockKey, "bad_credentials", clientIp);
        }

        if (!user.enabled()) {
            auditService.record("LOGIN_FAILED", user.username(), tenantId,
                    Map.of("reason", "disabled", "clientIp", clientIp));
            throw new BusinessException("USER_DISABLED", "User is disabled");
        }

        stringRedisTemplate.delete(failKey);

        String sessionId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        UserSession session = new UserSession(
                sessionId,
                user.id(),
                user.username(),
                user.tenantId(),
                clientIp,
                request.device(),
                now,
                now.plus(securityProperties.refreshTokenTtl()),
                now,
                true
        );
        sessionStore.save(session);
        updateLastLogin(user.id(), clientIp);
        auditService.record("LOGIN_SUCCESS", user.username(), user.tenantId(),
                Map.of("sessionId", sessionId, "clientIp", clientIp));
        return issueTokens(user, sessionId);
    }

    public TokenResponse refresh(String refreshToken) {
        TokenClaims claims;
        try {
            claims = jwtService.decode(refreshToken);
        } catch (Exception ex) {
            throw new BusinessException("INVALID_TOKEN", "Invalid refresh token");
        }

        if (!"refresh".equals(claims.tokenType())) {
            throw new BusinessException("INVALID_TOKEN", "Invalid refresh token type");
        }

        UserSession session = sessionStore.findBySessionId(claims.sessionId())
                .orElseThrow(() -> new BusinessException("SESSION_NOT_FOUND", "Session not found"));
        if (!session.active() || Instant.now().isAfter(session.expiresAt())) {
            throw new BusinessException("SESSION_EXPIRED", "Session expired");
        }

        UserAccount user = userRepository.findById(claims.userId())
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));

        if (!user.enabled()) {
            authorizationSessionService.revoke(session.sessionId());
            throw new BusinessException("USER_DISABLED", "User disabled");
        }
        if (user.sessionVersion() != claims.sessionVersion()) {
            authorizationSessionService.revoke(session.sessionId());
            throw new BusinessException("TOKEN_VERSION_MISMATCH", "Token version invalid");
        }
        if (!user.tenantId().equals(session.tenantId()) || !user.tenantId().equals(claims.tenantId())) {
            authorizationSessionService.revoke(session.sessionId());
            throw new BusinessException("TENANT_MISMATCH", "Tenant context mismatch");
        }

        sessionStore.touch(session.sessionId());
        return issueTokens(user, session.sessionId());
    }

    public void logout(String sessionId, String username, String tenantId) {
        authorizationSessionService.revoke(sessionId);
        auditService.record("LOGOUT", username, tenantId, Map.of("sessionId", sessionId));
    }

    public List<UserSessionResponse> sessions(UserAccount currentUser) {
        return authorizationSessionService.listSessions(currentUser);
    }

    public void forceOffline(UserAccount currentUser, String sessionId) {
        AuthorizationSessionService.SessionDescriptor session = authorizationSessionService.findSessionDescriptor(sessionId)
                .orElseThrow(() -> new BusinessException("SESSION_NOT_FOUND", "Session not found"));
        boolean sameOwner = currentUser.id().equals(session.userId());
        boolean canManage = currentUser.permissions().contains("session:write");
        boolean sameTenant = currentUser.tenantId().equals(session.tenantId());
        boolean visibleTarget = dataScopeService.canAccessUser(currentUser.tenantId(), session.userId());
        if (!sameOwner && (!canManage || !sameTenant || !visibleTarget)) {
            throw new BusinessException("ACCESS_DENIED", "No permission to operate this session");
        }
        authorizationSessionService.revoke(sessionId);
        auditService.record("SESSION_FORCED_OFFLINE", currentUser.username(), currentUser.tenantId(),
                Map.of("sessionId", sessionId));
    }

    private TokenResponse issueTokens(UserAccount user, String sessionId) {
        return new TokenResponse(
                jwtService.issueAccessToken(user, sessionId),
                jwtService.issueRefreshToken(user, sessionId),
                "Bearer",
                Instant.now().plus(securityProperties.accessTokenTtl()),
                sessionId
        );
    }

    private void updateLastLogin(Long userId, String clientIp) {
        if (!persistenceProperties.databaseEnabled() || sysUserMapper == null || userId == null) {
            return;
        }
        SysUserEntity entity = sysUserMapper.selectById(userId);
        if (entity == null || (entity.getDeleted() != null && entity.getDeleted() == 1)) {
            return;
        }
        entity.setLastLoginAt(LocalDateTime.now());
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

    private String failKey(String tenantId, String username) {
        return "auth:fail:" + tenantId + ":" + username;
    }

    private String lockKey(String tenantId, String username) {
        return "auth:lock:" + tenantId + ":" + username;
    }

    private BusinessException buildLoginFailure(
            String tenantId,
            String username,
            String failKey,
            String lockKey,
            String reason,
            String clientIp
    ) {
        Long fails = stringRedisTemplate.opsForValue().increment(failKey);
        if (fails != null && fails == 1) {
            stringRedisTemplate.expire(failKey, LOGIN_FAILURE_WINDOW);
        }

        if (fails != null && fails >= MAX_LOGIN_FAILURES) {
            stringRedisTemplate.opsForValue().set(lockKey, "LOCKED", LOGIN_FAILURE_WINDOW);
            stringRedisTemplate.delete(failKey);
            auditService.record("ACCOUNT_LOCKED", username, tenantId,
                    Map.of("reason", "exceed_max_failures", "clientIp", clientIp));
            return new BusinessException("ACCOUNT_LOCKED", "Account is locked. Try again later.");
        }

        auditService.record("LOGIN_FAILED", username, tenantId, Map.of("reason", reason, "clientIp", clientIp));
        long remaining = Math.max(0, MAX_LOGIN_FAILURES - (fails == null ? 0 : fails));
        return new BusinessException("BAD_CREDENTIALS", "Username or password is incorrect, remaining attempts: " + remaining);
    }
}
