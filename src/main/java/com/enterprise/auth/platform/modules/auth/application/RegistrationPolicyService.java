package com.enterprise.auth.platform.modules.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.cache.CacheNames;
import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysConfigEntity;
import com.enterprise.auth.platform.modules.system.application.ConfigApplicationService;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.context.TenantProperties;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RegistrationPolicyService {

    public static final String CACHE_NAME = CacheNames.REGISTRATION_POLICY;
    public static final String CACHE_KEY_DEFAULT_TENANT = "default-tenant";
    public static final String CACHE_KEY_DEFAULT_ROLE_CODES = "default-role-codes";
    public static final String CONFIG_KEY_DEFAULT_TENANT = "registration.default_tenant_id";
    public static final String CONFIG_KEY_DEFAULT_ROLE_CODES = "registration.default_role_codes";
    public static final String FALLBACK_DEFAULT_TENANT = "tenant-a";

    private final ConfigApplicationService configApplicationService;
    private final TenantProperties tenantProperties;

    public RegistrationPolicyService(
            ConfigApplicationService configApplicationService,
            TenantProperties tenantProperties
    ) {
        this.configApplicationService = configApplicationService;
        this.tenantProperties = tenantProperties;
    }

    @Cacheable(value = CACHE_NAME, key = "'" + CACHE_KEY_DEFAULT_TENANT + "'")
    public String resolveDefaultTenantId() {
        return loadPlatformConfigValue(CONFIG_KEY_DEFAULT_TENANT)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .orElse(FALLBACK_DEFAULT_TENANT);
    }

    @Cacheable(value = CACHE_NAME, key = "'" + CACHE_KEY_DEFAULT_ROLE_CODES + "'")
    public Set<String> resolveDefaultRoleCodes() {
        String raw = loadPlatformConfigValue(CONFIG_KEY_DEFAULT_ROLE_CODES).orElse("");
        if (!StringUtils.hasText(raw)) {
            return Set.of();
        }
        LinkedHashSet<String> codes = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return codes.isEmpty() ? Set.of() : Collections.unmodifiableSet(codes);
    }

    private Optional<String> loadPlatformConfigValue(String configKey) {
        String platformTenantId = tenantProperties.platformTenantId();
        if (!StringUtils.hasText(platformTenantId)) {
            return Optional.empty();
        }
        String previousTenantId = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(platformTenantId);
            return configApplicationService.getConfigValue(platformTenantId, configKey);
        } finally {
            if (StringUtils.hasText(previousTenantId)) {
                TenantContext.setTenantId(previousTenantId);
            } else {
                TenantContext.clear();
            }
        }
    }
}
