package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.modules.auth.interfaces.UserSessionResponse;
import com.enterprise.auth.platform.modules.auth.infrastructure.SecurityProperties;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SessionIndexService {

    private static final Logger log = LoggerFactory.getLogger(SessionIndexService.class);
    private static final String ALL_SESSIONS_KEY = "session:index:all";
    private static final String USER_SESSIONS_PREFIX = "session:index:user:";
    private static final String SESSION_META_PREFIX = "session:meta:";

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
                    issuedAt,
                    expiresAt,
                    issuedAt,
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
            redisTemplate.expire(allSessionsKey(), indexTtl());
            redisTemplate.expire(userSessionsKey(userId), indexTtl());
        } catch (RuntimeException ex) {
            log.warn("注册会话索引失败。token={}，error={}", token, ex.getMessage());
        }
    }

    public boolean touch(String token, long lastAccessAt) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        if (useLocalIndex()) {
            return touchLocal(token, lastAccessAt);
        }
        try {
            String sessionKey = sessionMetaKey(token);
            Object userId = redisTemplate.opsForHash().get(sessionKey, "userId");
            if (userId == null) {
                return false;
            }
            redisTemplate.opsForHash().put(sessionKey, "lastAccessAt", String.valueOf(lastAccessAt));
            redisTemplate.expire(sessionKey, indexTtl());
            redisTemplate.opsForZSet().add(allSessionsKey(), token, lastAccessAt);
            redisTemplate.opsForZSet().add(userSessionsKey(String.valueOf(userId)), token, lastAccessAt);
            return true;
        } catch (RuntimeException ex) {
            log.debug("刷新会话索引失败。token={}，error={}", token, ex.getMessage());
            return false;
        }
    }

    public void updateActiveTenant(String token, String activeTenantId) {
        if (!StringUtils.hasText(token) || !StringUtils.hasText(activeTenantId)) {
            return;
        }
        if (useLocalIndex()) {
            updateActiveTenantLocal(token, activeTenantId);
            return;
        }
        try {
            String sessionKey = sessionMetaKey(token);
            redisTemplate.opsForHash().put(sessionKey, "activeTenantId", activeTenantId);
            redisTemplate.expire(sessionKey, indexTtl());
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
            redisTemplate.opsForZSet().remove(allSessionsKey(), token);
            if (userId != null) {
                redisTemplate.opsForZSet().remove(userSessionsKey(String.valueOf(userId)), token);
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
        Optional<Page> pageResult = page(page, size);
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
      if (tokens == null) {
        return Optional.of(new Page(total, List.of()));
      }
      List<IndexedSession> sessions = tokens.stream()
          .map(this::read)
          .flatMap(Optional::stream)
          .toList();
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
      if (tokens == null) {
        return Optional.of(new Page(total, List.of()));
      }
      List<IndexedSession> sessions = tokens.stream()
          .map(this::read)
          .flatMap(Optional::stream)
          .filter(session -> userId.equals(session.userId()))
          .toList();
      return Optional.of(new Page(total, sessions));
    } catch (RuntimeException ex) {
      log.warn("读取用户会话索引失败。userId={}，error={}", userId, ex.getMessage());
      return Optional.empty();
    }
  }

  public record Page(long total, List<IndexedSession> records) {}

    private boolean visibleSession(IndexedSession session, String tenantId, Optional<Set<Long>> visibleUserIds) {
        if (session == null || session.response() == null) {
            return false;
        }
        if (!tenantId.equals(session.response().tenantId())) {
            return false;
        }
        return visibleUserIds.map(userIds -> userIds.contains(session.userId())).orElse(true);
    }

    private Optional<IndexedSession> read(String token) {
        Map<Object, Object> meta = redisTemplate.opsForHash().entries(sessionMetaKey(token));
        if (meta.isEmpty()) {
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
                longValue(meta.get("issuedAt"), 0L),
                longValue(meta.get("expiresAt"), 0L),
                longValue(meta.get("lastAccessAt"), 0L),
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
                lastAccessAt,
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
                .sorted(Comparator.comparingLong((IndexedSession session) -> session.response().lastAccessAt()).reversed())
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
                .sorted(Comparator.comparingLong((IndexedSession session) -> session.response().lastAccessAt()).reversed())
                .toList();
        List<IndexedSession> sessions = matched.stream()
                .skip(start)
                .limit(effectiveSize)
                .toList();
        return new Page(matched.size(), sessions);
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
