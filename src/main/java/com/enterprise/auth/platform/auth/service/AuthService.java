package com.enterprise.auth.platform.auth.service;

import com.enterprise.auth.platform.audit.service.AuditService;
import com.enterprise.auth.platform.auth.dto.LoginRequest;
import com.enterprise.auth.platform.auth.dto.TokenResponse;
import com.enterprise.auth.platform.auth.dto.UserSessionResponse;
import com.enterprise.auth.platform.auth.model.TokenClaims;
import com.enterprise.auth.platform.auth.model.UserSession;
import com.enterprise.auth.platform.auth.store.SessionStore;
import com.enterprise.auth.platform.auth.service.LoginAttemptService.LoginFailureResult;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.time.TimeSupport;
import com.enterprise.auth.platform.config.PersistenceProperties;
import com.enterprise.auth.platform.config.SecurityProperties;
import com.enterprise.auth.platform.persistence.entity.SysUserEntity;
import com.enterprise.auth.platform.persistence.mapper.SysUserMapper;
import com.enterprise.auth.platform.security.DataScopeService;
import com.enterprise.auth.platform.tenant.TenantContext;
import com.enterprise.auth.platform.user.model.UserAccount;
import com.enterprise.auth.platform.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

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
    private final AuthorizationSessionService authorizationSessionService;
    private final LoginAttemptService loginAttemptService;

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
            AuthorizationSessionService authorizationSessionService,
            LoginAttemptService loginAttemptService
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
        this.authorizationSessionService = authorizationSessionService;
        this.loginAttemptService = loginAttemptService;
    }

    public TokenResponse login(LoginRequest request, HttpServletRequest servletRequest) {
        captchaService.validate(request.captchaId(), request.captchaCode());
        String tenantId = StringUtils.hasText(request.tenantId()) ? request.tenantId() : TenantContext.getTenantId();
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
            throw new BusinessException("INVALID_TOKEN", "无效的刷新令牌");
        }

        if (!"refresh".equals(claims.tokenType())) {
            throw new BusinessException("INVALID_TOKEN", "刷新令牌类型无效");
        }

        UserSession session = sessionStore.findBySessionId(claims.sessionId())
                .orElseThrow(() -> new BusinessException("SESSION_NOT_FOUND", "会话不存在"));
        if (!session.active() || Instant.now().isAfter(session.expiresAt())) {
            throw new BusinessException("SESSION_EXPIRED", "会话已过期");
        }

        UserAccount user = userRepository.findById(claims.userId())
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "用户不存在"));

        validateSessionSubjectBinding(session, claims, user);
        if (!user.enabled()) {
            authorizationSessionService.revoke(session.sessionId());
            throw new BusinessException("USER_DISABLED", "用户已禁用");
        }
        if (user.sessionVersion() != claims.sessionVersion()) {
            authorizationSessionService.revoke(session.sessionId());
            throw new BusinessException("TOKEN_VERSION_MISMATCH", "令牌版本无效");
        }
        if (!user.tenantId().equals(session.tenantId()) || !user.tenantId().equals(claims.tenantId())) {
            authorizationSessionService.revoke(session.sessionId());
            throw new BusinessException("TENANT_MISMATCH", "租户上下文不匹配");
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
                .orElseThrow(() -> new BusinessException("SESSION_NOT_FOUND", "会话不存在"));
        boolean sameOwner = currentUser.id().equals(session.userId());
        boolean canManage = currentUser.permissions().contains("session:write");
        boolean sameTenant = currentUser.tenantId().equals(session.tenantId());
        boolean visibleTarget = dataScopeService.canAccessUser(currentUser.tenantId(), session.userId());
        if (!sameOwner && (!canManage || !sameTenant || !visibleTarget)) {
            throw new BusinessException("ACCESS_DENIED", "无权操作此会话");
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
                TimeSupport.toEpochMilli(Instant.now().plus(securityProperties.accessTokenTtl())),
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

    private void validateSessionSubjectBinding(UserSession session, TokenClaims claims, UserAccount user) {
        boolean mismatch = session.userId() == null
                || claims.userId() == null
                || user.id() == null
                || !session.userId().equals(claims.userId())
                || !session.userId().equals(user.id())
                || !StringUtils.hasText(session.username())
                || !session.username().equals(claims.username())
                || !session.username().equals(user.username());
        if (mismatch) {
            authorizationSessionService.revoke(session.sessionId());
            throw new BusinessException("SESSION_SUBJECT_MISMATCH", "会话主体不匹配");
        }
    }
}
