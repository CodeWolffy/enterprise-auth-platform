package com.enterprise.auth.platform.modules.auth.application;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.modules.auth.application.DataScopeService;
import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.modules.auth.application.PlatformAdminSupport;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.auth.interfaces.UserSessionResponse;
import com.enterprise.auth.platform.modules.auth.application.SessionIndexService;
import com.enterprise.auth.platform.modules.auth.application.SessionIndexService.Page;
import com.enterprise.auth.platform.modules.log.application.LogPublisher;
import com.enterprise.auth.platform.common.notification.NotificationScenarioPort;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SessionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(SessionApplicationService.class);
    private final DataScopeService dataScopeService;
    private final LogPublisher logPublisher;
    private final PlatformAdminSupport platformAdminSupport;
    private final SessionIndexService sessionIndexService;
    private final NotificationScenarioPort notificationScenarioPublisher;

    public SessionApplicationService(
            DataScopeService dataScopeService,
            LogPublisher logPublisher,
            PlatformAdminSupport platformAdminSupport,
            SessionIndexService sessionIndexService,
            NotificationScenarioPort notificationScenarioPublisher
    ) {
        this.dataScopeService = dataScopeService;
        this.logPublisher = logPublisher;
        this.platformAdminSupport = platformAdminSupport;
        this.sessionIndexService = sessionIndexService;
        this.notificationScenarioPublisher = notificationScenarioPublisher;
    }

    public void logout(String sessionId, String username, String tenantId) {
        String managementId = sessionIndexService.managementId(sessionId).orElse("");
        Map<String, Object> payload = sessionAuditPayload(managementId, sessionId);
        StpUtil.logoutByTokenValue(sessionId);
        sessionIndexService.remove(sessionId);
        logPublisher.publish("LOGOUT", username, tenantId, payload);
    }

    public List<UserSessionResponse> sessions(UserAccount currentUser, String scope, String currentToken) {
        return sessions(currentUser, scope, currentToken, null, null).records();
    }

    public PageResult<UserSessionResponse> sessions(
            UserAccount currentUser,
            String scope,
            String currentToken,
            Integer page,
            Integer size
    ) {
        boolean allTenant = "all".equals(scope)
                && (currentUser.permissions().contains(PermissionCodes.SESSION_PAGE) || platformAdminSupport.isPlatformSuperAdmin(currentUser));
        if (allTenant) {
            return allSessions(currentUser, currentToken, page, size);
        }
        List<UserSessionResponse> own = ownSessions(currentUser, currentToken);
        return PageResult.of(own.size(), 1, own.size(), own);
    }

    public void forceOffline(UserAccount currentUser, String sessionId) {
        String token = sessionIndexService.resolveToken(sessionId)
                .orElseThrow(() -> new BusinessException("SESSION_NOT_FOUND", "会话不存在"));
        Long targetUserId = resolveLoginId(token);
        String targetTenantId = sessionAttribute(token, "tenantId");
        if (!StringUtils.hasText(targetTenantId)) {
            sessionIndexService.remove(token);
            throw new BusinessException("SESSION_INVALID", "会话元数据不完整");
        }
        Map<String, Object> payload = sessionAuditPayload(sessionId, token);
        boolean sameOwner = currentUser.id().equals(targetUserId);
        boolean canManage = currentUser.permissions().contains(PermissionCodes.SESSION_KICK);
        boolean platformAdmin = platformAdminSupport.isPlatformSuperAdmin(currentUser);
        boolean sameTenantOrPlatform = currentUser.tenantId().equals(targetTenantId) || platformAdmin;
        boolean visibleTarget = platformAdmin || dataScopeService.canAccessUser(currentUser.tenantId(), targetUserId);
        if (!sameOwner && (!canManage || !sameTenantOrPlatform || !visibleTarget)) {
            throw new BusinessException("ACCESS_DENIED", "无权操作此会话");
        }
        StpUtil.kickoutByTokenValue(token);
        sessionIndexService.remove(token);
        payload.put("targetUserId", targetUserId);
        logPublisher.publish("SESSION_FORCED_OFFLINE", currentUser.username(), currentUser.tenantId(), payload);
        notificationScenarioPublisher.sessionForcedOffline(targetTenantId, targetUserId, currentUser.username(), payload);
    }

    private List<UserSessionResponse> ownSessions(UserAccount currentUser, String currentToken) {
        List<String> tokens = StpUtil.getTokenValueListByLoginId(currentUser.id());
        Map<String, SessionIndexService.IndexedSession> indexedByToken = sessionIndexService
                .pageUser(currentUser.id(), 0, Math.max(tokens.size(), 1))
                .map(Page::records)
                .orElseGet(List::of)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        SessionIndexService.IndexedSession::token,
                        session -> session,
                        (left, right) -> left
                ));
        return tokens.stream()
                .map(token -> Optional.ofNullable(indexedByToken.get(token))
                        .map(indexed -> withCurrentSession(indexed, currentToken))
                        .or(() -> safeSessionResponse(token, currentToken)))
                .flatMap(Optional::stream)
                .filter(UserSessionResponse::active)
                .filter(session -> session.expiresAt() == null
                        || session.expiresAt().toEpochMilli() >= Instant.now().toEpochMilli())
                .sorted((a, b) -> Long.compare(instantEpoch(b.lastAccessAt()), instantEpoch(a.lastAccessAt())))
                .toList();
    }

    private PageResult<UserSessionResponse> allSessions(UserAccount currentUser, String currentToken, Integer page, Integer size) {
        boolean platformAdmin = platformAdminSupport.isPlatformSuperAdmin(currentUser);
        int effectivePage = page != null && page > 0 ? page : 1;
        int effectiveSize = size != null && size > 0 ? Math.min(size, 100) : 10;
        // 请求内只算一次可见用户集合，避免循环 N 次 DataScope 查库
        final Optional<Set<Long>> visibleUserIds = platformAdmin
                ? Optional.empty()
                : dataScopeService.visibleUserIds(currentUser.tenantId());
        Page indexPage = platformAdmin
                ? sessionIndexService.page(effectivePage - 1, effectiveSize).orElseGet(() -> new Page(0, List.of()))
                : sessionIndexService.pageVisible(currentUser.tenantId(), visibleUserIds, effectivePage - 1, effectiveSize)
                        .orElseGet(() -> new Page(0, List.of()));
        if (indexPage.records().isEmpty() && effectivePage == 1) {
            List<UserSessionResponse> own = ownSessions(currentUser, currentToken).stream()
                    .limit(effectiveSize)
                    .toList();
            return PageResult.of(Math.max(indexPage.total(), own.size()), 1, effectiveSize, own);
        }
        List<UserSessionResponse> sessions = indexPage.records().stream()
                .filter(entry -> entry != null)
                .filter(entry -> tenantVisible(currentUser, entry.response()))
                .filter(entry -> platformAdmin || canAccessSessionUser(currentUser, entry, visibleUserIds))
                .map(entry -> withCurrentSession(entry, currentToken))
                // 索引 TTL 承担陈旧数据清理；列表页不再逐条回查 Sa-Token 活跃状态
                .filter(session -> session.expiresAt() == null
                        || session.expiresAt().toEpochMilli() >= Instant.now().toEpochMilli())
                .sorted((a, b) -> Long.compare(instantEpoch(b.lastAccessAt()), instantEpoch(a.lastAccessAt())))
                .toList();
        boolean hasCurrentSession = sessions.stream().anyMatch(UserSessionResponse::currentSession);
        if (!hasCurrentSession) {
            List<UserSessionResponse> ownSessions = ownSessions(currentUser, currentToken);
            if (!ownSessions.isEmpty()) {
                sessions = java.util.stream.Stream.concat(ownSessions.stream(), sessions.stream())
                        .collect(java.util.stream.Collectors.toMap(
                                UserSessionResponse::sessionId,
                                session -> session,
                                (left, right) -> left,
                                java.util.LinkedHashMap::new
                        ))
                        .values()
                        .stream()
                        .sorted((a, b) -> Long.compare(instantEpoch(b.lastAccessAt()), instantEpoch(a.lastAccessAt())))
                        .limit(effectiveSize)
                        .toList();
            }
        }
        return PageResult.of(indexPage.total(), effectivePage, effectiveSize, sessions);
    }

    private boolean tenantVisible(UserAccount currentUser, UserSessionResponse session) {
        if (platformAdminSupport.isPlatformSuperAdmin(currentUser)) {
            return true;
        }
        return currentUser.tenantId().equals(session.tenantId());
    }

    private Optional<UserSessionResponse> safeSessionResponse(String token, String currentToken) {
        try {
            return Optional.of(toSessionResponse(token, currentToken));
        } catch (Exception e) {
            log.debug("构建会话列表时跳过无效会话。token={}，error={}", token, e.getMessage());
            sessionIndexService.remove(token);
            return Optional.empty();
        }
    }

    private boolean canAccessSessionUser(
            UserAccount currentUser,
            SessionIndexService.IndexedSession entry,
            Optional<Set<Long>> visibleUserIds
    ) {
        if (currentUser.id().equals(entry.userId())) {
            return true;
        }
        // empty Optional = ALL scope；empty Set = NONE
        return visibleUserIds.map(userIds -> userIds.contains(entry.userId())).orElse(true);
    }

    private boolean canAccessSessionUser(UserAccount currentUser, SessionIndexService.IndexedSession entry) {
        if (currentUser.id().equals(entry.userId())) {
            return true;
        }
        try {
            return dataScopeService.canAccessUser(entry.response().tenantId(), entry.userId());
        } catch (Exception ex) {
            log.debug("解析会话用户数据权限失败。tenantId={}，userId={}，error={}",
                    entry.response().tenantId(), entry.userId(), ex.getMessage());
            return false;
        }
    }

    private UserSessionResponse withCurrentSession(SessionIndexService.IndexedSession indexedSession, String currentToken) {
        UserSessionResponse session = indexedSession.response();
        return new UserSessionResponse(
                session.sessionId(),
                session.username(),
                session.tenantId(),
                session.activeTenantId(),
                session.clientIp(),
                session.loginLocation(),
                session.device(),
                session.issuedAt(),
                session.expiresAt(),
                session.lastAccessAt(),
                session.active(),
                indexedSession.token().equals(currentToken)
        );
    }

    private long instantEpoch(Instant instant) {
        return instant == null ? 0L : instant.toEpochMilli();
    }

    private UserSessionResponse toSessionResponse(String token, String currentToken) {
        SaSession tokenSession = StpUtil.getTokenSessionByToken(token);
        String managementId = sessionIndexService.managementId(token)
                .orElseThrow(() -> new BusinessException("SESSION_INDEX_UNAVAILABLE", "会话管理索引不可用"));
        long issuedAt = sessionLong(tokenSession, "issuedAt", 0L);
        long expiresAt = sessionLong(tokenSession, "expiresAt", 0L);
        long lastAccessAt = sessionLong(tokenSession, "lastAccessAt", issuedAt);
        boolean currentSession = token.equals(currentToken);
        return new UserSessionResponse(
                managementId,
                requireSessionString(tokenSession, "username"),
                requireSessionString(tokenSession, "tenantId"),
                requireSessionString(tokenSession, "activeTenantId"),
                sessionString(tokenSession, "clientIp", ""),
                sessionString(tokenSession, "loginLocation", ""),
                sessionString(tokenSession, "device", "unknown"),
                TimeSupport.fromEpochMilli(issuedAt),
                TimeSupport.fromEpochMilli(expiresAt),
                TimeSupport.fromEpochMilli(lastAccessAt),
                StpUtil.stpLogic.getLoginIdByToken(token) != null,
                currentSession
        );
    }

    private Long resolveLoginId(String token) {
        Object loginId = StpUtil.stpLogic.getLoginIdByToken(token);
        if (loginId == null) {
            throw new BusinessException("SESSION_NOT_FOUND", "会话不存在");
        }
        return Long.parseLong(String.valueOf(loginId));
    }

    private String sessionAttribute(String token, String key) {
        return sessionString(StpUtil.getTokenSessionByToken(token), key, "");
    }

    private String sessionString(SaSession session, String key, String fallback) {
        Object value = session.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private String requireSessionString(SaSession session, String key) {
        String value = sessionString(session, key, "");
        if (StringUtils.hasText(value)) {
            return value;
        }
        throw new BusinessException("SESSION_INVALID", "会话元数据不完整");
    }

    private Map<String, Object> sessionAuditPayload(String managementId, String token) {
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfText(payload, "sessionId", managementId);
        try {
            SaSession session = StpUtil.getTokenSessionByToken(token);
            Object userId = session.get("userId");
            if (userId != null) {
                payload.put("targetUserId", userId);
            }
            putIfText(payload, "targetUsername", sessionString(session, "username", ""));
            putIfText(payload, "targetTenantId", sessionString(session, "tenantId", ""));
            putIfText(payload, "targetClientIp", sessionString(session, "clientIp", ""));
            putIfText(payload, "targetDevice", sessionString(session, "device", ""));
            long issuedAt = sessionLong(session, "issuedAt", 0L);
            long lastAccessAt = sessionLong(session, "lastAccessAt", 0L);
            if (issuedAt > 0) {
                payload.put("issuedAt", issuedAt);
            }
            if (lastAccessAt > 0) {
                payload.put("lastAccessAt", lastAccessAt);
            }
        } catch (Exception ex) {
            log.debug("构建会话审计载荷失败。managementId={}，error={}", managementId, ex.getMessage());
            // 即使令牌会话元数据已被清理，也要保证登出/下线操作可靠执行。
        }
        return payload;
    }

    private void putIfText(Map<String, Object> payload, String key, String value) {
        if (StringUtils.hasText(value)) {
            payload.put(key, value);
        }
    }

    private long sessionLong(SaSession session, String key, long fallback) {
        Object value = session.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
