package com.enterprise.auth.platform.modules.security.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.modules.auth.application.SecuritySupport;
import com.enterprise.auth.platform.common.context.TenantContextSupport;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.log.application.LogPublisher;
import com.enterprise.auth.platform.common.security.EffectiveSecurityPolicy;
import com.enterprise.auth.platform.modules.security.infrastructure.entity.SysPlatformSecurityPolicyEntity;
import com.enterprise.auth.platform.modules.security.infrastructure.entity.SysTenantSecurityPolicyEntity;
import com.enterprise.auth.platform.modules.security.infrastructure.mapper.SysPlatformSecurityPolicyMapper;
import com.enterprise.auth.platform.modules.security.infrastructure.mapper.SysTenantSecurityPolicyMapper;
import com.enterprise.auth.platform.modules.security.interfaces.SecurityPolicyRequest;
import com.enterprise.auth.platform.modules.tenant.application.TenantAccessPolicy;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SecurityPolicyApplicationService {

    private final SysPlatformSecurityPolicyMapper platformPolicyMapper;
    private final SysTenantSecurityPolicyMapper tenantPolicyMapper;
    private final LogPublisher logPublisher;
    private final TenantAccessPolicy tenantAccessPolicy;

    public SecurityPolicyApplicationService(
            SysPlatformSecurityPolicyMapper platformPolicyMapper,
            SysTenantSecurityPolicyMapper tenantPolicyMapper,
            LogPublisher logPublisher,
            TenantAccessPolicy tenantAccessPolicy
    ) {
        this.platformPolicyMapper = platformPolicyMapper;
        this.tenantPolicyMapper = tenantPolicyMapper;
        this.logPublisher = logPublisher;
        this.tenantAccessPolicy = tenantAccessPolicy;
    }

    public EffectiveSecurityPolicy effectivePolicy(String tenantId) {
        SysPlatformSecurityPolicyEntity platform = ensurePlatformPolicy();
        SysTenantSecurityPolicyEntity tenant = StringUtils.hasText(tenantId) ? tenantPolicy(tenantId) : null;
        return merge(platform, tenant);
    }

    public EffectiveSecurityPolicy currentTenantPolicy() {
        return effectivePolicy(resolveTenantId());
    }

    public SecurityPolicyView currentTenantPolicyView() {
        return SecurityPolicyView.from(currentTenantPolicy());
    }

    public SecurityPolicyView platformPolicyView() {
        tenantAccessPolicy.requirePlatformSuperAdmin();
        return SecurityPolicyView.from(merge(ensurePlatformPolicy(), null));
    }

    @Transactional
    public SecurityPolicyView updatePlatformPolicy(SecurityPolicyRequest request) {
        tenantAccessPolicy.requirePlatformSuperAdmin();
        validateRequest(request, false);
        SysPlatformSecurityPolicyEntity entity = ensurePlatformPolicy();
        applyPlatform(entity, request);
        platformPolicyMapper.updateById(entity);
        return platformPolicyView();
    }

    @Transactional
    public SecurityPolicyView updateTenantPolicy(SecurityPolicyRequest request) {
        validateRequest(request, true);
        String tenantId = resolveTenantId();
        SysTenantSecurityPolicyEntity entity = tenantPolicy(tenantId);
        if (entity == null) {
            entity = new SysTenantSecurityPolicyEntity();
            entity.setTenantId(tenantId);
            tenantPolicyMapper.insert(entity);
        }
        applyTenant(entity, request);
        tenantPolicyMapper.updateById(entity);
        return SecurityPolicyView.from(effectivePolicy(tenantId));
    }

    @Transactional
    public void ensureTenantPolicy(String tenantId) {
        if (!StringUtils.hasText(tenantId) || tenantPolicy(tenantId) != null) {
            return;
        }
        SysTenantSecurityPolicyEntity entity = new SysTenantSecurityPolicyEntity();
        entity.setTenantId(tenantId);
        tenantPolicyMapper.insert(entity);
    }

    private SysPlatformSecurityPolicyEntity ensurePlatformPolicy() {
        SysPlatformSecurityPolicyEntity entity = platformPolicyMapper.selectOne(new LambdaQueryWrapper<SysPlatformSecurityPolicyEntity>()
                .eq(SysPlatformSecurityPolicyEntity::getDeleted, 0)
                .orderByAsc(SysPlatformSecurityPolicyEntity::getId)
                .last("limit 1"));
        if (entity != null) {
            return entity;
        }
        SysPlatformSecurityPolicyEntity created = new SysPlatformSecurityPolicyEntity();
        EffectiveSecurityPolicy defaults = EffectiveSecurityPolicy.defaults();
        created.setPasswordMinLength(defaults.passwordMinLength());
        created.setPasswordMaxLength(defaults.passwordMaxLength());
        created.setPasswordRequireLetter(toFlag(defaults.passwordRequireLetter()));
        created.setPasswordRequireNumber(toFlag(defaults.passwordRequireNumber()));
        created.setPasswordRequireSpecial(toFlag(defaults.passwordRequireSpecial()));
        created.setPasswordHistoryCount(defaults.passwordHistoryCount());
        created.setPasswordExpireDays(defaults.passwordExpireDays());
        created.setLoginFailureMaxAttempts(defaults.loginFailureMaxAttempts());
        created.setLoginFailureLockMinutes(defaults.loginFailureLockMinutes());
        created.setLoginFailureWindowMinutes(defaults.loginFailureWindowMinutes());
        created.setCaptchaEnabled(toFlag(defaults.captchaEnabled()));
        platformPolicyMapper.insert(created);
        return created;
    }

    private SysTenantSecurityPolicyEntity tenantPolicy(String tenantId) {
        return tenantPolicyMapper.selectOne(new LambdaQueryWrapper<SysTenantSecurityPolicyEntity>()
                .eq(SysTenantSecurityPolicyEntity::getTenantId, tenantId)
                .eq(SysTenantSecurityPolicyEntity::getDeleted, 0)
                .last("limit 1"));
    }

    private EffectiveSecurityPolicy merge(SysPlatformSecurityPolicyEntity platform, SysTenantSecurityPolicyEntity tenant) {
        EffectiveSecurityPolicy defaults = EffectiveSecurityPolicy.defaults();
        int minLength = value(tenant == null ? null : tenant.getPasswordMinLength(), value(platform.getPasswordMinLength(), defaults.passwordMinLength()));
        int maxLength = value(tenant == null ? null : tenant.getPasswordMaxLength(), value(platform.getPasswordMaxLength(), defaults.passwordMaxLength()));
        return new EffectiveSecurityPolicy(
                minLength,
                Math.max(minLength, maxLength),
                flag(tenant == null ? null : tenant.getPasswordRequireLetter(), flag(platform.getPasswordRequireLetter(), defaults.passwordRequireLetter())),
                flag(tenant == null ? null : tenant.getPasswordRequireNumber(), flag(platform.getPasswordRequireNumber(), defaults.passwordRequireNumber())),
                flag(tenant == null ? null : tenant.getPasswordRequireSpecial(), flag(platform.getPasswordRequireSpecial(), defaults.passwordRequireSpecial())),
                value(tenant == null ? null : tenant.getPasswordHistoryCount(), value(platform.getPasswordHistoryCount(), defaults.passwordHistoryCount())),
                value(tenant == null ? null : tenant.getPasswordExpireDays(), value(platform.getPasswordExpireDays(), defaults.passwordExpireDays())),
                value(tenant == null ? null : tenant.getLoginFailureMaxAttempts(), value(platform.getLoginFailureMaxAttempts(), defaults.loginFailureMaxAttempts())),
                value(tenant == null ? null : tenant.getLoginFailureLockMinutes(), value(platform.getLoginFailureLockMinutes(), defaults.loginFailureLockMinutes())),
                value(tenant == null ? null : tenant.getLoginFailureWindowMinutes(), value(platform.getLoginFailureWindowMinutes(), defaults.loginFailureWindowMinutes())),
                flag(tenant == null ? null : tenant.getCaptchaEnabled(), flag(platform.getCaptchaEnabled(), defaults.captchaEnabled()))
        );
    }

    private void validateRequest(SecurityPolicyRequest request, boolean allowNull) {
        if (request == null) {
            throw new BusinessException("VALIDATION_ERROR", "安全策略不能为空");
        }
        Integer minLength = request.passwordMinLength();
        Integer maxLength = request.passwordMaxLength();
        if (minLength != null && maxLength != null && minLength > maxLength) {
            throw new BusinessException("VALIDATION_ERROR", "密码最小长度不能大于最大长度");
        }
        if (!allowNull && hasNullField(request)) {
            throw new BusinessException("VALIDATION_ERROR", "平台安全策略不允许为空值");
        }
    }

    private boolean hasNullField(SecurityPolicyRequest request) {
        return request.passwordMinLength() == null
                || request.passwordMaxLength() == null
                || request.passwordRequireLetter() == null
                || request.passwordRequireNumber() == null
                || request.passwordRequireSpecial() == null
                || request.passwordHistoryCount() == null
                || request.passwordExpireDays() == null
                || request.loginFailureMaxAttempts() == null
                || request.loginFailureLockMinutes() == null
                || request.loginFailureWindowMinutes() == null
                || request.captchaEnabled() == null;
    }

    private void applyPlatform(SysPlatformSecurityPolicyEntity entity, SecurityPolicyRequest request) {
        entity.setPasswordMinLength(request.passwordMinLength());
        entity.setPasswordMaxLength(request.passwordMaxLength());
        entity.setPasswordRequireLetter(toFlag(request.passwordRequireLetter()));
        entity.setPasswordRequireNumber(toFlag(request.passwordRequireNumber()));
        entity.setPasswordRequireSpecial(toFlag(request.passwordRequireSpecial()));
        entity.setPasswordHistoryCount(request.passwordHistoryCount());
        entity.setPasswordExpireDays(request.passwordExpireDays());
        entity.setLoginFailureMaxAttempts(request.loginFailureMaxAttempts());
        entity.setLoginFailureLockMinutes(request.loginFailureLockMinutes());
        entity.setLoginFailureWindowMinutes(request.loginFailureWindowMinutes());
        entity.setCaptchaEnabled(toFlag(request.captchaEnabled()));
    }

    private void applyTenant(SysTenantSecurityPolicyEntity entity, SecurityPolicyRequest request) {
        entity.setPasswordMinLength(request.passwordMinLength());
        entity.setPasswordMaxLength(request.passwordMaxLength());
        entity.setPasswordRequireLetter(toFlag(request.passwordRequireLetter()));
        entity.setPasswordRequireNumber(toFlag(request.passwordRequireNumber()));
        entity.setPasswordRequireSpecial(toFlag(request.passwordRequireSpecial()));
        entity.setPasswordHistoryCount(request.passwordHistoryCount());
        entity.setPasswordExpireDays(request.passwordExpireDays());
        entity.setLoginFailureMaxAttempts(request.loginFailureMaxAttempts());
        entity.setLoginFailureLockMinutes(request.loginFailureLockMinutes());
        entity.setLoginFailureWindowMinutes(request.loginFailureWindowMinutes());
        entity.setCaptchaEnabled(toFlag(request.captchaEnabled()));
    }

    private String resolveTenantId() {
        return TenantContextSupport.currentTenantIdOrPlatform();
    }

    private int value(Integer candidate, int fallback) {
        return candidate == null ? fallback : candidate;
    }

    private boolean flag(Integer candidate, boolean fallback) {
        return candidate == null ? fallback : candidate == 1;
    }

    private Integer toFlag(Boolean value) {
        return value == null ? null : (value ? 1 : 0);
    }
}