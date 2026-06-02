package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.auth.infrastructure.SecurityProperties;
import java.util.Arrays;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PasswordResetNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetNotificationService.class);

    private final SecurityProperties securityProperties;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final Environment environment;

    public PasswordResetNotificationService(
            SecurityProperties securityProperties,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            Environment environment
    ) {
        this.securityProperties = securityProperties;
        this.mailSenderProvider = mailSenderProvider;
        this.environment = environment;
    }

    public void sendPasswordResetLink(String email, String username, String resetLink) {
        if (!StringUtils.hasText(email)) {
            return;
        }
        SecurityProperties.Notification notification = securityProperties.resolvedNotification();
        String channel = notification.channel().toLowerCase(Locale.ROOT);
        if (requiresSmtp() && !"smtp".equals(channel)) {
            throw new BusinessException("NOTIFICATION_CHANNEL_INVALID", "当前环境必须使用 SMTP 通知通道");
        }
        if ("smtp".equals(channel)) {
            sendSmtp(email, username, resetLink, notification.mailFrom());
            return;
        }
        log.info("Password reset link generated for user={}, email={}, link={}", username, maskEmail(email), resetLink);
    }

    private void sendSmtp(String email, String username, String resetLink, String from) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new BusinessException("NOTIFICATION_UNAVAILABLE", "SMTP 通知通道不可用");
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(email);
            message.setSubject("密码重置确认");
            message.setText("用户 " + username + " 您好：\n\n请使用以下链接重置密码，链接将在限定时间内失效：\n" + resetLink + "\n\n如果不是您本人操作，请忽略本邮件。");
            mailSender.send(message);
        } catch (Exception ex) {
            throw new BusinessException("NOTIFICATION_SEND_FAILED", "密码重置邮件发送失败");
        }
    }

    private boolean requiresSmtp() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("staging") || profile.equals("prod"));
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}