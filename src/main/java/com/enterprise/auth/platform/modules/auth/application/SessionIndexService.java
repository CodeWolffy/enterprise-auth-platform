package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.modules.auth.infrastructure.SecurityProperties;
import com.enterprise.auth.platform.modules.auth.interfaces.UserSessionResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Sa-Token 之外的影子会话索引，供在线会话管理使用。
 * <p>请求路径上的 touch 做节流；列表读取用 pipeline 批 HGETALL，避免 N 次 RTT。</p>
 */
@Service
public class SessionIndexService {

    private static final Logger log = LoggerFactory.getLogger(SessionIndexService.class);
    private static final String ALL_SESSIONS_KEY = "session:index:all";
    private static final String TENANT_SESSIONS_PREFIX = "session:index:tenant:";
    private static final String USER_SESSIONS_PREFIX = "session:index:user:";
    private static final String SESSION_META_PREFIX = "session:meta:";

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
    private final ConcurrentMap<String, IndexedSession> localSessions = new ConcurrentHashMap<>();

    public SessionIndexService(
            StringRedisTemplate redisTemplate,
            SecurityProperties securityProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.securityProperties = securityProperties;
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
            localSessions.put(token, new IndexedSession(new UserSessionResponse(
                    token,
                    valueOrEmpty(username),
                    valueOrEmpty(tenantId),
                    valueOrEmpty(tenantId),
                    valueOrEmpty(clientIp),
                    valueOrEmpty(loginLocation),
                    valueOrEmpty(device),
                    TimeSupport.fromEpochMilli(issuedAt),
                    TimeSupport.fromEpochMilli(expiresAt),
                    TimeSupport.fromEpochMilli(issuedAt),
                    true,
                    false
            ), userId));
            return;
        }
        try {
            String sessionKey = sessionMetaKey(token);
            Map<String, String> meta = Map.ofEntries(
                    Map.entry("sessionId", token),
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
            redisTemplate.expire(sessionKey, indexTtl());
            redisTemplate.opsForZSet().add(allSessionsKey(), token, issuedAt);
            redisTemplate.opsForZSet().add(userSessionsKey(userId), token, issuedAt);
            redisTemplate.opsForZSet().add(tenantSessionsKey(tenantId), token, issuedAt);
            redisTemplate.expire(allSessionsKey(), indexTtl());
            redisTemplate.expire(userSessionsKey(userId), indexTtl());
            redisTemplate.expire(tenantSessionsKey(tenantId), indexTtl());
        } catch (RuntimeException ex) {
            log.warn("注册会话索引失败。token={}，error={}", token, ex.getMessage());
        }
    }

