package com.enterprise.auth.platform.modules.auth.infrastructure;

import com.enterprise.auth.platform.common.context.TenantContext;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AuthPrincipalCacheService {

    public static final String CACHE_NAME = "auth:principal";

    private final CacheManager cacheManager;

    public AuthPrincipalCacheService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public static String usernameKey(String tenantId, String username) {
        return "username:" + tenantId + ":" + username;
    }

    public static String idKey(Long userId) {
        return "id:" + userId;
    }

    public static String currentTenantIdKey(Long userId) {
        return idKey(TenantContext.getTenantId(), userId);
    }

    public static String idKey(String tenantId, Long userId) {
        return "id:" + (StringUtils.hasText(tenantId) ? tenantId : "") + ":" + userId;
    }

    public void evictByUser(Long userId, String tenantId, String username) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) {
            return;
        }
        if (userId != null) {
            cache.evict(idKey(userId));
            cache.evict(idKey(tenantId, userId));
        }
        if (StringUtils.hasText(tenantId) && StringUtils.hasText(username)) {
            cache.evict(usernameKey(tenantId, username));
        }
    }

    public void evictAll() {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.clear();
        }
    }
}