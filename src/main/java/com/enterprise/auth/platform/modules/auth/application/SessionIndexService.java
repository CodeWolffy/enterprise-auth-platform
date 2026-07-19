package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.modules.auth.infrastructure.SecurityProperties;
import com.enterprise.auth.platform.modules.auth.interfaces.UserSessionResponse;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Sa-Token 之外的影子会话索引，供在线会话管理使用。
 *
 * <p>The service owns the application-facing contract and routes to either
 * the local fallback or the Redis implementation. Redis scripts, key access,
 * metadata compatibility, and pagination live in {@link RedisSessionIndex}.</p>
 */
@Service
public class SessionIndexService {

    private final SecurityProperties securityProperties;
    private final LocalSessionIndex localIndex;
    private final RedisSessionIndex redisIndex;

    public SessionIndexService(
            StringRedisTemplate redisTemplate,
            SecurityProperties securityProperties
    ) {
        this.securityProperties = securityProperties;
        SessionIndexKeySpace keySpace = new SessionIndexKeySpace(securityProperties);
        this.localIndex = new LocalSessionIndex(securityProperties);
        this.redisIndex = new RedisSessionIndex(redisTemplate, securityProperties, keySpace);
    }

    public void register(
            String token,
            Long userId,
            String username,
            String tenantId,
            String clientIp,
            String loginLocation,
            String device,
            long issuedAt,
            long expiresAt
    ) {
        if (!StringUtils.hasText(token) || userId == null) {
            return;
        }
        if (useLocalIndex()) {
            localIndex.register(token, userId, username, tenantId, clientIp, loginLocation, device, issuedAt, expiresAt);
            return;
        }
        redisIndex.register(token, userId, username, tenantId, clientIp, loginLocation, device, issuedAt, expiresAt);
    }

    /** Refresh lastAccessAt; the implementation applies the configured throttle. */
    public boolean touch(String token, long lastAccessAt) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        return useLocalIndex()
                ? localIndex.touch(token, lastAccessAt)
                : redisIndex.touch(token, lastAccessAt);
    }

    /** Updates activeTenantId only when the value actually changes. */
    public void updateActiveTenant(String token, String activeTenantId) {
        if (!StringUtils.hasText(token) || !StringUtils.hasText(activeTenantId)) {
            return;
        }
        if (useLocalIndex()) {
            localIndex.updateActiveTenant(token, activeTenantId);
        } else {
            redisIndex.updateActiveTenant(token, activeTenantId);
        }
    }

    public void remove(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        if (useLocalIndex()) {
            localIndex.remove(token);
        } else {
            redisIndex.remove(token);
        }
    }

    public void removeUser(Long userId) {
        if (userId == null) {
            return;
        }
        if (useLocalIndex()) {
            localIndex.removeUser(userId);
        } else {
            redisIndex.removeUser(userId);
        }
    }

    public Optional<List<IndexedSession>> recent(int limit) {
        return page(0, limit).map(Page::records);
    }

    /** Returns the random management ID, never the bearer token. */
    public Optional<String> managementId(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        return useLocalIndex() ? localIndex.managementId(token) : redisIndex.managementId(token);
    }

    /** Resolves a management ID to a token after checking the reverse index. */
    public Optional<String> resolveToken(String managementId) {
        if (!StringUtils.hasText(managementId)) {
            return Optional.empty();
        }
        return useLocalIndex() ? localIndex.resolveToken(managementId) : redisIndex.resolveToken(managementId);
    }

    public Optional<Long> count() {
        return useLocalIndex() ? localIndex.count() : redisIndex.count();
    }

    public Optional<Long> countVisible(String tenantId, boolean platformScope, Optional<Set<Long>> visibleUserIds) {
        if (platformScope) {
            return count();
        }
        return useLocalIndex()
                ? localIndex.countVisible(tenantId, visibleUserIds)
                : redisIndex.countVisible(tenantId, visibleUserIds);
    }

    /**
     * Filters within the tenant index before paging, avoiding cross-page misses
     * for restricted data scopes.
     */
    public Optional<Page> pageVisible(String tenantId, Optional<Set<Long>> visibleUserIds, int page, int size) {
        int effectivePage = Math.max(page, 0);
        int effectiveSize = Math.max(size, 1);
        if (visibleUserIds.isEmpty()) {
            return pageTenant(tenantId, effectivePage, effectiveSize);
        }
        return useLocalIndex()
                ? localIndex.pageVisible(tenantId, visibleUserIds, effectivePage, effectiveSize)
                : redisIndex.pageVisible(tenantId, visibleUserIds, effectivePage, effectiveSize);
    }

    public Optional<Page> page(int page, int size) {
        return useLocalIndex() ? localIndex.page(page, size) : redisIndex.page(page, size);
    }

    public Optional<Page> pageUser(Long userId, int page, int size) {
        if (userId == null) {
            return Optional.of(new Page(0, List.of()));
        }
        return useLocalIndex() ? localIndex.pageUser(userId, page, size) : redisIndex.pageUser(userId, page, size);
    }

    public Optional<Page> pageTenant(String tenantId, int page, int size) {
        if (!StringUtils.hasText(tenantId)) {
            return Optional.of(new Page(0, List.of()));
        }
        return useLocalIndex() ? localIndex.pageTenant(tenantId, page, size) : redisIndex.pageTenant(tenantId, page, size);
    }

    private boolean useLocalIndex() {
        return !securityProperties.resolvedRedis().sessionEnabled();
    }

    public record Page(long total, List<IndexedSession> records) {
    }

    public record IndexedSession(UserSessionResponse response, Long userId, String token) {
    }
}
