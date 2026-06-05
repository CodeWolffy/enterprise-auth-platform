package com.enterprise.auth.platform.modules.system.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysMailChannelEntity;
import com.enterprise.auth.platform.modules.system.infrastructure.mapper.SysMailChannelMapper;
import com.enterprise.auth.platform.modules.system.interfaces.MailChannelRequest;
import com.enterprise.auth.platform.modules.system.interfaces.MailChannelResponse;
import com.enterprise.auth.platform.modules.tenant.infrastructure.TenantProperties;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MailChannelApplicationService {

    private static final Logger log = LoggerFactory.getLogger(MailChannelApplicationService.class);
    private static final String DEFAULT_PLATFORM_TENANT_ID = "platform";
    private static final String DEFAULT_PROTOCOL = "smtp";

    private final SysMailChannelMapper mapper;
    private final MailChannelSenderManager senderManager;
    private final MailChannelSecretService secretService;
    private final TransactionalMailSupport transactionalMailSupport;
    private final TenantProperties tenantProperties;

    public MailChannelApplicationService(
            SysMailChannelMapper mapper,
            MailChannelSenderManager senderManager,
            MailChannelSecretService secretService,
            TransactionalMailSupport transactionalMailSupport,
            TenantProperties tenantProperties
    ) {
        this.mapper = mapper;
        this.senderManager = senderManager;
        this.secretService = secretService;
        this.transactionalMailSupport = transactionalMailSupport;
        this.tenantProperties = tenantProperties;
    }

    public Optional<MailChannelResponse> getVisibleChannel(String tenantId) {
        String normalizedTenantId = normalizeTenantId(tenantId);
        Optional<SysMailChannelEntity> own = findByTenant(normalizedTenantId);
        if (own.isPresent()) {
            return own.map(entity -> toResponse(entity, false, normalizedTenantId));
        }
        if (!platformTenantId().equals(normalizedTenantId)) {
            return findEnabledByTenant(platformTenantId()).map(entity -> toResponse(entity, true, normalizedTenantId));
        }
        return Optional.empty();
    }

    public Optional<SysMailChannelEntity> getEnabledChannel(String tenantId) {
        String normalizedTenantId = normalizeTenantId(tenantId);
        Optional<SysMailChannelEntity> own = findByTenant(normalizedTenantId);
        if (own.isPresent()) {
            return isUsable(own.get()) ? own : Optional.empty();
        }
        if (!platformTenantId().equals(normalizedTenantId)) {
            return findEnabledByTenant(platformTenantId());
        }
        return Optional.empty();
    }

    public SysMailChannelEntity getEnabledChannelOrThrow(String tenantId) {
        String normalizedTenantId = normalizeTenantId(tenantId);
        return getEnabledChannel(normalizedTenantId)
                .orElseThrow(() -> new BusinessException("MAIL_CHANNEL_NOT_CONFIGURED",
                        "租户 " + normalizedTenantId + " 未配置可用邮件渠道，且平台租户也没有可用默认渠道"));
    }

    @Transactional
    public MailChannelResponse saveOrUpdate(MailChannelRequest request) {
        String tenantId = currentTenantId();
        SysMailChannelEntity existing = findByTenant(tenantId).orElse(null);
        boolean creating = existing == null;
        boolean passwordProvided = StringUtils.hasText(request.mailPassword());
        boolean passwordRequired = creating || !StringUtils.hasText(existing.getMailPassword());
        validateRequest(request, passwordRequired, passwordProvided);

        SysMailChannelEntity entity = creating ? new SysMailChannelEntity() : existing;
        if (creating) {
            entity.setTenantId(tenantId);
        }

        entity.setProvider(normalizeProvider(request.provider()));
        entity.setMailHost(request.mailHost().trim());
        entity.setMailPort(request.mailPort());
        entity.setMailUsername(request.mailUsername().trim());
        if (passwordProvided) {
            entity.setMailPassword(secretService.protect(request.mailPassword()));
        }
        entity.setMailFrom(request.mailFrom().trim());
        entity.setMailProtocol(normalizeProtocol(request.mailProtocol()));
        entity.setUseSsl(request.useSsl() ? 1 : 0);
        entity.setUseStarttls(request.useStartTls() ? 1 : 0);
        entity.setEnabled(request.enabled() ? 1 : 0);
        entity.setDeleted(0);

        if (creating) {
            mapper.insert(entity);
            log.info("Mail channel created for tenant={}, provider={}, host={}:{}", tenantId, entity.getProvider(), entity.getMailHost(), entity.getMailPort());
        } else {
            mapper.updateById(entity);
            log.info("Mail channel updated for tenant={}, provider={}, host={}:{}", tenantId, entity.getProvider(), entity.getMailHost(), entity.getMailPort());
        }

        senderManager.evict(tenantId);
        return toResponse(entity, false, tenantId);
    }

    @Transactional
    public void deleteChannel() {
        String tenantId = currentTenantId();
        SysMailChannelEntity entity = findByTenant(tenantId)
                .orElseThrow(() -> new BusinessException("MAIL_CHANNEL_NOT_FOUND", "未找到当前租户的邮件渠道配置"));
        withTenant(tenantId, () -> mapper.update(null, new LambdaUpdateWrapper<SysMailChannelEntity>()
                .eq(SysMailChannelEntity::getTenantId, entity.getTenantId())
                .eq(SysMailChannelEntity::getDeleted, 0)
                .set(SysMailChannelEntity::getEnabled, 0)
                .set(SysMailChannelEntity::getDeleted, 1)));
        senderManager.evict(tenantId);
        log.info("Mail channel deleted for tenant={}", tenantId);
    }

    public boolean sendTestMail(String tenantId, String toEmail) {
        String normalizedTenantId = normalizeTenantId(tenantId);
        validateEmail(toEmail, "测试邮件接收地址格式不正确");
        SysMailChannelEntity config = getEnabledChannelOrThrow(normalizedTenantId);
        JavaMailSender sender = senderManager.getOrCreateSender(config);
        try {
            transactionalMailSupport.send(
                    sender,
                    config.getMailFrom(),
                    toEmail.trim(),
                    "邮件渠道测试",
                    transactionalMailSupport.testMailContent(
                            normalizedTenantId,
                            config.getTenantId(),
                            config.getMailHost(),
                            config.getMailPort(),
                            config.getMailFrom()
                    )
            );
            log.info("Test mail sent to {} for tenant={}, channelTenant={}", maskEmail(toEmail), normalizedTenantId, config.getTenantId());
            return true;
        } catch (Exception ex) {
            log.error("Test mail failed for tenant={}, channelTenant={}, to={}", normalizedTenantId, config.getTenantId(), maskEmail(toEmail), ex);
            throw new BusinessException("MAIL_TEST_FAILED", "测试邮件发送失败，请检查邮件渠道配置或稍后重试");
        }
    }

    public MailChannelPreset resolvePreset(String provider) {
        if (!StringUtils.hasText(provider)) {
            return MailChannelPreset.CUSTOM;
        }
        try {
            return MailChannelPreset.valueOf(provider.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return MailChannelPreset.CUSTOM;
        }
    }

    private Optional<SysMailChannelEntity> findByTenant(String tenantId) {
        return Optional.ofNullable(withTenant(tenantId, () -> mapper.selectOne(new LambdaQueryWrapper<SysMailChannelEntity>()
                .eq(SysMailChannelEntity::getTenantId, tenantId)
                .eq(SysMailChannelEntity::getDeleted, 0)
                .last("limit 1"))));
    }

    private Optional<SysMailChannelEntity> findEnabledByTenant(String tenantId) {
        return Optional.ofNullable(withTenant(tenantId, () -> mapper.selectOne(new LambdaQueryWrapper<SysMailChannelEntity>()
                .eq(SysMailChannelEntity::getTenantId, tenantId)
                .eq(SysMailChannelEntity::getDeleted, 0)
                .eq(SysMailChannelEntity::getEnabled, 1)
                .isNotNull(SysMailChannelEntity::getMailPassword)
                .ne(SysMailChannelEntity::getMailPassword, "")
                .last("limit 1"))))
                .filter(this::isUsable);
    }

    private MailChannelResponse toResponse(SysMailChannelEntity entity, boolean inherited, String visibleTenantId) {
        return new MailChannelResponse(
                entity.getId(),
                inherited ? visibleTenantId : entity.getTenantId(),
                entity.getProvider(),
                entity.getMailHost(),
                entity.getMailPort(),
                entity.getMailUsername(),
                entity.getMailFrom(),
                entity.getMailProtocol(),
                entity.getUseSsl() != null && entity.getUseSsl() == 1,
                entity.getUseStarttls() != null && entity.getUseStarttls() == 1,
                isEnabled(entity),
                StringUtils.hasText(entity.getMailPassword()),
                inherited,
                entity.getTenantId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private void validateRequest(MailChannelRequest request, boolean passwordRequired, boolean passwordProvided) {
        if (passwordRequired && !passwordProvided) {
            throw new BusinessException("MAIL_PASSWORD_REQUIRED", "首次配置 SMTP 时必须填写授权码或密码");
        }
        if (request.useSsl() && request.useStartTls()) {
            throw new BusinessException("MAIL_SECURITY_MODE_CONFLICT", "SSL 与 STARTTLS 不能同时启用");
        }
        normalizeProvider(request.provider());
        normalizeProtocol(request.mailProtocol());
        validateEmail(request.mailFrom(), "发件人邮箱格式不正确");
    }

    private String normalizeProvider(String provider) {
        String normalized = StringUtils.hasText(provider) ? provider.trim().toUpperCase(Locale.ROOT) : MailChannelPreset.CUSTOM.name();
        try {
            return MailChannelPreset.valueOf(normalized).name();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("MAIL_PROVIDER_UNSUPPORTED", "不支持的邮件渠道类型：" + normalized);
        }
    }

    private String normalizeProtocol(String protocol) {
        String normalized = StringUtils.hasText(protocol) ? protocol.trim().toLowerCase(Locale.ROOT) : DEFAULT_PROTOCOL;
        if (!DEFAULT_PROTOCOL.equals(normalized)) {
            throw new BusinessException("MAIL_PROTOCOL_UNSUPPORTED", "当前仅支持 smtp 协议");
        }
        return normalized;
    }

    private boolean isEnabled(SysMailChannelEntity entity) {
        return entity.getEnabled() != null && entity.getEnabled() == 1;
    }

    private boolean isUsable(SysMailChannelEntity entity) {
        return isEnabled(entity)
                && StringUtils.hasText(entity.getMailHost())
                && entity.getMailPort() != null
                && StringUtils.hasText(entity.getMailUsername())
                && StringUtils.hasText(entity.getMailPassword())
                && StringUtils.hasText(entity.getMailFrom());
    }

    private String currentTenantId() {
        return normalizeTenantId(TenantContext.getTenantId());
    }

    private String normalizeTenantId(String tenantId) {
        return StringUtils.hasText(tenantId) ? tenantId.trim() : platformTenantId();
    }

    private String platformTenantId() {
        return StringUtils.hasText(tenantProperties.platformTenantId())
                ? tenantProperties.platformTenantId().trim()
                : DEFAULT_PLATFORM_TENANT_ID;
    }

    private <T> T withTenant(String tenantId, Supplier<T> action) {
        String previousTenantId = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(normalizeTenantId(tenantId));
            return action.get();
        } finally {
            if (StringUtils.hasText(previousTenantId)) {
                TenantContext.setTenantId(previousTenantId);
            } else {
                TenantContext.clear();
            }
        }
    }

    private void validateEmail(String email, String message) {
        try {
            InternetAddress address = new InternetAddress(email, true);
            address.validate();
        } catch (AddressException ex) {
            throw new BusinessException("MAIL_EMAIL_INVALID", message);
        }
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}