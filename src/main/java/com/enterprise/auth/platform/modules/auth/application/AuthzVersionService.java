package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.modules.auth.infrastructure.SecurityProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * 权限版本号：菜单/角色/套餐等变更时 O(1) 递增，会话下次校验发现版本不一致后重建快照。
 * 替代全量在线会话扫描。
 */
@Service
public class AuthzVersionService {

    private static final String GLOBAL_KEY = "authz:version:global";
    private static final String TENANT_KEY_PREFIX = "authz:version:tenant:";
    private static final String REQUEST_CACHE_ATTRIBUTE = AuthzVersionService.class.getName() + ".versions";

    private final StringRedisTemplate redisTemplate;
    private final SecurityProperties securityProperties;

    public AuthzVersionService(StringRedisTemplate redisTemplate, SecurityProperties securityProperties) {
        this.redisTemplate = redisTemplate;
        this.securityProperties = securityProperties;
    }

    public long currentGlobalVersion() {
        RequestVersionCache cache = requestCache(true);
        if (cache != null && cache.globalVersion != null) {
            return cache.globalVersion;
        }
        long version = read(globalKey());
        if (cache != null) {
            cache.globalVersion = version;
        }
        return version;
    }

    public long currentTenantVersion(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return 0L;
        }
        String normalizedTenantId = tenantId.trim();
        RequestVersionCache cache = requestCache(true);
        if (cache != null && cache.tenantVersions.containsKey(normalizedTenantId)) {
            return cache.tenantVersions.get(normalizedTenantId);
        }
        long version = read(tenantKey(normalizedTenantId));
        if (cache != null) {
            cache.tenantVersions.put(normalizedTenantId, version);
        }
        return version;
    }

    /**
     * 一次 Redis 往返读取全局与租户权限版本，并在当前 HTTP 请求内复用结果。
     * 请求结束后缓存随 RequestAttributes 一同释放，因此下一请求仍会立即感知版本变更。
     */
    public Versions currentVersions(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return new Versions(currentGlobalVersion(), 0L);
        }
        String normalizedTenantId = tenantId.trim();
        RequestVersionCache cache = requestCache(true);
        if (cache != null
                && cache.globalVersion != null
                && cache.tenantVersions.containsKey(normalizedTenantId)) {
            return new Versions(cache.globalVersion, cache.tenantVersions.get(normalizedTenantId));
        }

        Versions versions;
        if (cache == null || (cache.globalVersion == null && !cache.tenantVersions.containsKey(normalizedTenantId))) {
            versions = readPair(normalizedTenantId);
        } else {
            long globalVersion = cache.globalVersion == null ? read(globalKey()) : cache.globalVersion;
            long tenantVersion = cache.tenantVersions.containsKey(normalizedTenantId)
                    ? cache.tenantVersions.get(normalizedTenantId)
                    : read(tenantKey(normalizedTenantId));
            versions = new Versions(globalVersion, tenantVersion);
        }
        if (cache != null) {
            cache.globalVersion = versions.global();
            cache.tenantVersions.put(normalizedTenantId, versions.tenant());
        }
        return versions;
    }

    /**
     * 绕过请求级缓存读取最新权限版本。
     * 主要用于创建会话权限快照等必须以 Redis 当前值为准的边界场景。
     */
    public Versions currentVersionsFresh(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return new Versions(read(globalKey()), 0L);
        }
        return readPair(tenantId.trim());
    }

    public long bumpGlobal() {
        return bump(globalKey());
    }

    public long bumpTenant(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return bumpGlobal();
        }
        return bump(tenantKey(tenantId));
    }

    private long bump(String key) {
        try {
            Long value = redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, 365, TimeUnit.DAYS);
            return value == null ? 0L : value;
        } catch (RuntimeException ex) {
            return System.currentTimeMillis();
        } finally {
            invalidateRequestCache();
        }
    }

    private Versions readPair(String tenantId) {
        try {
            List<String> values = redisTemplate.opsForValue().multiGet(List.of(globalKey(), tenantKey(tenantId)));
            if (values == null) {
                return Versions.ZERO;
            }
            long globalVersion = values.isEmpty() ? 0L : parse(values.get(0));
            long tenantVersion = values.size() < 2 ? 0L : parse(values.get(1));
            return new Versions(globalVersion, tenantVersion);
        } catch (RuntimeException ex) {
            return Versions.ZERO;
        }
    }

    private long read(String key) {
        try {
            return parse(redisTemplate.opsForValue().get(key));
        } catch (RuntimeException ex) {
            return 0L;
        }
    }

    private long parse(String value) {
        if (!StringUtils.hasText(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private RequestVersionCache requestCache(boolean create) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        try {
            Object existing = attributes.getAttribute(REQUEST_CACHE_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
            if (existing instanceof RequestVersionCache cache) {
                return cache;
            }
            if (!create) {
                return null;
            }
            RequestVersionCache cache = new RequestVersionCache();
            attributes.setAttribute(REQUEST_CACHE_ATTRIBUTE, cache, RequestAttributes.SCOPE_REQUEST);
            return cache;
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private void invalidateRequestCache() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }
        try {
            attributes.removeAttribute(REQUEST_CACHE_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        } catch (IllegalStateException ignored) {
            // 请求已结束时无需再清理。
        }
    }

    private String globalKey() {
        return securityProperties.resolvedRedis().resolvedNamespacePrefix() + GLOBAL_KEY;
    }

    private String tenantKey(String tenantId) {
        return securityProperties.resolvedRedis().resolvedNamespacePrefix() + TENANT_KEY_PREFIX + tenantId.trim();
    }

    public record Versions(long global, long tenant) {
        private static final Versions ZERO = new Versions(0L, 0L);
    }

    private static final class RequestVersionCache {
        private Long globalVersion;
        private final Map<String, Long> tenantVersions = new HashMap<>();
    }
}