    /**
     * 刷新 lastAccessAt。节流窗口内不写 Redis，返回 true 表示索引仍存在。
     */
    public boolean touch(String token, long lastAccessAt) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        if (useLocalIndex()) {
            return touchLocal(token, lastAccessAt);
        }
        try {
            long ttlSeconds = Math.max(1, indexTtl().toSeconds());
            long throttleMs = securityProperties.resolvedRedis().resolvedSessionTouchThrottle().toMillis();
            String userZsetPrefix = securityProperties.resolvedRedis().resolvedNamespacePrefix() + USER_SESSIONS_PREFIX;
            String tenantZsetPrefix = securityProperties.resolvedRedis().resolvedNamespacePrefix() + TENANT_SESSIONS_PREFIX;
            Long result = redisTemplate.execute(
                    TOUCH_SCRIPT,
                    List.of(sessionMetaKey(token), allSessionsKey()),
                    String.valueOf(lastAccessAt),
                    String.valueOf(ttlSeconds),
                    String.valueOf(throttleMs),
                    token,
                    userZsetPrefix,
                    tenantZsetPrefix
            );
            return result != null && result != 0L;
        } catch (RuntimeException ex) {
            log.debug("刷新会话索引失败。token={}，error={}", token, ex.getMessage());
            return false;
        }
    }

    /**
     * 仅在 activeTenantId 实际变更时写 Redis。
     */
    public void updateActiveTenant(String token, String activeTenantId) {
        if (!StringUtils.hasText(token) || !StringUtils.hasText(activeTenantId)) {
            return;
        }
        if (useLocalIndex()) {
            updateActiveTenantLocal(token, activeTenantId);
            return;
        }
        try {
            long ttlSeconds = Math.max(1, indexTtl().toSeconds());
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

    public void remove(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        if (useLocalIndex()) {
            localSessions.remove(token);
            return;
        }
        try {
            String sessionKey = sessionMetaKey(token);
            Object userId = redisTemplate.opsForHash().get(sessionKey, "userId");
            Object tenantId = redisTemplate.opsForHash().get(sessionKey, "tenantId");
            redisTemplate.opsForZSet().remove(allSessionsKey(), token);
            if (userId != null) {
                redisTemplate.opsForZSet().remove(userSessionsKey(String.valueOf(userId)), token);
            }
            if (tenantId != null) {
                redisTemplate.opsForZSet().remove(tenantSessionsKey(String.valueOf(tenantId)), token);
            }
            redisTemplate.delete(sessionKey);
        } catch (RuntimeException ex) {
            log.debug("移除会话索引失败。token={}，error={}", token, ex.getMessage());
        }
    }

    public void removeUser(Long userId) {
        if (userId == null) {
            return;
        }
        if (useLocalIndex()) {
            localSessions.entrySet().removeIf(entry -> userId.equals(entry.getValue().userId()));
            return;
        }
        try {
            String userKey = userSessionsKey(userId);
            Collection<String> tokens = redisTemplate.opsForZSet().range(userKey, 0, -1);
            if (tokens != null && !tokens.isEmpty()) {
                readBatch(tokens).stream()
                        .map(IndexedSession::response)
                        .map(UserSessionResponse::tenantId)
                        .filter(StringUtils::hasText)
                        .distinct()
                        .forEach(tenantId -> redisTemplate.opsForZSet()
                                .remove(tenantSessionsKey(tenantId), tokens.toArray()));
                redisTemplate.opsForZSet().remove(allSessionsKey(), tokens.toArray());
                redisTemplate.delete(tokens.stream().map(this::sessionMetaKey).toList());
            }
            redisTemplate.delete(userKey);
        } catch (RuntimeException ex) {
            log.debug("移除用户会话索引失败。userId={}，error={}", userId, ex.getMessage());
        }
    }

    public Optional<List<IndexedSession>> recent(int limit) {
        return page(0, limit).map(Page::records);
    }

    public Optional<Long> count() {
        if (useLocalIndex()) {
            return Optional.of((long) localSessions.size());
        }
        try {
            Long total = redisTemplate.opsForZSet().zCard(allSessionsKey());
            return Optional.of(total == null ? 0L : total);
        } catch (RuntimeException ex) {
            log.warn("统计会话索引失败。error={}", ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<Long> countVisible(String tenantId, boolean platformScope, Optional<Set<Long>> visibleUserIds) {
        if (platformScope) {
            return count();
        }
        if (useLocalIndex()) {
            return Optional.of(localSessions.values().stream()
                    .filter(session -> visibleSession(session, tenantId, visibleUserIds))
                    .count());
        }
        try {
            long total = 0;
            int page = 0;
            int size = 200;
            while (true) {
                Optional<Page> pageResult = pageTenant(tenantId, page, size);
                if (pageResult.isEmpty()) {
                    return Optional.empty();
                }
                List<IndexedSession> records = pageResult.get().records();
                if (records.isEmpty()) {
                    break;
                }
                total += records.stream()
                        .filter(session -> visibleSession(session, tenantId, visibleUserIds))
                        .count();
                if ((long) (page + 1) * size >= pageResult.get().total()) {
                    break;
                }
                page++;
            }
            return Optional.of(total);
        } catch (RuntimeException ex) {
            log.warn("统计可见会话索引失败。error={}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 在租户索引内完成过滤和分页，避免先对全局索引分页造成的跨页遗漏。
     * ALL 数据范围直接使用 ZSET 分页和 ZCARD；受限数据范围只扫描当前租户。
     */
    public Optional<Page> pageVisible(String tenantId, Optional<Set<Long>> visibleUserIds, int page, int size) {
        int effectivePage = Math.max(page, 0);
        int effectiveSize = Math.max(size, 1);
        if (visibleUserIds.isEmpty()) {
            return pageTenant(tenantId, effectivePage, effectiveSize);
        }
        if (useLocalIndex()) {
            List<IndexedSession> matched = localSessions.values().stream()
                    .filter(session -> visibleSession(session, tenantId, visibleUserIds))
                    .sorted(Comparator.comparingLong((IndexedSession session) ->
                            instantEpoch(session.response().lastAccessAt())).reversed())
                    .toList();
            long start = (long) effectivePage * effectiveSize;
            return Optional.of(new Page(matched.size(), matched.stream().skip(start).limit(effectiveSize).toList()));
        }
        try {
            List<IndexedSession> matched = new ArrayList<>();
            int scanPage = 0;
            int scanSize = 200;
            while (true) {
                Optional<Page> batch = pageTenant(tenantId, scanPage, scanSize);
                if (batch.isEmpty()) {
                    return Optional.empty();
                }
                matched.addAll(batch.get().records().stream()
                        .filter(session -> visibleSession(session, tenantId, visibleUserIds))
                        .toList());
                if ((long) (scanPage + 1) * scanSize >= batch.get().total()) {
                    break;
                }
                scanPage++;
            }
            long start = (long) effectivePage * effectiveSize;
            return Optional.of(new Page(matched.size(), matched.stream().skip(start).limit(effectiveSize).toList()));
        } catch (RuntimeException ex) {
            log.warn("读取可见会话索引失败。tenantId={}，error={}", tenantId, ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<Page> page(int page, int size) {
        if (useLocalIndex()) {
            return Optional.of(localPage(page, size));
        }
        try {
            String key = allSessionsKey();
            Long total = redisTemplate.opsForZSet().zCard(key);
            if (total == null || total == 0) {
                return Optional.of(new Page(0, List.of()));
            }
            long start = (long) page * size;
            long end = start + size - 1;
            Collection<String> tokens = redisTemplate.opsForZSet().reverseRange(key, start, end);
            if (tokens == null || tokens.isEmpty()) {
                return Optional.of(new Page(total, List.of()));
            }
            List<IndexedSession> sessions = readBatch(tokens);
            return Optional.of(new Page(total, sessions));
        } catch (RuntimeException ex) {
            log.warn("读取会话索引失败。error={}", ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<Page> pageUser(Long userId, int page, int size) {
        if (userId == null) {
            return Optional.of(new Page(0, List.of()));
        }
        if (useLocalIndex()) {
            return Optional.of(localUserPage(userId, page, size));
        }
        try {
            String key = userSessionsKey(userId);
            Long total = redisTemplate.opsForZSet().zCard(key);
            if (total == null || total == 0) {
                return Optional.of(new Page(0, List.of()));
            }
            long start = (long) page * size;
            long end = start + size - 1;
            Collection<String> tokens = redisTemplate.opsForZSet().reverseRange(key, start, end);
            if (tokens == null || tokens.isEmpty()) {
                return Optional.of(new Page(total, List.of()));
            }
            List<IndexedSession> sessions = readBatch(tokens).stream()
                    .filter(session -> userId.equals(session.userId()))
                    .toList();
            return Optional.of(new Page(total, sessions));
        } catch (RuntimeException ex) {
            log.warn("读取用户会话索引失败。userId={}，error={}", userId, ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<Page> pageTenant(String tenantId, int page, int size) {
        if (!StringUtils.hasText(tenantId)) {
            return Optional.of(new Page(0, List.of()));
        }
        if (useLocalIndex()) {
            int effectivePage = Math.max(page, 0);
            int effectiveSize = Math.max(size, 1);
            List<IndexedSession> matched = localSessions.values().stream()
                    .filter(session -> tenantId.equals(session.response().tenantId()))
                    .sorted(Comparator.comparingLong((IndexedSession session) ->
                            instantEpoch(session.response().lastAccessAt())).reversed())
                    .toList();
            long start = (long) effectivePage * effectiveSize;
            return Optional.of(new Page(matched.size(), matched.stream().skip(start).limit(effectiveSize).toList()));
        }
        try {
            String key = tenantSessionsKey(tenantId);
            Long total = redisTemplate.opsForZSet().zCard(key);
            if (total == null || total == 0) {
                return Optional.of(new Page(0, List.of()));
            }
            long start = (long) Math.max(page, 0) * Math.max(size, 1);
            long end = start + Math.max(size, 1) - 1;
            Collection<String> tokens = redisTemplate.opsForZSet().reverseRange(key, start, end);
            if (tokens == null || tokens.isEmpty()) {
                return Optional.of(new Page(total, List.of()));
            }
            return Optional.of(new Page(total, readBatch(tokens)));
        } catch (RuntimeException ex) {
            log.warn("读取租户会话索引失败。tenantId={}，error={}", tenantId, ex.getMessage());
            return Optional.empty();
        }
    }

    public record Page(long total, List<IndexedSession> records) {
    }

    private boolean visibleSession(IndexedSession session, String tenantId, Optional<Set<Long>> visibleUserIds) {
        if (session == null || session.response() == null) {
            return false;
        }
        if (!tenantId.equals(session.response().tenantId())) {
            return false;
        }
        return visibleUserIds.map(userIds -> userIds.contains(session.userId())).orElse(true);
    }

    /** pipeline 批量 HGETALL，单次 RTT 读多条 session meta。 */
    @SuppressWarnings("unchecked")
    private List<IndexedSession> readBatch(Collection<String> tokens) {
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
        List<IndexedSession> result = new ArrayList<>(tokenList.size());
        for (int i = 0; i < tokenList.size(); i++) {
            String token = tokenList.get(i);
            Object entry = i < raw.size() ? raw.get(i) : null;
            Map<Object, Object> meta = toObjectMap(entry);
            parseMeta(token, meta).ifPresent(result::add);
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

    private Optional<IndexedSession> parseMeta(String token, Map<Object, Object> meta) {
        if (meta == null || meta.isEmpty()) {
            redisTemplate.opsForZSet().remove(allSessionsKey(), token);
            return Optional.empty();
        }
        Long userId = longValue(meta.get("userId"), null);
        String username = stringValue(meta.get("username"));
        String tenantId = stringValue(meta.get("tenantId"));
        String activeTenantId = stringValue(meta.get("activeTenantId"));
        if (userId == null || !StringUtils.hasText(username) || !StringUtils.hasText(tenantId) || !StringUtils.hasText(activeTenantId)) {
            remove(token);
            return Optional.empty();
        }
        UserSessionResponse response = new UserSessionResponse(
                token,
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
        return Optional.of(new IndexedSession(response, userId));
    }

    private boolean useLocalIndex() {
        return !securityProperties.resolvedRedis().sessionEnabled();
    }

    private boolean touchLocal(String token, long lastAccessAt) {
        IndexedSession existing = localSessions.get(token);
        if (existing == null) {
            return false;
        }
        UserSessionResponse session = existing.response();
        long previous = session.lastAccessAt() == null ? 0L : session.lastAccessAt().toEpochMilli();
        long throttleMs = securityProperties.resolvedRedis().resolvedSessionTouchThrottle().toMillis();
        if (lastAccessAt - previous < throttleMs) {
            return true;
        }
        localSessions.put(token, new IndexedSession(new UserSessionResponse(
                session.sessionId(),
                session.username(),
                session.tenantId(),
                session.activeTenantId(),
                session.clientIp(),
                session.loginLocation(),
                session.device(),
                session.issuedAt(),
                session.expiresAt(),
                TimeSupport.fromEpochMilli(lastAccessAt),
                session.active(),
                session.currentSession()
        ), existing.userId()));
        return true;
    }

    private void updateActiveTenantLocal(String token, String activeTenantId) {
        IndexedSession existing = localSessions.get(token);
        if (existing == null) {
            return;
        }
        UserSessionResponse session = existing.response();
        if (activeTenantId.equals(session.activeTenantId())) {
            return;
        }
        localSessions.put(token, new IndexedSession(new UserSessionResponse(
                session.sessionId(),
                session.username(),
                session.tenantId(),
                activeTenantId,
                session.clientIp(),
                session.loginLocation(),
                session.device(),
                session.issuedAt(),
                session.expiresAt(),
                session.lastAccessAt(),
                session.active(),
                session.currentSession()
        ), existing.userId()));
    }

    private Page localPage(int page, int size) {
        int effectivePage = Math.max(page, 0);
        int effectiveSize = Math.max(size, 0);
        long start = (long) effectivePage * effectiveSize;
        List<IndexedSession> sessions = localSessions.values().stream()
                .sorted(Comparator.comparingLong((IndexedSession session) -> instantEpoch(session.response().lastAccessAt())).reversed())
                .skip(start)
                .limit(effectiveSize)
                .toList();
        return new Page(localSessions.size(), sessions);
    }

    private Page localUserPage(Long userId, int page, int size) {
        int effectivePage = Math.max(page, 0);
        int effectiveSize = Math.max(size, 0);
        long start = (long) effectivePage * effectiveSize;
        List<IndexedSession> matched = localSessions.values().stream()
                .filter(session -> userId.equals(session.userId()))
                .sorted(Comparator.comparingLong((IndexedSession session) -> instantEpoch(session.response().lastAccessAt())).reversed())
                .toList();
        List<IndexedSession> sessions = matched.stream()
                .skip(start)
                .limit(effectiveSize)
                .toList();
        return new Page(matched.size(), sessions);
    }

    private long instantEpoch(Instant instant) {
        return instant == null ? 0L : instant.toEpochMilli();
    }

    private Duration indexTtl() {
        Duration sessionTtl = securityProperties.sessionTtl();
        if (sessionTtl == null || sessionTtl.isNegative() || sessionTtl.isZero()) {
            return Duration.ofDays(7);
        }
        return sessionTtl.plusHours(1);
    }

    private String allSessionsKey() {
        return securityProperties.resolvedRedis().resolvedNamespacePrefix() + ALL_SESSIONS_KEY;
    }

    private String userSessionsKey(Long userId) {
        return userSessionsKey(String.valueOf(userId));
    }

    private String userSessionsKey(String userId) {
        return securityProperties.resolvedRedis().resolvedNamespacePrefix() + USER_SESSIONS_PREFIX + userId;
    }

    private String tenantSessionsKey(String tenantId) {
        return securityProperties.resolvedRedis().resolvedNamespacePrefix()
                + TENANT_SESSIONS_PREFIX + tenantId.trim();
    }

    private String sessionMetaKey(String token) {
        return securityProperties.resolvedRedis().resolvedNamespacePrefix() + SESSION_META_PREFIX + token;
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

    public record IndexedSession(UserSessionResponse response, Long userId) {
    }
}
