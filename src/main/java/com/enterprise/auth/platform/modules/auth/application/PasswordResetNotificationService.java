package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.auth.infrastructure.SecurityProperties;
import com.enterprise.auth.platform.modules.system.application.MailChannelApplicationService;
import com.enterprise.auth.platform.modules.system.application.MailChannelSenderManager;
import com.enterprise.auth.platform.modules.system.application.TransactionalMailSupport;
import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysMailChannelEntity;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PasswordResetNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetNotificationService.class);

    private final SecurityProperties securityProperties;
    private final MailChannelApplicationService mailChannelService;
    private final MailChannelSenderManager senderManager;
    private final TransactionalMailSupport transactionalMailSupport;
    private final Environment environment;

    public PasswordResetNotificationService(
            SecurityProperties securityProperties,
            MailChannelApplicationService mailChannelService,
            MailChannelSenderManager senderManager,
            TransactionalMailSupport transactionalMailSupport,
            Environment environment
    ) {
        this.securityProperties = securityProperties;
        this.mailChannelService = mailChannelService;
        this.senderManager = senderManager;
        this.transactionalMailSupport = transactionalMailSupport;
        this.environment = environment;
    }

    public void sendPasswordResetLink(String tenantId, String email, String username, String resetLink) {
        if (!StringUtils.hasText(email)) {
            return;
        }
        String normalizedTenantId = normalizeTenantId(tenantId);
        Optional<SysMailChannelEntity> dbConfig = mailChannelService.getEnabledChannel(normalizedTenantId);
        if (dbConfig.isPresent()) {
            sendViaDbChannel(dbConfig.get(), email, username, resetLink);
            return;
        }

        SecurityProperties.Notification notification = securityProperties.resolvedNotification();
        String channel = notification.channel().toLowerCase(Locale.ROOT);
        if (!"smtp".equals(channel)) {
            logResetLink(username, email, resetLink);
            return;
        }
        if (requiresSmtp()) {
            throw new BusinessException("MAIL_CHANNEL_NOT_CONFIGURED",
                    "当前环境要求启用 SMTP，但租户 " + normalizedTenantId + " 尚未配置可用邮件渠道，请在系统设置中配置");
        }
        log.warn("SMTP notification requested but no enabled mail channel found for tenant={}, falling back to log", normalizedTenantId);
        logResetLink(username, email, resetLink);
    }

    private void sendViaDbChannel(SysMailChannelEntity config, String email, String username, String resetLink) {
        JavaMailSender mailSender = senderManager.getOrCreateSender(config);
        long ttlMinutes = Math.max(1, securityProperties.resolvedPasswordReset().tokenTtl().toMinutes());
        try {
            transactionalMailSupport.send(
                    mailSender,
                    config.getMailFrom(),
                    email,
                    "密码重置确认",
                    transactionalMailSupport.passwordResetContent(username, resetLink, ttlMinutes)
            );
            log.info("Password reset email sent to {} for user={}, channelTenant={}", maskEmail(email), username, config.getTenantId());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Password reset email send failed for user={}, channelTenant={}", username, config.getTenantId(), ex);
            throw new BusinessException("NOTIFICATION_SEND_FAILED", "密码重置邮件发送失败");
        }
    }

    private boolean requiresSmtp() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("staging") || profile.equals("prod"));
    }

    private String normalizeTenantId(String tenantId) {
        return StringUtils.hasText(tenantId) ? tenantId.trim() : "platform";
    }

    private void logResetLink(String username, String email, String resetLink) {
        log.info("Password reset link generated for user={}, email={}, link={}", username, maskEmail(email), resetLink);
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}