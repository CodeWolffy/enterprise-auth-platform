package com.enterprise.auth.platform.modules.auth.application;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.common.authz.DataScopeService;
import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.common.authz.PlatformAdminSupport;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.auth.interfaces.UserSessionResponse;
import com.enterprise.auth.platform.modules.auth.application.SessionIndexService;
import com.enterprise.auth.platform.modules.auth.application.SessionIndexService.Page;
import com.enterprise.auth.platform.modules.notification.application.NotificationScenarioPublisher;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SessionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(SessionApplicationService.class);
    private static final int SESSION_RESULT_LIMIT = 200;

    private final DataScopeService dataScopeService;
    private final PlatformAdminSupport platformAdminSupport;
    private final SessionIndexService sessionIndexService;
    private final NotificationScenarioPublisher notificationScenarioPublisher;

    public SessionApplicationService(
            DataScopeService dataScopeService,
            PlatformAdminSupport platformAdminSupport,
            SessionIndexService sessionIndexService,
            NotificationScenarioPublisher notificationScenarioPublisher
    ) {
        this.dataScopeService = dataScopeService;
        this.platformAdminSupport = platformAdminSupport;
        this.sessionIndexService = sessionIndexService;
        this.notificationScenarioPublisher = notificationScenarioPublisher;
    }

    public void logout(String sessionId, String username, String tenantId) {
        Map<String, Object> payload = sessionAuditPayload(sessionId);
        StpUtil.logoutByTokenValue(sessionId);
        sessionIndexService.remove(sessionId);
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
        Long targetUserId = resolveLoginId(sessionId);
        String targetTenantId = sessionAttribute(sessionId, "tenantId", currentUser.tenantId());
        Map<String, Object> payload = sessionAuditPayload(sessionId);
        boolean sameOwner = currentUser.id().equals(targetUserId);
        boolean canManage = currentUser.permissions().contains(PermissionCodes.SESSION_KICK);
        boolean platformAdmin = platformAdminSupport.isPlatformSuperAdmin(currentUser);
        boolean sameTenantOrPlatform = currentUser.tenantId().equals(targetTenantId) || platformAdmin;
        boolean visibleTarget = platformAdmin || dataScopeService.canAccessUser(currentUser.tenantId(), targetUserId);
        if (!sameOwner && (!canManage || !sameTenantOrPlatform || !visibleTarget)) {
            throw new BusinessException("ACCESS_DENIED", "无权操作此会话");
        }
        StpUtil.kickoutByTokenValue(sessionId);
        sessionIndexService.remove(sessionId);
        payload.put("targetUserId", targetUserId);
        notificationScenarioPublisher.sessionForcedOffline(targetTenantId, targetUserId, currentUser.username(), payload);
    }

    private List<UserSessionResponse> ownSessions(UserAccount currentUser, String currentToken) {
        return StpUtil.getTokenValueListByLoginId(currentUser.id()).stream()
                .map(token -> safeSessionResponse(token, currentUser, currentToken))
                .flatMap(Optional::stream)
                .filter(UserSessionResponse::active)
                .sorted((a, b) -> Long.compare(b.lastAccessAt(), a.lastAccessAt()))
                .toList();
    }

    private PageResult<UserSessionResponse> allSessions(UserAccount currentUser, String currentToken, Integer page, Integer size) {
        boolean platformAdmin = platformAdminSupport.isPlatformSuperAdmin(currentUser);
        int effectivePage = page != null && page > 0 ? page : 1;
        int effectiveSize = size != null && size > 0 ? size : 10;
        int scanMultiplier = Math.min(effectiveSize * 3, SESSION_RESULT_LIMIT);
        Page indexPage = sessionIndexService.page(effectivePage - 1, scanMultiplier)
                .orElseGet(() -> new Page(0, List.of()));
        if (indexPage.records().isEmpty()) {
            List<UserSessionResponse> own = ownSessions(currentUser, currentToken);
            return PageResult.of(own.size(), 1, own.size(), own);
        }
        List<UserSessionResponse> sessions = indexPage.records().stream()
                .filter(entry -> entry != null)
                .filter(entry -> tenantVisible(currentUser, entry.response()))
                .filter(entry -> platformAdmin || canAccessSessionUser(currentUser, entry))
                .map(entry -> withCurrentSession(entry.response(), currentToken))
                .map(this::withActiveState)
                .peek(session -> {
                    if (!session.active()) {
                        sessionIndexService.remove(session.sessionId());
                    }
                })
                .filter(UserSessionResponse::active)
                .sorted((a, b) -> Long.compare(b.lastAccessAt(), a.lastAccessAt()))
                .limit(effectiveSize)
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
                        .sorted((a, b) -> Long.compare(b.lastAccessAt(), a.lastAccessAt()))
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

    private Optional<UserSessionResponse> safeSessionResponse(String token, UserAccount fallbackUser, String currentToken) {
        try {
            return Optional.of(toSessionResponse(token, fallbackUser, currentToken));
        } catch (Exception e) {
            log.debug("构建会话列表时跳过无效会话。token={}，error={}", token, e.getMessage());
            sessionIndexService.remove(token);
            return Optional.empty();
        }
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

    private UserSessionResponse withCurrentSession(UserSessionResponse session, String currentToken) {
        return new UserSessionResponse(
                session.sessionId(),
                session.username(),
                session.tenantId(),
                session.activeTenantId(),
                session.clientIp(),
                session.device(),
                session.issuedAt(),
                session.expiresAt(),
                session.lastAccessAt(),
                session.active(),
                session.sessionId().equals(currentToken)
        );
    }

    private UserSessionResponse withActiveState(UserSessionResponse session) {
        return new UserSessionResponse(
                session.sessionId(),
                session.username(),
                session.tenantId(),
                session.activeTenantId(),
                session.clientIp(),
                session.device(),
                session.issuedAt(),
                session.expiresAt(),
                session.lastAccessAt(),
                isSessionActive(session.sessionId()),
                session.currentSession()
        );
    }

    private boolean isSessionActive(String token) {
        try {
            return StpUtil.stpLogic.getLoginIdByToken(token) != null;
        } catch (Exception ex) {
            log.debug("检查会话活跃状态失败。token={}，error={}", token, ex.getMessage());
            return false;
        }
    }

    private UserSessionResponse toSessionResponse(String token, UserAccount fallbackUser, String currentToken) {
        SaSession tokenSession = StpUtil.getTokenSessionByToken(token);
        long issuedAt = sessionLong(tokenSession, "issuedAt", 0L);
        long expiresAt = sessionLong(tokenSession, "expiresAt", 0L);
        long lastAccessAt = sessionLong(tokenSession, "lastAccessAt", issuedAt);
        boolean currentSession = token.equals(currentToken);
        return new UserSessionResponse(
                token,
                sessionString(tokenSession, "username", fallbackUser.username()),
                sessionString(tokenSession, "tenantId", fallbackUser.tenantId()),
                sessionString(tokenSession, "activeTenantId", sessionString(tokenSession, "tenantId", fallbackUser.tenantId())),
                sessionString(tokenSession, "clientIp", ""),
                sessionString(tokenSession, "device", "unknown"),
                issuedAt,
                expiresAt,
                lastAccessAt,
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

    private String sessionAttribute(String token, String key, String fallback) {
        return sessionString(StpUtil.getTokenSessionByToken(token), key, fallback);
    }

    private String sessionString(SaSession session, String key, String fallback) {
        Object value = session.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private Map<String, Object> sessionAuditPayload(String sessionId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        try {
            SaSession session = StpUtil.getTokenSessionByToken(sessionId);
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
            log.debug("构建会话审计载荷失败。sessionId={}，error={}", sessionId, ex.getMessage());
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
