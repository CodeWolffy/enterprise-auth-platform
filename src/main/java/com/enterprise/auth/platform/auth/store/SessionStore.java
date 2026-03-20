package com.enterprise.auth.platform.auth.store;

import com.enterprise.auth.platform.auth.model.UserSession;
import java.util.List;
import java.util.Optional;

public interface SessionStore {

    void save(UserSession session);

    Optional<UserSession> findBySessionId(String sessionId);

    List<UserSession> findByUserId(Long userId);

    void deactivate(String sessionId);

    void touch(String sessionId);
}

