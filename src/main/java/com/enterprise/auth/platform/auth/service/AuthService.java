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
            DataScopeService dataScopeService
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
    }

    public TokenResponse login(LoginRequest request, HttpServletRequest servletRequest) {
        captchaService.validate(request.captchaId(), request.captchaCode());
        String tenantId = StringUtils.hasText(request.tenantId()) ? request.tenantId() : TenantContext.getTenantId();
        String clientIp = clientIp(servletRequest);
        UserAccount user = userRepository.findByUsername(tenantId, request.username())
                .orElseThrow(() -> {
                    auditService.record("LOGIN_FAILED", request.username(), tenantId, Map.of("reason", "user_not_found", "clientIp", clientIp));
                    return new BusinessException("用户名或密码错误");
                });
        if (!passwordEncoder.matches(request.password(), user.password())) {
            auditService.record("LOGIN_FAILED", request.username(), tenantId, Map.of("reason", "bad_credentials", "clientIp", clientIp));
            throw new BusinessException("用户名或密码错误");
        }
        if (!user.enabled()) {
            auditService.record("LOGIN_FAILED", user.username(), tenantId, Map.of("reason", "disabled", "clientIp", clientIp));
            throw new BusinessException("用户已禁用");
        }

        String sessionId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        // 自定义登录链路继续通过会话存储处理刷新、下线和失效控制。
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
        auditService.record("LOGIN_SUCCESS", user.username(), user.tenantId(), Map.of("sessionId", sessionId, "clientIp", clientIp));
        return issueTokens(user, sessionId);
    }

    public TokenResponse refresh(String refreshToken) {
        TokenClaims claims = jwtService.decode(refreshToken);
        if (!"refresh".equals(claims.tokenType())) {
            throw new BusinessException("刷新令牌类型错误");
        }
        UserSession session = sessionStore.findBySessionId(claims.sessionId())
                .orElseThrow(() -> new BusinessException("会话不存在"));
        if (!session.active() || Instant.now().isAfter(session.expiresAt())) {
            throw new BusinessException("会话已过期");
        }
        UserAccount user = userRepository.findById(claims.userId())
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if (user.sessionVersion() != claims.sessionVersion()) {
            throw new BusinessException("令牌版本已失效");
        }
        sessionStore.touch(session.sessionId());
        return issueTokens(user, session.sessionId());
    }

    public void logout(String sessionId, String username, String tenantId) {
        sessionStore.deactivate(sessionId);
        auditService.record("LOGOUT", username, tenantId, Map.of("sessionId", sessionId));
    }

    public List<UserSessionResponse> sessions(UserAccount currentUser) {
        return sessionStore.findByUserId(currentUser.id()).stream()
                .map(session -> new UserSessionResponse(
                        session.sessionId(),
                        session.username(),
                        session.tenantId(),
                        session.clientIp(),
                        session.device(),
                        session.issuedAt(),
                        session.expiresAt(),
                        session.lastAccessAt(),
                        session.active()
                ))
                .toList();
    }

    public void forceOffline(UserAccount currentUser, String sessionId) {
        UserSession session = sessionStore.findBySessionId(sessionId)
                .orElseThrow(() -> new BusinessException("会话不存在"));
        boolean sameOwner = currentUser.id().equals(session.userId());
        boolean canManage = currentUser.permissions().contains("session:write");
        boolean sameTenant = currentUser.tenantId().equals(session.tenantId());
        boolean visibleTarget = dataScopeService.canAccessUser(currentUser.tenantId(), session.userId());
        if (!sameOwner && (!canManage || !sameTenant || !visibleTarget)) {
            throw new BusinessException("无权操作该会话");
        }
        sessionStore.deactivate(sessionId);
        auditService.record("SESSION_FORCED_OFFLINE", currentUser.username(), currentUser.tenantId(), Map.of("sessionId", sessionId));
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
}
