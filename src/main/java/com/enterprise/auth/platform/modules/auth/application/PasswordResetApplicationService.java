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
import com.enterprise.auth.platform.modules.security.application.SecurityPolicyApplicationService;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
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
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PasswordResetApplicationService {

    private static final String GENERIC_REQUEST_MESSAGE = "如果账号存在且已配置邮箱，将会收到密码重置邮件";

    private final SysUserMapper sysUserMapper;
    private final SysPasswordResetTokenMapper tokenMapper;
    private final PasswordHasher passwordHasher;
    private final SecurityPolicyApplicationService securityPolicyApplicationService;
    private final PasswordResetNotificationService notificationService;
    private final AuditService auditService;
    private final SecurityProperties securityProperties;
    private final ClientIpResolver clientIpResolver;
    private final SessionIndexService sessionIndexService;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetApplicationService(
            SysUserMapper sysUserMapper,
            SysPasswordResetTokenMapper tokenMapper,
            PasswordHasher passwordHasher,
            SecurityPolicyApplicationService securityPolicyApplicationService,
            PasswordResetNotificationService notificationService,
            AuditService auditService,
            SecurityProperties securityProperties,
            ClientIpResolver clientIpResolver,
            SessionIndexService sessionIndexService
    ) {
        this.sysUserMapper = sysUserMapper;
        this.tokenMapper = tokenMapper;
        this.passwordHasher = passwordHasher;
        this.securityPolicyApplicationService = securityPolicyApplicationService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.securityProperties = securityProperties;
        this.clientIpResolver = clientIpResolver;
        this.sessionIndexService = sessionIndexService;
    }

    @Transactional
    public PasswordResetRequestResponse request(PasswordResetRequest request, HttpServletRequest servletRequest) {
        String username = normalize(request.username());
        String clientIp = clientIpResolver.resolve(servletRequest);
        String tenantId = resolveTenantId(request.tenantId(), username);
        SysUserEntity user = StringUtils.hasText(tenantId) ? findUser(tenantId, username) : null;
        if (user == null || user.getEnabled() == null || user.getEnabled() != 1 || !StringUtils.hasText(user.getEmail())) {
            auditService.record("PASSWORD_RESET_REQUESTED", username, StringUtils.hasText(tenantId) ? tenantId : "unknown",
                    Map.of("result", "accepted", "clientIp", clientIp, "accountMatched", false));
            return new PasswordResetRequestResponse(GENERIC_REQUEST_MESSAGE);
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
        tokenMapper.insert(entity);

        String resetLink = buildResetLink(rawToken);
        notificationService.sendPasswordResetLink(user.getEmail(), user.getUsername(), resetLink);
        auditService.record("PASSWORD_RESET_REQUESTED", user.getUsername(), user.getTenantId(),
                Map.of("userId", user.getId(), "clientIp", clientIp));
        return new PasswordResetRequestResponse(GENERIC_REQUEST_MESSAGE);
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
        SysPasswordResetTokenEntity token = activeToken(request.token());
        if (token == null) {
            auditService.record("PASSWORD_RESET_FAILED", "anonymous", "unknown", Map.of("reason", "invalid_or_expired"));
            throw new BusinessException("PASSWORD_RESET_TOKEN_INVALID", "重置链接无效或已过期");
        }
        SysUserEntity user = sysUserMapper.selectById(token.getUserId());
        if (user == null || (user.getDeleted() != null && user.getDeleted() == 1) || user.getEnabled() == null || user.getEnabled() != 1) {
            token.setRevokedAt(TimeSupport.utcNowDateTime());
            tokenMapper.updateById(token);
            auditService.record("PASSWORD_RESET_FAILED", token.getUsername(), token.getTenantId(), Map.of("reason", "user_unavailable"));
            throw new BusinessException("PASSWORD_RESET_TOKEN_INVALID", "重置链接无效或已过期");
        }

        validatePassword(user.getTenantId(), request.newPassword());
        if (passwordHasher.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException("PASSWORD_REUSED", "新密码不能与当前密码相同");
        }

        int nextSessionVersion = (user.getSessionVersion() == null ? 1 : user.getSessionVersion()) + 1;
        user.setPasswordHash(passwordHasher.hash(request.newPassword()));
        user.setSessionVersion(nextSessionVersion);
        user.setMustChangePassword(0);
        user.setPasswordUpdatedAt(TimeSupport.utcNowDateTime());
        user.setUpdatedBy("password-reset");
        sysUserMapper.updateById(user);

        token.setUsedAt(TimeSupport.utcNowDateTime());
        token.setUpdatedBy("password-reset");
        tokenMapper.updateById(token);
        revokeActiveTokens(user.getTenantId(), user.getId());
        sessionIndexService.removeUser(user.getId());
        StpUtil.kickout(user.getId());
        auditService.record("PASSWORD_RESET_COMPLETED", user.getUsername(), user.getTenantId(), Map.of("userId", user.getId()));
        return new PasswordResetConfirmResponse("密码已重置，请使用新密码登录");
    }

    private void enforceRequestFrequency(SysUserEntity user, String username, String clientIp) {
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
    }

    private void revokeActiveTokens(String tenantId, Long userId) {
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
    }

    private SysPasswordResetTokenEntity activeToken(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            return null;
        }
        SysPasswordResetTokenEntity token = tokenMapper.selectOne(new LambdaQueryWrapper<SysPasswordResetTokenEntity>()
                .eq(SysPasswordResetTokenEntity::getTokenHash, hashToken(rawToken))
                .eq(SysPasswordResetTokenEntity::getDeleted, 0)
                .last("limit 1"));
        if (token == null || token.getUsedAt() != null || token.getRevokedAt() != null) {
            return null;
        }
        if (token.getExpiresAt() == null || !token.getExpiresAt().isAfter(TimeSupport.utcNowDateTime())) {
            return null;
        }
        return token;
    }

    private void validatePassword(String tenantId, String password) {
        String previousTenantId = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenantId);
            PasswordValidator.validate(password, securityPolicyApplicationService.effectivePolicy(tenantId));
        } finally {
            if (StringUtils.hasText(previousTenantId)) {
                TenantContext.setTenantId(previousTenantId);
            } else {
                TenantContext.clear();
            }
        }
    }

    private SysUserEntity findUser(String tenantId, String username) {
        return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getTenantId, tenantId)
                .eq(SysUserEntity::getUsername, username)
                .eq(SysUserEntity::getDeleted, 0)
                .last("limit 1"));
    }

    private String resolveTenantId(String requestedTenantId, String username) {
        if (StringUtils.hasText(requestedTenantId)) {
            return requestedTenantId.trim();
        }
        var tenantIds = sysUserMapper.selectActiveTenantIdsByUsername(username).stream()
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

    public record PasswordResetRequest(@NotBlank String username, String tenantId) {
    }

    public record PasswordResetRequestResponse(String message) {
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