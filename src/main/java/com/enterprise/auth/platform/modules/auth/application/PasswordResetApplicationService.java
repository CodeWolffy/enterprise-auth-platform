package com.enterprise.auth.platform.modules.auth.application;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.PasswordValidator;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.web.ClientIpResolver;
import com.enterprise.auth.platform.modules.audit.application.AuditService;
import com.enterprise.auth.platform.modules.auth.domain.PasswordHasher;
import com.enterprise.auth.platform.modules.auth.infrastructure.SecurityProperties;
import com.enterprise.auth.platform.modules.auth.infrastructure.entity.SysPasswordResetTokenEntity;
import com.enterprise.auth.platform.modules.auth.infrastructure.mapper.SysPasswordResetTokenMapper;
import com.enterprise.auth.platform.modules.notification.application.NotificationScenarioPublisher;
import com.enterprise.auth.platform.modules.security.application.SecurityPolicyApplicationService;
import com.enterprise.auth.platform.modules.user.application.UserAuthenticationFacade;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PasswordResetApplicationService {

    private static final String GENERIC_REQUEST_MESSAGE = "如果账号存在且邮箱匹配，将会收到密码重置邮件";

    private final UserAuthenticationFacade userAuthenticationFacade;
    private final SysPasswordResetTokenMapper tokenMapper;
    private final PasswordHasher passwordHasher;
    private final SecurityPolicyApplicationService securityPolicyApplicationService;
    private final PasswordResetNotificationService notificationService;
    private final AuditService auditService;
    private final SecurityProperties securityProperties;
    private final ClientIpResolver clientIpResolver;
    private final SessionIndexService sessionIndexService;
    private final NotificationScenarioPublisher notificationScenarioPublisher;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetApplicationService(
            UserAuthenticationFacade userAuthenticationFacade,
            SysPasswordResetTokenMapper tokenMapper,
            PasswordHasher passwordHasher,
            SecurityPolicyApplicationService securityPolicyApplicationService,
            PasswordResetNotificationService notificationService,
            AuditService auditService,
            SecurityProperties securityProperties,
            ClientIpResolver clientIpResolver,
            SessionIndexService sessionIndexService,
            NotificationScenarioPublisher notificationScenarioPublisher
    ) {
        this.userAuthenticationFacade = userAuthenticationFacade;
        this.tokenMapper = tokenMapper;
        this.passwordHasher = passwordHasher;
        this.securityPolicyApplicationService = securityPolicyApplicationService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.securityProperties = securityProperties;
        this.clientIpResolver = clientIpResolver;
        this.sessionIndexService = sessionIndexService;
        this.notificationScenarioPublisher = notificationScenarioPublisher;
    }

    @Transactional
    public PasswordResetRequestResponse request(PasswordResetRequest request, HttpServletRequest servletRequest) {
        String username = normalize(request.username());
        String email = normalize(request.email());
        String clientIp = clientIpResolver.resolve(servletRequest);

        // 快速存在性检查：失败分支只写审计，不向客户端暴露账号状态
        if (!StringUtils.hasText(request.tenantId())) {
            List<String> tenantIds = userAuthenticationFacade.activeTenantIdsByUsername(username);
            if (tenantIds.isEmpty()) {
                auditService.record("PASSWORD_RESET_REQUESTED", username, "unknown",
                        Map.of("result", "not_found", "clientIp", clientIp));
                return acceptedPasswordResetRequest();
            }
        }

        String tenantId = resolveTenantId(request.tenantId(), username);
        SysUserEntity user = StringUtils.hasText(tenantId) ? findUser(tenantId, username) : null;

        if (user == null) {
            // 显式指定了租户但用户不存在，或未指定租户但用户跨多租户存在
            if (StringUtils.hasText(request.tenantId())) {
                auditService.record("PASSWORD_RESET_REQUESTED", username, request.tenantId(),
                        Map.of("result", "not_found", "clientIp", clientIp));
                return acceptedPasswordResetRequest();
            }
            auditService.record("PASSWORD_RESET_REQUESTED", username, "unknown",
                    Map.of("result", "accepted", "clientIp", clientIp, "accountMatched", false));
            return acceptedPasswordResetRequest();
        }

        if (user.getEnabled() == null || user.getEnabled() != 1) {
            auditService.record("PASSWORD_RESET_REQUESTED", username, tenantId,
                    Map.of("result", "disabled", "clientIp", clientIp));
            return acceptedPasswordResetRequest();
        }

        if (!StringUtils.hasText(user.getEmail())) {
            auditService.record("PASSWORD_RESET_REQUESTED", username, tenantId,
                    Map.of("result", "no_email", "clientIp", clientIp));
            return acceptedPasswordResetRequest();
        }

        if (!emailMatches(user.getEmail(), email)) {
            auditService.record("PASSWORD_RESET_REQUESTED", username, tenantId,
                    Map.of("result", "email_mismatch", "clientIp", clientIp));
            return acceptedPasswordResetRequest();
        }

        enforceRequestFrequency(user, username, clientIp);
        revokeActiveTokens(user.getTenantId(), user.getId());

        String rawToken = newToken();
        LocalDateTime now = TimeSupport.utcNowDateTime();
        SecurityProperties.PasswordReset config = securityProperties.resolvedPasswordReset();
        SysPasswordResetTokenEntity entity = new SysPasswordResetTokenEntity();
        entity.setTenantId(user.getTenantId());
        entity.setUserId(user.getId());
        entity.setUsername(user.getUsername());
        entity.setTokenHash(hashToken(rawToken));
        entity.setExpiresAt(now.plusSeconds(config.tokenTtl().toSeconds()));
        entity.setRequestIp(clientIp);
        entity.setCreatedBy("password-reset");
        entity.setUpdatedBy("password-reset");
        withTenant(user.getTenantId(), () -> {
            tokenMapper.insert(entity);
            return null;
        });

        String resetLink = buildResetLink(rawToken);
        notificationService.sendPasswordResetLink(user.getTenantId(), user.getEmail(), user.getUsername(), resetLink);
        notificationScenarioPublisher.passwordResetRequested(user.getTenantId(), user.getId(), user.getUsername(), clientIp);
        auditService.record("PASSWORD_RESET_REQUESTED", user.getUsername(), user.getTenantId(),
                Map.of("userId", user.getId(), "clientIp", clientIp));
        return new PasswordResetRequestResponse(GENERIC_REQUEST_MESSAGE, "EMAIL_SENT");
    }

    public PasswordResetVerifyResponse verify(PasswordResetVerifyRequest request) {
        SysPasswordResetTokenEntity token = activeToken(request.token());
        if (token == null) {
            auditService.record("PASSWORD_RESET_FAILED", "anonymous", "unknown", Map.of("reason", "invalid_or_expired"));
            return new PasswordResetVerifyResponse(false, null);
        }
        return new PasswordResetVerifyResponse(true, token.getUsername());
    }

    @Transactional
    public PasswordResetConfirmResponse confirm(PasswordResetConfirmRequest request) {
        SysPasswordResetTokenEntity token = activeTokenForUpdate(request.token());
        if (token == null) {
            auditService.record("PASSWORD_RESET_FAILED", "anonymous", "unknown", Map.of("reason", "invalid_or_expired"));
            throw new BusinessException("PASSWORD_RESET_TOKEN_INVALID", "重置链接无效、已使用或已过期");
        }
        SysUserEntity user = userAuthenticationFacade.findActiveEntityById(token.getTenantId(), token.getUserId()).orElse(null);
        if (user == null || (user.getDeleted() != null && user.getDeleted() == 1) || user.getEnabled() == null || user.getEnabled() != 1) {
            tokenMapper.revokeIfActive(token.getId(), TimeSupport.utcNowDateTime(), "password-reset");
            auditService.record("PASSWORD_RESET_FAILED", token.getUsername(), token.getTenantId(), Map.of("reason", "user_unavailable"));
            throw new BusinessException("PASSWORD_RESET_TOKEN_INVALID", "重置链接无效、已使用或已过期");
        }

        validatePassword(user.getTenantId(), request.newPassword());
        if (passwordHasher.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException("PASSWORD_REUSED", "新密码不能与当前密码相同");
        }

        LocalDateTime now = TimeSupport.utcNowDateTime();
        int nextSessionVersion = (user.getSessionVersion() == null ? 1 : user.getSessionVersion()) + 1;
        user.setPasswordHash(passwordHasher.hash(request.newPassword()));
        user.setSessionVersion(nextSessionVersion);
        user.setMustChangePassword(0);
        user.setPasswordUpdatedAt(now);
        user.setUpdatedBy("password-reset");
        userAuthenticationFacade.update(user);

        token.setUsedAt(now);
        token.setUpdatedBy("password-reset");
        withTenant(token.getTenantId(), () -> {
            tokenMapper.updateById(token);
            return null;
        });
        revokeActiveTokens(user.getTenantId(), user.getId());
        afterCommit(() -> {
            sessionIndexService.removeUser(user.getId());
            StpUtil.kickout(user.getId());
        });
        auditService.record("PASSWORD_RESET_COMPLETED", user.getUsername(), user.getTenantId(), Map.of("userId", user.getId()));
        notificationScenarioPublisher.passwordResetCompleted(user.getTenantId(), user.getId(), user.getUsername());
        return new PasswordResetConfirmResponse("密码已重置，请使用新密码登录");
    }

    private PasswordResetRequestResponse acceptedPasswordResetRequest() {
        return new PasswordResetRequestResponse(GENERIC_REQUEST_MESSAGE, "EMAIL_SENT");
    }

    private void afterCommit(Runnable action) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    }
            );
            return;
        }
        action.run();
    }

    private void enforceRequestFrequency(SysUserEntity user, String username, String clientIp) {
        withTenant(user.getTenantId(), () -> {
            SecurityProperties.PasswordReset config = securityProperties.resolvedPasswordReset();
            LocalDateTime now = TimeSupport.utcNowDateTime();
            long usernameCount = tokenMapper.selectCount(new LambdaQueryWrapper<SysPasswordResetTokenEntity>()
                    .eq(SysPasswordResetTokenEntity::getTenantId, user.getTenantId())
                    .eq(SysPasswordResetTokenEntity::getUsername, username)
                    .ge(SysPasswordResetTokenEntity::getCreatedAt, now.minusMinutes(config.usernameWindowMinutes())));
            if (usernameCount >= config.usernameMaxRequests()) {
                throw new BusinessException("RATE_LIMITED", "请求过于频繁，请稍后再试");
            }
            long ipCount = tokenMapper.selectCount(new LambdaQueryWrapper<SysPasswordResetTokenEntity>()
                    .eq(SysPasswordResetTokenEntity::getRequestIp, clientIp)
                    .ge(SysPasswordResetTokenEntity::getCreatedAt, now.minusMinutes(config.ipWindowMinutes())));
            if (ipCount >= config.ipMaxRequests()) {
                throw new BusinessException("RATE_LIMITED", "请求过于频繁，请稍后再试");
            }
            return null;
        });
    }

    private void revokeActiveTokens(String tenantId, Long userId) {
        withTenant(tenantId, () -> {
            LocalDateTime now = TimeSupport.utcNowDateTime();
            tokenMapper.selectList(new LambdaQueryWrapper<SysPasswordResetTokenEntity>()
                            .eq(SysPasswordResetTokenEntity::getTenantId, tenantId)
                            .eq(SysPasswordResetTokenEntity::getUserId, userId)
                            .isNull(SysPasswordResetTokenEntity::getUsedAt)
                            .isNull(SysPasswordResetTokenEntity::getRevokedAt))
                    .forEach(token -> {
                        token.setRevokedAt(now);
                        token.setUpdatedBy("password-reset");
                        tokenMapper.updateById(token);
                    });
            return null;
        });
    }

    private SysPasswordResetTokenEntity activeToken(String rawToken) {
        return loadActiveToken(rawToken, false);
    }

    private SysPasswordResetTokenEntity activeTokenForUpdate(String rawToken) {
        return loadActiveToken(rawToken, true);
    }

    private SysPasswordResetTokenEntity loadActiveToken(String rawToken, boolean forUpdate) {
        if (!StringUtils.hasText(rawToken)) {
            return null;
        }
        SysPasswordResetTokenEntity token = forUpdate
                ? tokenMapper.selectByTokenHashForUpdate(hashToken(rawToken))
                : tokenMapper.selectByTokenHash(hashToken(rawToken));
        if (token == null || token.getUsedAt() != null || token.getRevokedAt() != null) {
            return null;
        }
        if (token.getExpiresAt() == null || !token.getExpiresAt().isAfter(TimeSupport.utcNowDateTime())) {
            return null;
        }
        return token;
    }

    private void validatePassword(String tenantId, String password) {
        withTenant(tenantId, () -> {
            PasswordValidator.validate(password, securityPolicyApplicationService.effectivePolicy(tenantId));
            return null;
        });
    }

    private <T> T withTenant(String tenantId, Supplier<T> action) {
        String previousTenantId = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenantId);
            return action.get();
        } finally {
            if (StringUtils.hasText(previousTenantId)) {
                TenantContext.setTenantId(previousTenantId);
            } else {
                TenantContext.clear();
            }
        }
    }

    private SysUserEntity findUser(String tenantId, String username) {
        return userAuthenticationFacade.findActiveEntity(tenantId, username).orElse(null);
    }

    private boolean emailMatches(String storedEmail, String submittedEmail) {
        return StringUtils.hasText(storedEmail)
                && StringUtils.hasText(submittedEmail)
                && storedEmail.trim().equalsIgnoreCase(submittedEmail.trim());
    }

    private String resolveTenantId(String requestedTenantId, String username) {
        if (StringUtils.hasText(requestedTenantId)) {
            return requestedTenantId.trim();
        }
        var tenantIds = userAuthenticationFacade.activeTenantIdsByUsername(username).stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        return tenantIds.size() == 1 ? tenantIds.get(0) : null;
    }

    private String buildResetLink(String rawToken) {
        String base = securityProperties.resolvedPasswordReset().frontendUrl();
        String separator = base.contains("?") ? "&" : "?";
        return base + separator + "token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public record PasswordResetRequest(
            @NotBlank @Size(min = 5, max = 16) String username,
            @NotBlank @Email @Size(max = 128) String email,
            String tenantId
    ) {}

    public record PasswordResetRequestResponse(String message, String result) {
    }

    public record PasswordResetVerifyRequest(@NotBlank String token) {
    }

    public record PasswordResetVerifyResponse(boolean valid, String username) {
    }

    public record PasswordResetConfirmRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 64) String newPassword
    ) {
    }

    public record PasswordResetConfirmResponse(String message) {
    }
}