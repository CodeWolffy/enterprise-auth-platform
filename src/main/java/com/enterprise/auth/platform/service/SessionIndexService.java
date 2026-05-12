package com.enterprise.auth.platform.service;

import com.enterprise.auth.platform.dto.resp.UserSessionResponse;
import com.enterprise.auth.platform.config.SecurityProperties;
import com.enterprise.auth.platform.config.SecurityRedisProperties;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final SecurityRedisProperties redisProperties;
    private final SecurityProperties securityProperties;

    public SessionIndexService(
            StringRedisTemplate redisTemplate,
            SecurityRedisProperties redisProperties,
            SecurityProperties securityProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.redisProperties = redisProperties;
        this.securityProperties = securityProperties;
    }

    public void register(
            String token,
            Long userId,
            String username,
            String tenantId,
            String clientIp,
            String device,
            long issuedAt,
            long expiresAt
    ) {
        if (!StringUtils.hasText(token) || userId == null) {
            return;
        }
        try {
            String sessionKey = sessionMetaKey(token);
            Map<String, String> meta = Map.of(
                    "sessionId", token,
                    "userId", String.valueOf(userId),
                    "username", valueOrEmpty(username),
                    "tenantId", valueOrEmpty(tenantId),
                    "clientIp", valueOrEmpty(clientIp),
                    "device", valueOrEmpty(device),
                    "issuedAt", String.valueOf(issuedAt),
                    "expiresAt", String.valueOf(expiresAt),
                    "lastAccessAt", String.valueOf(issuedAt)
            );
            redisTemplate.opsForHash().putAll(sessionKey, meta);
            redisTemplate.expire(sessionKey, indexTtl());
            redisTemplate.opsForZSet().add(allSessionsKey(), token, issuedAt);
            redisTemplate.opsForZSet().add(userSessionsKey(userId), token, issuedAt);
            redisTemplate.expire(allSessionsKey(), indexTtl());
            redisTemplate.expire(userSessionsKey(userId), indexTtl());
        } catch (RuntimeException ex) {
            log.warn("Failed to register session index. token={}, error={}", token, ex.getMessage());
        }
    }

    public boolean touch(String token, long lastAccessAt) {
        if (!StringUtils.hasText(token)) {
            return false;
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
            log.debug("Failed to touch session index. token={}, error={}", token, ex.getMessage());
            return false;
        }
    }

    public void remove(String token) {
        if (!StringUtils.hasText(token)) {
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
            log.debug("Failed to remove session index. token={}, error={}", token, ex.getMessage());
        }
    }

    public void removeUser(Long userId) {
        if (userId == null) {
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
            log.debug("Failed to remove user session index. userId={}, error={}", userId, ex.getMessage());
        }
    }

  public Optional<List<IndexedSession>> recent(int limit) {
    return page(0, limit).map(Page::records);
  }

  public Optional<Page> page(int page, int size) {
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
      log.warn("Failed to read session index. error={}", ex.getMessage());
      return Optional.empty();
    }
  }

  public record Page(long total, List<IndexedSession> records) {}

    private Optional<IndexedSession> read(String token) {
        Map<Object, Object> meta = redisTemplate.opsForHash().entries(sessionMetaKey(token));
        if (meta.isEmpty()) {
            redisTemplate.opsForZSet().remove(allSessionsKey(), token);
            return Optional.empty();
        }
        Long userId = longValue(meta.get("userId"), null);
        String username = stringValue(meta.get("username"));
        String tenantId = stringValue(meta.get("tenantId"));
        if (userId == null || !StringUtils.hasText(username) || !StringUtils.hasText(tenantId)) {
            remove(token);
            return Optional.empty();
        }
        UserSessionResponse response = new UserSessionResponse(
                token,
                username,
                tenantId,
                stringValue(meta.get("clientIp")),
                stringValue(meta.get("device")),
                longValue(meta.get("issuedAt"), 0L),
                longValue(meta.get("expiresAt"), 0L),
                longValue(meta.get("lastAccessAt"), 0L),
                true,
                false
        );
        return Optional.of(new IndexedSession(response, userId));
    }

    private Duration indexTtl() {
        Duration sessionTtl = securityProperties.sessionTtl();
        if (sessionTtl == null || sessionTtl.isNegative() || sessionTtl.isZero()) {
            return Duration.ofDays(7);
        }
        return sessionTtl.plusHours(1);
    }

    private String allSessionsKey() {
        return redisProperties.resolvedNamespacePrefix() + ALL_SESSIONS_KEY;
    }

    private String userSessionsKey(Long userId) {
        return userSessionsKey(String.valueOf(userId));
    }

    private String userSessionsKey(String userId) {
        return redisProperties.resolvedNamespacePrefix() + USER_SESSIONS_PREFIX + userId;
    }

    private String sessionMetaKey(String token) {
        return redisProperties.resolvedNamespacePrefix() + SESSION_META_PREFIX + token;
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
