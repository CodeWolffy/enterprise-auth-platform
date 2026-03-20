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
import org.springframework.lang.Nullable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.security.redis", name = "session-enabled", havingValue = "true")
public class RedisSessionStore implements SessionStore {

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
        String sessionKey = sessionKey(session.sessionId());
        Duration ttl = remainingTtl(session.expiresAt());
        if (redissonStorageEnabled()) {
            RBucket<String> sessionBucket = redissonClient.getBucket(sessionKey);
            sessionBucket.set(payload, ttl);
            RScoredSortedSet<String> userSessions = redissonClient.getScoredSortedSet(userSessionsKey(session.userId()));
            userSessions.add(session.issuedAt().toEpochMilli(), session.sessionId());
            return;
        }
        redisTemplate.opsForValue().set(sessionKey, payload, ttl);
        redisTemplate.opsForZSet().add(userSessionsKey(session.userId()), session.sessionId(), session.issuedAt().toEpochMilli());
    }

    @Override
    public Optional<UserSession> findBySessionId(String sessionId) {
        String payload;
        if (redissonStorageEnabled()) {
            RBucket<String> bucket = redissonClient.getBucket(sessionKey(sessionId));
            payload = bucket.get();
        } else {
            payload = redisTemplate.opsForValue().get(sessionKey(sessionId));
        }
        if (payload == null) {
            return Optional.empty();
        }
        return Optional.of(readSession(payload));
    }

    @Override
    public List<UserSession> findByUserId(Long userId) {
        List<String> sessionIds;
        if (redissonStorageEnabled()) {
            RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(userSessionsKey(userId));
            sessionIds = new ArrayList<>(zset.valueRangeReversed(0, true, Double.MAX_VALUE, true));
            if (sessionIds.size() > 200) {
                sessionIds = sessionIds.subList(0, 200);
            }
        } else {
            var result = redisTemplate.opsForZSet().reverseRange(userSessionsKey(userId), 0, 200);
            sessionIds = result == null ? List.of() : new ArrayList<>(result);
        }
        if (sessionIds == null || sessionIds.isEmpty()) {
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
            if (redissonStorageEnabled()) {
                redissonClient.getScoredSortedSet(userSessionsKey(userId)).removeAll(staleIds);
            } else {
                redisTemplate.opsForZSet().remove(userSessionsKey(userId), staleIds.toArray());
            }
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
        return redisProperties.resolvedKeyPrefix() + "session:" + sessionId;
    }

    private String userSessionsKey(Long userId) {
        return redisProperties.resolvedKeyPrefix() + "user-sessions:" + userId;
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
            throw new IllegalStateException("序列化会话失败", ex);
        }
    }

    private UserSession readSession(String payload) {
        try {
            return objectMapper.readValue(payload, UserSession.class);
        } catch (Exception ex) {
            throw new IllegalStateException("反序列化会话失败", ex);
        }
    }
}
