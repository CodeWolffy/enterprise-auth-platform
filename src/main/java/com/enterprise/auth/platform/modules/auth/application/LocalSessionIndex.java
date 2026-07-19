package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.modules.auth.infrastructure.SecurityProperties;
import com.enterprise.auth.platform.modules.auth.interfaces.UserSessionResponse;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.util.StringUtils;

/**
 * In-process fallback used when the shadow Redis session index is disabled.
 * This is intentionally isolated from Redis key and pipeline code.
 */
final class LocalSessionIndex {

    private final SecurityProperties securityProperties;
    private final ConcurrentMap<String, SessionIndexService.IndexedSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> managementTokens = new ConcurrentHashMap<>();

    LocalSessionIndex(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
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
        SessionIndexService.IndexedSession existing = sessions.get(token);
        String managementId = existing == null ? UUID.randomUUID().toString() : existing.response().sessionId();
        sessions.put(token, new SessionIndexService.IndexedSession(
                new UserSessionResponse(
                        managementId,
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
                ),
                userId,
                token
        ));
        managementTokens.put(managementId, token);
    }

    boolean touch(String token, long lastAccessAt) {
        SessionIndexService.IndexedSession existing = sessions.get(token);
        if (existing == null) {
            return false;
        }
        UserSessionResponse session = existing.response();
        long previous = session.lastAccessAt() == null ? 0L : session.lastAccessAt().toEpochMilli();
        long throttleMs = securityProperties.resolvedRedis().resolvedSessionTouchThrottle().toMillis();
        if (lastAccessAt - previous < throttleMs) {
            return true;
        }
        sessions.put(token, new SessionIndexService.IndexedSession(
                withLastAccess(session, lastAccessAt), existing.userId(), token));
        return true;
    }

    void updateActiveTenant(String token, String activeTenantId) {
        SessionIndexService.IndexedSession existing = sessions.get(token);
        if (existing == null || activeTenantId.equals(existing.response().activeTenantId())) {
            return;
        }
        UserSessionResponse session = existing.response();
        sessions.put(token, new SessionIndexService.IndexedSession(
                new UserSessionResponse(
                        session.sessionId(), session.username(), session.tenantId(), activeTenantId,
                        session.clientIp(), session.loginLocation(), session.device(), session.issuedAt(),
                        session.expiresAt(), session.lastAccessAt(), session.active(), session.currentSession()),
                existing.userId(), token));
    }

    void remove(String token) {
        SessionIndexService.IndexedSession removed = sessions.remove(token);
        if (removed != null) {
            managementTokens.remove(removed.response().sessionId(), token);
        }
    }

    void removeUser(Long userId) {
        sessions.entrySet().removeIf(entry -> {
            if (!userId.equals(entry.getValue().userId())) {
                return false;
            }
            managementTokens.remove(entry.getValue().response().sessionId(), entry.getKey());
            return true;
        });
    }

    Optional<String> managementId(String token) {
        SessionIndexService.IndexedSession indexed = sessions.get(token);
        return indexed == null ? Optional.empty() : Optional.of(indexed.response().sessionId());
    }

    Optional<String> resolveToken(String managementId) {
        String token = managementTokens.get(managementId);
        SessionIndexService.IndexedSession indexed = token == null ? null : sessions.get(token);
        return indexed != null && managementId.equals(indexed.response().sessionId())
                ? Optional.of(token)
                : Optional.empty();
    }

    Optional<Long> count() {
        return Optional.of((long) sessions.size());
    }

    Optional<Long> countVisible(String tenantId, Optional<Set<Long>> visibleUserIds) {
        return Optional.of(sessions.values().stream()
                .filter(session -> visibleSession(session, tenantId, visibleUserIds))
                .count());
    }

