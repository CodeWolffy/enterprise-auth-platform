package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.modules.auth.infrastructure.SecurityProperties;
import com.enterprise.auth.platform.modules.auth.interfaces.UserSessionResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.util.StringUtils;

/**
 * Redis-backed implementation of the shadow session index.
 *
 * <p>The application-facing service keeps local fallback routing separate from
 * Redis key, script, serialization, and stale-entry handling.</p>
 */
final class RedisSessionIndex {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionIndex.class);
    private static final int VISIBLE_SESSION_SCAN_SIZE = 200;
    private static final String MANAGEMENT_ID_FIELD = "managementId";

    /**
     * KEYS[1]=meta KEYS[2]=allZset
     * ARGV[1]=lastAccessAt ARGV[2]=ttlSeconds ARGV[3]=throttleMs ARGV[4]=token
     * ARGV[5]=userZsetPrefix ARGV[6]=tenantZsetPrefix
     * return 0 missing / 1 updated / 2 throttled(no-write)
     */
    private static final DefaultRedisScript<Long> TOUCH_SCRIPT = new DefaultRedisScript<>(
            """
                    local userId = redis.call('HGET', KEYS[1], 'userId')
                    local tenantId = redis.call('HGET', KEYS[1], 'tenantId')
                    if not userId or not tenantId then
                      return 0
                    end
                    local now = tonumber(ARGV[1])
                    local throttle = tonumber(ARGV[3])
                    local last = redis.call('HGET', KEYS[1], 'lastAccessAt')
                    if last then
                      local prev = tonumber(last)
                      if prev and (now - prev) < throttle then
                        return 2
                      end
                    end
                    local userKey = ARGV[5] .. userId
                    local tenantKey = ARGV[6] .. tenantId
                    redis.call('HSET', KEYS[1], 'lastAccessAt', ARGV[1])
                    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
                    redis.call('ZADD', KEYS[2], now, ARGV[4])
                    redis.call('ZADD', userKey, now, ARGV[4])
                    redis.call('ZADD', tenantKey, now, ARGV[4])
                    redis.call('EXPIRE', KEYS[2], tonumber(ARGV[2]))
                    redis.call('EXPIRE', userKey, tonumber(ARGV[2]))
                    redis.call('EXPIRE', tenantKey, tonumber(ARGV[2]))
                    return 1
                    """,
            Long.class
    );

    /**
     * KEYS[1]=meta ARGV[1]=activeTenantId ARGV[2]=ttlSeconds
     * return 0 missing / 1 updated / 2 unchanged
     */
    private static final DefaultRedisScript<Long> UPDATE_TENANT_SCRIPT = new DefaultRedisScript<>(
            """
                    local current = redis.call('HGET', KEYS[1], 'activeTenantId')
                    if not redis.call('EXISTS', KEYS[1]) or redis.call('HGET', KEYS[1], 'userId') == false then
                      return 0
                    end
                    if current == ARGV[1] then
                      return 2
                    end
                    redis.call('HSET', KEYS[1], 'activeTenantId', ARGV[1])
                    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
                    return 1
                    """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final SecurityProperties securityProperties;
    private final SessionIndexKeySpace keySpace;

    RedisSessionIndex(
            StringRedisTemplate redisTemplate,
            SecurityProperties securityProperties,
            SessionIndexKeySpace keySpace
    ) {
        this.redisTemplate = redisTemplate;
        this.securityProperties = securityProperties;
        this.keySpace = keySpace;
    }

    void register(
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
        try {
            String sessionKey = sessionMetaKey(token);
            String existingManagementId = stringValue(redisTemplate.opsForHash().get(sessionKey, MANAGEMENT_ID_FIELD));
            String managementId = StringUtils.hasText(existingManagementId) ? existingManagementId : newManagementId();
            Map<String, String> meta = Map.ofEntries(
                    Map.entry("sessionId", managementId),
                    Map.entry(MANAGEMENT_ID_FIELD, managementId),
                    Map.entry("userId", String.valueOf(userId)),
                    Map.entry("username", valueOrEmpty(username)),
                    Map.entry("tenantId", valueOrEmpty(tenantId)),
                    Map.entry("activeTenantId", valueOrEmpty(tenantId)),
                    Map.entry("clientIp", valueOrEmpty(clientIp)),
                    Map.entry("loginLocation", valueOrEmpty(loginLocation)),
                    Map.entry("device", valueOrEmpty(device)),
                    Map.entry("issuedAt", String.valueOf(issuedAt)),
                    Map.entry("expiresAt", String.valueOf(expiresAt)),
                    Map.entry("lastAccessAt", String.valueOf(issuedAt))
            );
            redisTemplate.opsForHash().putAll(sessionKey, meta);
            redisTemplate.opsForValue().set(managementTokenKey(managementId), token, keySpace.indexTtl());
            redisTemplate.expire(sessionKey, keySpace.indexTtl());
            redisTemplate.opsForZSet().add(allSessionsKey(), token, issuedAt);
            redisTemplate.opsForZSet().add(userSessionsKey(userId), token, issuedAt);
            redisTemplate.opsForZSet().add(tenantSessionsKey(tenantId), token, issuedAt);
            redisTemplate.expire(allSessionsKey(), keySpace.indexTtl());
            redisTemplate.expire(userSessionsKey(userId), keySpace.indexTtl());
            redisTemplate.expire(tenantSessionsKey(tenantId), keySpace.indexTtl());
        } catch (RuntimeException ex) {
            log.warn("注册会话索引失败。token={}，error={}", token, ex.getMessage());
        }
    }

    boolean touch(String token, long lastAccessAt) {
        try {
            long ttlSeconds = Math.max(1, keySpace.indexTtl().toSeconds());
            long throttleMs = securityProperties.resolvedRedis().resolvedSessionTouchThrottle().toMillis();
            Long result = redisTemplate.execute(
                    TOUCH_SCRIPT,
                    List.of(sessionMetaKey(token), allSessionsKey()),
                    String.valueOf(lastAccessAt),
                    String.valueOf(ttlSeconds),
                    String.valueOf(throttleMs),
                    token,
                    keySpace.userZsetPrefix(),
                    keySpace.tenantZsetPrefix()
            );
            return result != null && result != 0L;
        } catch (RuntimeException ex) {
            log.debug("刷新会话索引失败。token={}，error={}", token, ex.getMessage());
            return false;
        }
    }

    void updateActiveTenant(String token, String activeTenantId) {
        try {
            long ttlSeconds = Math.max(1, keySpace.indexTtl().toSeconds());
            redisTemplate.execute(
                    UPDATE_TENANT_SCRIPT,
                    List.of(sessionMetaKey(token)),
                    activeTenantId,
                    String.valueOf(ttlSeconds)
            );
        } catch (RuntimeException ex) {
            log.debug("更新会话索引中的活跃租户失败。token={}，error={}", token, ex.getMessage());
        }
    }

    void remove(String token) {
        try {
            String sessionKey = sessionMetaKey(token);
            Object userId = redisTemplate.opsForHash().get(sessionKey, "userId");
            Object tenantId = redisTemplate.opsForHash().get(sessionKey, "tenantId");
            Object managementId = redisTemplate.opsForHash().get(sessionKey, MANAGEMENT_ID_FIELD);
            redisTemplate.opsForZSet().remove(allSessionsKey(), token);
            if (userId != null) {
                redisTemplate.opsForZSet().remove(userSessionsKey(String.valueOf(userId)), token);
            }
            if (tenantId != null) {
                redisTemplate.opsForZSet().remove(tenantSessionsKey(String.valueOf(tenantId)), token);
            }
            if (managementId != null) {
                redisTemplate.delete(managementTokenKey(String.valueOf(managementId)));
            }
            redisTemplate.delete(sessionKey);
        } catch (RuntimeException ex) {
            log.debug("移除会话索引失败。token={}，error={}", token, ex.getMessage());
        }
    }

    void removeUser(Long userId) {
        try {
            String userKey = userSessionsKey(userId);
            Collection<String> tokens = redisTemplate.opsForZSet().range(userKey, 0, -1);
            if (tokens != null && !tokens.isEmpty()) {
                List<SessionIndexService.IndexedSession> indexedSessions = readBatch(tokens);
                indexedSessions.stream()
                        .map(SessionIndexService.IndexedSession::response)
                        .map(UserSessionResponse::tenantId)
                        .filter(StringUtils::hasText)
                        .distinct()
                        .forEach(tenantId -> redisTemplate.opsForZSet()
                                .remove(tenantSessionsKey(tenantId), tokens.toArray()));
                Object[] managementIds = indexedSessions.stream()
                        .map(SessionIndexService.IndexedSession::response)
                        .map(UserSessionResponse::sessionId)
                        .filter(StringUtils::hasText)
                        .toArray();
                if (managementIds.length > 0) {
                    redisTemplate.delete(java.util.Arrays.stream(managementIds)
                            .map(String::valueOf)
                            .map(this::managementTokenKey)
                            .toList());
                }
                redisTemplate.opsForZSet().remove(allSessionsKey(), tokens.toArray());
                redisTemplate.delete(tokens.stream().map(this::sessionMetaKey).toList());
            }
            redisTemplate.delete(userKey);
        } catch (RuntimeException ex) {
            log.debug("移除用户会话索引失败。userId={}，error={}", userId, ex.getMessage());
        }
    }

    Optional<String> managementId(String token) {
        try {
            String metaKey = sessionMetaKey(token);
            Boolean exists = redisTemplate.hasKey(metaKey);
            if (!Boolean.TRUE.equals(exists)) {
                return Optional.empty();
            }
            return ensureManagementId(token, metaKey);
        } catch (RuntimeException ex) {
            log.debug("解析会话管理ID失败。error={}", ex.getMessage());
            return Optional.empty();
        }
    }

    Optional<String> resolveToken(String managementId) {
        try {
            String token = redisTemplate.opsForValue().get(managementTokenKey(managementId));
            if (!StringUtils.hasText(token)) {
                return Optional.empty();
            }
            Object indexedManagementId = redisTemplate.opsForHash().get(sessionMetaKey(token), MANAGEMENT_ID_FIELD);
            if (!managementId.equals(stringValue(indexedManagementId))) {
                redisTemplate.delete(managementTokenKey(managementId));
                return Optional.empty();
            }
            return Optional.of(token);
        } catch (RuntimeException ex) {
            log.debug("解析会话管理ID对应令牌失败。error={}", ex.getMessage());
            return Optional.empty();
        }
    }

    Optional<Long> count() {
        try {
            Long total = redisTemplate.opsForZSet().zCard(allSessionsKey());
            return Optional.of(total == null ? 0L : total);
        } catch (RuntimeException ex) {
            log.warn("统计会话索引失败。error={}", ex.getMessage());
            return Optional.empty();
        }
    }

    Optional<Long> countVisible(String tenantId, Optional<Set<Long>> visibleUserIds) {
        try {
            return Optional.of(scanVisibleTenant(tenantId, visibleUserIds, 0, 0).total());
        } catch (RuntimeException ex) {
            log.warn("统计可见会话索引失败。error={}", ex.getMessage());
            return Optional.empty();
        }
    }

    Optional<SessionIndexService.Page> pageVisible(
            String tenantId,
            Optional<Set<Long>> visibleUserIds,
            int page,
            int size
    ) {
        try {
            long start = (long) page * size;
            return Optional.of(scanVisibleTenant(tenantId, visibleUserIds, start, size));
        } catch (RuntimeException ex) {
            log.warn("读取可见会话索引失败。tenantId={}，error={}", tenantId, ex.getMessage());
            return Optional.empty();
        }
    }

    Optional<SessionIndexService.Page> page(int page, int size) {
        try {
            String key = allSessionsKey();
            Long total = redisTemplate.opsForZSet().zCard(key);
            if (total == null || total == 0) {
                return Optional.of(new SessionIndexService.Page(0, List.of()));
            }
            long start = (long) page * size;
            long end = start + size - 1;
            Collection<String> tokens = redisTemplate.opsForZSet().reverseRange(key, start, end);
            if (tokens == null || tokens.isEmpty()) {
                return Optional.of(new SessionIndexService.Page(total, List.of()));
            }
            return Optional.of(new SessionIndexService.Page(total, readBatch(tokens)));
        } catch (RuntimeException ex) {
            log.warn("读取会话索引失败。error={}", ex.getMessage());
            return Optional.empty();
        }
    }

    Optional<SessionIndexService.Page> pageUser(Long userId, int page, int size) {
        try {
            String key = userSessionsKey(userId);
            Long total = redisTemplate.opsForZSet().zCard(key);
            if (total == null || total == 0) {
                return Optional.of(new SessionIndexService.Page(0, List.of()));
            }
            long start = (long) page * size;
            long end = start + size - 1;
            Collection<String> tokens = redisTemplate.opsForZSet().reverseRange(key, start, end);
            if (tokens == null || tokens.isEmpty()) {
                return Optional.of(new SessionIndexService.Page(total, List.of()));
            }
            List<SessionIndexService.IndexedSession> sessions = readBatch(tokens).stream()
                    .filter(session -> userId.equals(session.userId()))
                    .toList();
            return Optional.of(new SessionIndexService.Page(total, sessions));
        } catch (RuntimeException ex) {
            log.warn("读取用户会话索引失败。userId={}，error={}", userId, ex.getMessage());
            return Optional.empty();
        }
    }

    Optional<SessionIndexService.Page> pageTenant(String tenantId, int page, int size) {
        try {
            String key = tenantSessionsKey(tenantId);
            Long total = redisTemplate.opsForZSet().zCard(key);
            if (total == null || total == 0) {
                return Optional.of(new SessionIndexService.Page(0, List.of()));
            }
            int effectivePage = Math.max(page, 0);
            int effectiveSize = Math.max(size, 1);
            long start = (long) effectivePage * effectiveSize;
            long end = start + effectiveSize - 1;
            Collection<String> tokens = redisTemplate.opsForZSet().reverseRange(key, start, end);
            if (tokens == null || tokens.isEmpty()) {
                return Optional.of(new SessionIndexService.Page(total, List.of()));
            }
            return Optional.of(new SessionIndexService.Page(total, readBatch(tokens)));
        } catch (RuntimeException ex) {
            log.warn("读取租户会话索引失败。tenantId={}，error={}", tenantId, ex.getMessage());
            return Optional.empty();
        }
    }

    private SessionIndexService.Page scanVisibleTenant(
            String tenantId,
            Optional<Set<Long>> visibleUserIds,
            long pageStart,
            int pageSize
    ) {
        String key = tenantSessionsKey(tenantId);
        Long indexedTotal = redisTemplate.opsForZSet().zCard(key);
        if (indexedTotal == null || indexedTotal == 0) {
            return new SessionIndexService.Page(0, List.of());
        }
        List<SessionIndexService.IndexedSession> pageRecords = new ArrayList<>(Math.max(pageSize, 0));
        long visibleTotal = 0;
        for (long offset = 0; offset < indexedTotal; offset += VISIBLE_SESSION_SCAN_SIZE) {
            long end = Math.min(offset + VISIBLE_SESSION_SCAN_SIZE - 1, indexedTotal - 1);
            Collection<String> tokens = redisTemplate.opsForZSet().reverseRange(key, offset, end);
            if (tokens == null || tokens.isEmpty()) {
                break;
            }
            for (SessionIndexService.IndexedSession session : readBatch(tokens)) {
                if (!visibleSession(session, tenantId, visibleUserIds)) {
                    continue;
                }
                if (visibleTotal >= pageStart && pageRecords.size() < pageSize) {
                    pageRecords.add(session);
                }
                visibleTotal++;
            }
        }
        return new SessionIndexService.Page(visibleTotal, List.copyOf(pageRecords));
    }

    /** Pipeline HGETALL calls so a page needs one Redis round trip for metadata. */
    @SuppressWarnings("unchecked")
    private List<SessionIndexService.IndexedSession> readBatch(Collection<String> tokens) {
        List<String> tokenList = tokens.stream().filter(StringUtils::hasText).toList();
        if (tokenList.isEmpty()) {
            return List.of();
        }
        List<Object> raw = redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                for (String token : tokenList) {
                    operations.opsForHash().entries(sessionMetaKey(token));
                }
                return null;
            }
        });
        List<SessionIndexService.IndexedSession> result = new ArrayList<>(tokenList.size());
        for (int i = 0; i < tokenList.size(); i++) {
            String token = tokenList.get(i);
            Object entry = i < raw.size() ? raw.get(i) : null;
            parseMeta(token, toObjectMap(entry)).ifPresent(result::add);
        }
        return result;
    }

    private Map<Object, Object> toObjectMap(Object entry) {
        if (entry instanceof Map<?, ?> map) {
            Map<Object, Object> result = new HashMap<>(map.size());
            map.forEach((k, v) -> result.put(k, v));
            return result;
        }
        return Map.of();
    }

    private Optional<SessionIndexService.IndexedSession> parseMeta(String token, Map<Object, Object> meta) {
        if (meta == null || meta.isEmpty()) {
            redisTemplate.opsForZSet().remove(allSessionsKey(), token);
            return Optional.empty();
        }
        Long userId = longValue(meta.get("userId"), null);
        String username = stringValue(meta.get("username"));
        String tenantId = stringValue(meta.get("tenantId"));
        String activeTenantId = stringValue(meta.get("activeTenantId"));
        if (userId == null || !StringUtils.hasText(username) || !StringUtils.hasText(tenantId)
                || !StringUtils.hasText(activeTenantId)) {
            remove(token);
            return Optional.empty();
        }
        String managementId = stringValue(meta.get(MANAGEMENT_ID_FIELD));
        if (!StringUtils.hasText(managementId)) {
            managementId = ensureManagementId(token, sessionMetaKey(token)).orElse("");
        }
        if (!StringUtils.hasText(managementId)) {
            return Optional.empty();
        }
        UserSessionResponse response = new UserSessionResponse(
                managementId,
                username,
                tenantId,
                activeTenantId,
                stringValue(meta.get("clientIp")),
                stringValue(meta.get("loginLocation")),
                stringValue(meta.get("device")),
                TimeSupport.fromEpochMilli(longValue(meta.get("issuedAt"), 0L)),
                TimeSupport.fromEpochMilli(longValue(meta.get("expiresAt"), 0L)),
                TimeSupport.fromEpochMilli(longValue(meta.get("lastAccessAt"), 0L)),
                true,
                false
        );
        return Optional.of(new SessionIndexService.IndexedSession(response, userId, token));
    }

    private boolean visibleSession(
            SessionIndexService.IndexedSession session,
            String tenantId,
            Optional<Set<Long>> visibleUserIds
    ) {
        if (session == null || session.response() == null || !tenantId.equals(session.response().tenantId())) {
            return false;
        }
        return visibleUserIds.map(userIds -> userIds.contains(session.userId())).orElse(true);
    }

    private Optional<String> ensureManagementId(String token, String metaKey) {
        Object existing = redisTemplate.opsForHash().get(metaKey, MANAGEMENT_ID_FIELD);
        String managementId = stringValue(existing);
        if (!StringUtils.hasText(managementId)) {
            String candidate = newManagementId();
            Boolean inserted = redisTemplate.opsForHash().putIfAbsent(metaKey, MANAGEMENT_ID_FIELD, candidate);
            managementId = Boolean.FALSE.equals(inserted)
                    ? stringValue(redisTemplate.opsForHash().get(metaKey, MANAGEMENT_ID_FIELD))
                    : candidate;
            if (!StringUtils.hasText(managementId)) {
                return Optional.empty();
            }
            redisTemplate.opsForHash().put(metaKey, "sessionId", managementId);
        }
        redisTemplate.opsForValue().set(managementTokenKey(managementId), token, keySpace.indexTtl());
        return Optional.of(managementId);
    }

    private String allSessionsKey() {
        return keySpace.allSessionsKey();
    }

    private String userSessionsKey(Long userId) {
        return keySpace.userSessionsKey(userId);
    }

    private String userSessionsKey(String userId) {
        return keySpace.userSessionsKey(userId);
    }

    private String tenantSessionsKey(String tenantId) {
        return keySpace.tenantSessionsKey(tenantId);
    }

    private String sessionMetaKey(String token) {
        return keySpace.sessionMetaKey(token);
    }

    private String managementTokenKey(String managementId) {
        return keySpace.managementTokenKey(managementId);
    }

    private String newManagementId() {
        return UUID.randomUUID().toString();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Long longValue(Object value, Long fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
