package com.enterprise.auth.platform.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.persistence.entity.SysOauthClientEntity;
import com.enterprise.auth.platform.persistence.mapper.SysOauthClientMapper;
import com.enterprise.auth.platform.tenant.TenantContext;
import com.enterprise.auth.platform.tenant.TenantProperties;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OAuthClientLookupCacheService {

    public static final String CACHE_NAME = "oauth:registered-client";

    private final SysOauthClientMapper sysOauthClientMapper;
    private final TenantProperties tenantProperties;

    public OAuthClientLookupCacheService(
            @Nullable SysOauthClientMapper sysOauthClientMapper,
            TenantProperties tenantProperties
    ) {
        this.sysOauthClientMapper = sysOauthClientMapper;
        this.tenantProperties = tenantProperties;
    }

    @Cacheable(value = CACHE_NAME, key = "#root.target.clientIdCacheKey(#tenantId, #clientId)", unless = "#result == null")
    public CachedOAuthClient findByClientId(String tenantId, String clientId) {
        if (sysOauthClientMapper == null || !StringUtils.hasText(clientId)) {
            return null;
        }
        List<SysOauthClientEntity> matches = runInTenant(tenantId, () -> sysOauthClientMapper.selectList(
                new LambdaQueryWrapper<SysOauthClientEntity>()
                        .eq(SysOauthClientEntity::getDeleted, 0)
                        .eq(SysOauthClientEntity::getClientStatus, 1)
                        .eq(SysOauthClientEntity::getClientId, clientId)
                        .orderByAsc(SysOauthClientEntity::getId)
        ));
        if (matches.isEmpty()) {
            return null;
        }
        if (matches.size() > 1) {
            throw new IllegalStateException("检测到重复的 OAuth2 client_id，请保持 client_id 全局唯一: " + clientId);
        }
        return CachedOAuthClient.fromEntity(matches.get(0));
    }

    @Cacheable(value = CACHE_NAME, key = "#root.target.idCacheKey(#tenantId, #id)", unless = "#result == null")
    public CachedOAuthClient findById(String tenantId, String id) {
        if (sysOauthClientMapper == null || !StringUtils.hasText(id)) {
            return null;
        }
        SysOauthClientEntity currentTenantClient = runInTenant(tenantId, () -> sysOauthClientMapper.selectOne(
                new LambdaQueryWrapper<SysOauthClientEntity>()
                        .eq(SysOauthClientEntity::getDeleted, 0)
                        .eq(SysOauthClientEntity::getClientStatus, 1)
                        .eq(SysOauthClientEntity::getId, id)
                        .last("limit 1")
        ));
        if (currentTenantClient != null) {
            return CachedOAuthClient.fromEntity(currentTenantClient);
        }

        String platformTenantId = tenantProperties.platformTenantId();
        if (!StringUtils.hasText(platformTenantId) || platformTenantId.equals(tenantId)) {
            return null;
        }

        SysOauthClientEntity platformClient = runInTenant(platformTenantId, () -> sysOauthClientMapper.selectOne(
                new LambdaQueryWrapper<SysOauthClientEntity>()
                        .eq(SysOauthClientEntity::getDeleted, 0)
                        .eq(SysOauthClientEntity::getClientStatus, 1)
                        .eq(SysOauthClientEntity::getId, id)
                        .last("limit 1")
        ));
        return platformClient == null ? null : CachedOAuthClient.fromEntity(platformClient);
    }

    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void evictAll() {
        // Triggered on OAuth client writes to keep token endpoint lookups consistent.
    }

    String clientIdCacheKey(String tenantId, String clientId) {
        return (StringUtils.hasText(tenantId) ? tenantId.trim() : tenantProperties.platformTenantId())
                + ":client-id:"
                + clientId;
    }

    String idCacheKey(String tenantId, String id) {
        return (StringUtils.hasText(tenantId) ? tenantId.trim() : tenantProperties.platformTenantId())
                + ":id:"
                + id;
    }

    private <T> T runInTenant(String tenantId, Supplier<T> supplier) {
        String previousTenantId = TenantContext.getTenantId();
        if (StringUtils.hasText(tenantId)) {
            TenantContext.setTenantId(tenantId);
        } else {
            TenantContext.clear();
        }
        try {
            return supplier.get();
        } finally {
            if (StringUtils.hasText(previousTenantId)) {
                TenantContext.setTenantId(previousTenantId);
            } else {
                TenantContext.clear();
            }
        }
    }

    public record CachedOAuthClient(
            String id,
            String clientId,
            String clientSecret,
            String clientName,
            String redirectUris,
            String scopes,
            String grantTypes,
            boolean requirePkce,
            boolean requireConsent
    ) {
        static CachedOAuthClient fromEntity(SysOauthClientEntity entity) {
            return new CachedOAuthClient(
                    entity.getId() == null ? null : entity.getId().toString(),
                    entity.getClientId(),
                    entity.getClientSecret(),
                    entity.getClientName(),
                    entity.getRedirectUris(),
                    entity.getScopes(),
                    entity.getGrantTypes(),
                    entity.getRequirePkce() != null && entity.getRequirePkce() == 1,
                    entity.getRequireConsent() != null && entity.getRequireConsent() == 1
            );
        }
    }
}