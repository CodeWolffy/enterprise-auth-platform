package com.enterprise.auth.platform.auth.store;

import com.enterprise.auth.platform.auth.model.UserSession;
import com.enterprise.auth.platform.config.SecurityRedisProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.redisson.api.RBucket;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.security.redis", name = "session-enabled", havingValue = "true")
public class RedisSessionStore implements SessionStore {

    private static final int MAX_USER_SESSIONS = 200;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SecurityRedisProperties redisProperties;
    private final RedissonClient redissonClient;

    public RedisSessionStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            SecurityRedisProperties redisProperties,
            @Nullable RedissonClient redissonClient
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.redisProperties = redisProperties;
        this.redissonClient = redissonClient;
    }

    @Override
    public void save(UserSession session) {
        String payload = writeSession(session);
        Duration ttl = remainingTtl(session.expiresAt());
        String sessionKey = sessionKey(session.sessionId());
        String userSessionsKey = userSessionsKey(session.userId());

        if (redissonStorageEnabled()) {
            try {
                RBucket<String> sessionBucket = redissonClient.getBucket(sessionKey);
                sessionBucket.set(payload, ttl);
                RScoredSortedSet<String> userSessions = redissonClient.getScoredSortedSet(userSessionsKey);
                userSessions.add(session.issuedAt().toEpochMilli(), session.sessionId());
                extendIndexTtlRedisson(userSessions, ttl);
                return;
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to save session via Redisson", ex);
            }
        }

        try {
            redisTemplate.opsForValue().set(sessionKey, payload, ttl);
            redisTemplate.opsForZSet().add(userSessionsKey, session.sessionId(), session.issuedAt().toEpochMilli());
            extendIndexTtlRedisTemplate(userSessionsKey, ttl);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to save session via RedisTemplate", ex);
        }
    }

    @Override
    public Optional<UserSession> findBySessionId(String sessionId) {
        String sessionKey = sessionKey(sessionId);
        String payload;

        if (redissonStorageEnabled()) {
            try {
                RBucket<String> sessionBucket = redissonClient.getBucket(sessionKey);
                payload = sessionBucket.get();
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to fetch session via Redisson", ex);
            }
        } else {
            try {
                payload = redisTemplate.opsForValue().get(sessionKey);
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to fetch session via RedisTemplate", ex);
            }
        }

        if (payload == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(readSession(payload));
        } catch (Exception ex) {
            deleteSessionKey(sessionKey);
            return Optional.empty();
        }
    }

    @Override
    public List<UserSession> findByUserId(Long userId) {
        List<String> sessionIds;
        String userSessionsKey = userSessionsKey(userId);

        if (redissonStorageEnabled()) {
            try {
                RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(userSessionsKey);
                sessionIds = new ArrayList<>(zset.valueRangeReversed(0, true, Double.MAX_VALUE, true));
                if (sessionIds.size() > MAX_USER_SESSIONS) {
                    sessionIds = sessionIds.subList(0, MAX_USER_SESSIONS);
                }
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to fetch user sessions via Redisson", ex);
            }
        } else {
            try {
                var result = redisTemplate.opsForZSet().reverseRange(userSessionsKey, 0, MAX_USER_SESSIONS - 1);
                sessionIds = result == null ? List.of() : new ArrayList<>(result);
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to fetch user sessions via RedisTemplate", ex);
            }
        }

        if (sessionIds.isEmpty()) {
            return List.of();
        }

        List<String> staleIds = new ArrayList<>();
        List<UserSession> sessions = new ArrayList<>();
        for (String sessionId : sessionIds) {
            Optional<UserSession> session = findBySessionId(sessionId);
            if (session.isPresent()) {
                sessions.add(session.get());
            } else {
                staleIds.add(sessionId);
            }
        }

        if (!staleIds.isEmpty()) {
            removeUserSessionIndex(userSessionsKey, staleIds);
        }
        return sessions;
    }

    @Override
    public void deactivate(String sessionId) {
        findBySessionId(sessionId).ifPresent(session -> save(session.deactivate(Instant.now())));
    }

    @Override
    public void touch(String sessionId) {
        findBySessionId(sessionId).ifPresent(session -> save(session.touch(Instant.now())));
    }

    private String sessionKey(String sessionId) {
        return redisProperties.resolvedNamespacePrefix() + "session:by-id:" + sessionId;
    }

    private String userSessionsKey(Long userId) {
        return redisProperties.resolvedNamespacePrefix() + "session:user-index:" + userId;
    }

    private Duration remainingTtl(Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        return ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(1) : ttl;
    }

    private boolean redissonStorageEnabled() {
        return redisProperties.redissonEnabled() && redissonClient != null;
    }

    private String writeSession(UserSession session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize session", ex);
        }
    }

    private UserSession readSession(String payload) {
        try {
            return objectMapper.readValue(payload, UserSession.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialize session", ex);
        }
    }

    private void deleteSessionKey(String key) {
        if (redissonStorageEnabled()) {
            try {
                redissonClient.getBucket(key).delete();
                return;
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to delete invalid session key via Redisson", ex);
            }
        }
        try {
            redisTemplate.delete(key);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to delete invalid session key via RedisTemplate", ex);
        }
    }

    private void removeUserSessionIndex(String userSessionsKey, List<String> staleIds) {
        if (redissonStorageEnabled()) {
            try {
                redissonClient.getScoredSortedSet(userSessionsKey).removeAll(staleIds);
                return;
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to cleanup stale user session index via Redisson", ex);
            }
        }
        try {
            redisTemplate.opsForZSet().remove(userSessionsKey, staleIds.toArray());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to cleanup stale user session index via RedisTemplate", ex);
        }
    }

    private void extendIndexTtlRedisTemplate(String userSessionsKey, Duration ttl) {
        Long currentSeconds = redisTemplate.getExpire(userSessionsKey);
        long targetSeconds = Math.max(1L, ttl.getSeconds());
        if (currentSeconds == null || currentSeconds < 0 || targetSeconds > currentSeconds) {
            redisTemplate.expire(userSessionsKey, Duration.ofSeconds(targetSeconds));
        }
    }

    private void extendIndexTtlRedisson(RScoredSortedSet<String> userSessions, Duration ttl) {
        long currentMs = userSessions.remainTimeToLive();
        long targetMs = Math.max(1000L, ttl.toMillis());
        if (currentMs < 0 || targetMs > currentMs) {
            userSessions.expire(Duration.ofMillis(targetMs));
        }
    }
}