    Optional<SessionIndexService.Page> pageVisible(
            String tenantId,
            Optional<Set<Long>> visibleUserIds,
            int page,
            int size
    ) {
        if (visibleUserIds.isEmpty()) {
            return pageTenant(tenantId, page, size);
        }
        List<SessionIndexService.IndexedSession> matched = sessions.values().stream()
                .filter(session -> visibleSession(session, tenantId, visibleUserIds))
                .sorted(Comparator.comparingLong((SessionIndexService.IndexedSession session) ->
                        instantEpoch(session.response().lastAccessAt())).reversed())
                .toList();
        int effectivePage = Math.max(page, 0);
        int effectiveSize = Math.max(size, 1);
        long start = (long) effectivePage * effectiveSize;
        return Optional.of(new SessionIndexService.Page(
                matched.size(), matched.stream().skip(start).limit(effectiveSize).toList()));
    }

    Optional<SessionIndexService.Page> page(int page, int size) {
        return Optional.of(localPage(page, size));
    }

    Optional<SessionIndexService.Page> pageUser(Long userId, int page, int size) {
        if (userId == null) {
            return Optional.of(new SessionIndexService.Page(0, List.of()));
        }
        return Optional.of(localUserPage(userId, page, size));
    }

    Optional<SessionIndexService.Page> pageTenant(String tenantId, int page, int size) {
        if (!StringUtils.hasText(tenantId)) {
            return Optional.of(new SessionIndexService.Page(0, List.of()));
        }
        int effectivePage = Math.max(page, 0);
        int effectiveSize = Math.max(size, 1);
        List<SessionIndexService.IndexedSession> matched = sessions.values().stream()
                .filter(session -> tenantId.equals(session.response().tenantId()))
                .sorted(Comparator.comparingLong((SessionIndexService.IndexedSession session) ->
                        instantEpoch(session.response().lastAccessAt())).reversed())
                .toList();
        long start = (long) effectivePage * effectiveSize;
        return Optional.of(new SessionIndexService.Page(
                matched.size(), matched.stream().skip(start).limit(effectiveSize).toList()));
    }

    private SessionIndexService.Page localPage(int page, int size) {
        int effectivePage = Math.max(page, 0);
        int effectiveSize = Math.max(size, 0);
        long start = (long) effectivePage * effectiveSize;
        List<SessionIndexService.IndexedSession> result = sessions.values().stream()
                .sorted(Comparator.comparingLong((SessionIndexService.IndexedSession session) ->
                        instantEpoch(session.response().lastAccessAt())).reversed())
                .skip(start)
                .limit(effectiveSize)
                .toList();
        return new SessionIndexService.Page(sessions.size(), result);
    }

    private SessionIndexService.Page localUserPage(Long userId, int page, int size) {
        int effectivePage = Math.max(page, 0);
        int effectiveSize = Math.max(size, 0);
        long start = (long) effectivePage * effectiveSize;
        List<SessionIndexService.IndexedSession> matched = sessions.values().stream()
                .filter(session -> userId.equals(session.userId()))
                .sorted(Comparator.comparingLong((SessionIndexService.IndexedSession session) ->
                        instantEpoch(session.response().lastAccessAt())).reversed())
                .toList();
        return new SessionIndexService.Page(
                matched.size(), matched.stream().skip(start).limit(effectiveSize).toList());
    }

    private boolean visibleSession(
            SessionIndexService.IndexedSession session,
            String tenantId,
            Optional<Set<Long>> visibleUserIds
    ) {
        return session != null
                && session.response() != null
                && tenantId.equals(session.response().tenantId())
                && visibleUserIds.map(ids -> ids.contains(session.userId())).orElse(true);
    }

    private UserSessionResponse withLastAccess(UserSessionResponse session, long lastAccessAt) {
        return new UserSessionResponse(
                session.sessionId(), session.username(), session.tenantId(), session.activeTenantId(),
                session.clientIp(), session.loginLocation(), session.device(), session.issuedAt(),
                session.expiresAt(), TimeSupport.fromEpochMilli(lastAccessAt), session.active(),
                session.currentSession());
    }

    private long instantEpoch(Instant instant) {
        return instant == null ? 0L : instant.toEpochMilli();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
