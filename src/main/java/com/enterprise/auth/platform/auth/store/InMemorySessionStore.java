package com.enterprise.auth.platform.auth.store;

import com.enterprise.auth.platform.auth.model.UserSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.security.redis", name = "session-enabled", havingValue = "false", matchIfMissing = true)
public class InMemorySessionStore implements SessionStore {

    private final ConcurrentHashMap<String, UserSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void save(UserSession session) {
        sessions.put(session.sessionId(), session);
    }

    @Override
    public Optional<UserSession> findBySessionId(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public List<UserSession> findByUserId(Long userId) {
        return sessions.values().stream()
                .filter(session -> session.userId().equals(userId))
                .sorted((left, right) -> right.issuedAt().compareTo(left.issuedAt()))
                .toList();
    }

    @Override
    public void deactivate(String sessionId) {
        sessions.computeIfPresent(sessionId, (key, session) -> session.deactivate(Instant.now()));
    }

    @Override
    public void touch(String sessionId) {
        sessions.computeIfPresent(sessionId, (key, session) -> session.touch(Instant.now()));
    }
}

