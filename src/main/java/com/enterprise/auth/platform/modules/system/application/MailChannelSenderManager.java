package com.enterprise.auth.platform.modules.system.application;

import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysMailChannelEntity;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MailChannelSenderManager {

    private static final Logger log = LoggerFactory.getLogger(MailChannelSenderManager.class);

    private final Map<String, JavaMailSender> senderCache = new ConcurrentHashMap<>();
    private final Map<String, String> configFingerprint = new ConcurrentHashMap<>();
    private final MailChannelSecretService secretService;
    private final MailChannelProperties properties;

    public MailChannelSenderManager(MailChannelSecretService secretService, MailChannelProperties properties) {
        this.secretService = secretService;
        this.properties = properties;
    }

    public JavaMailSender getOrCreateSender(SysMailChannelEntity config) {
        if (config == null) {
            throw new IllegalArgumentException("Mail channel config must not be null");
        }
        String cacheKey = config.getTenantId();
        String fingerprint = buildFingerprint(config);

        String cachedFingerprint = configFingerprint.get(cacheKey);
        if (fingerprint.equals(cachedFingerprint)) {
            JavaMailSender cached = senderCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        JavaMailSenderImpl sender = buildSender(config);
        senderCache.put(cacheKey, sender);
        configFingerprint.put(cacheKey, fingerprint);
        log.info("Mail channel sender created for tenant={}, host={}:{}", config.getTenantId(), config.getMailHost(), config.getMailPort());
        return sender;
    }

    public void evict(String tenantId) {
        senderCache.remove(tenantId);
        configFingerprint.remove(tenantId);
        log.info("Mail channel sender evicted for tenant={}", tenantId);
    }

    private JavaMailSenderImpl buildSender(SysMailChannelEntity config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        String password = secretService.reveal(config.getMailPassword());
        sender.setHost(config.getMailHost());
        sender.setPort(config.getMailPort() != null ? config.getMailPort() : 587);
        sender.setUsername(config.getMailUsername());
        sender.setPassword(password);
        sender.setProtocol(StringUtils.hasText(config.getMailProtocol()) ? config.getMailProtocol() : "smtp");
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(StringUtils.hasText(config.getMailUsername()) && StringUtils.hasText(password)));
        props.put("mail.smtp.starttls.enable", String.valueOf(config.getUseStarttls() != null && config.getUseStarttls() == 1));
        props.put("mail.smtp.starttls.required", String.valueOf(config.getUseStarttls() != null && config.getUseStarttls() == 1));
        props.put("mail.smtp.ssl.enable", String.valueOf(config.getUseSsl() != null && config.getUseSsl() == 1));
        props.put("mail.smtp.ssl.protocols", "TLSv1.3 TLSv1.2");
        if (StringUtils.hasText(config.getMailHost())) {
            props.put("mail.smtp.ssl.trust", config.getMailHost());
        }
        props.put("mail.smtp.connectiontimeout", String.valueOf(properties.resolvedConnectionTimeoutMillis()));
        props.put("mail.smtp.timeout", String.valueOf(properties.resolvedTimeoutMillis()));
        props.put("mail.smtp.writetimeout", String.valueOf(properties.resolvedWriteTimeoutMillis()));
        props.put("mail.debug", String.valueOf(properties.resolvedDebug()));

        return sender;
    }

    private String buildFingerprint(SysMailChannelEntity config) {
        return config.getId() + "|" + config.getMailHost() + ":" + config.getMailPort() + "|" +
               config.getMailUsername() + "|" + config.getMailPassword() + "|" +
               config.getMailProtocol() + "|" + config.getUseSsl() + "|" + config.getUseStarttls();
    }
}