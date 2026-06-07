package com.enterprise.auth.platform.modules.auth.application;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.modules.auth.infrastructure.AuthPrincipalCacheService;
import com.enterprise.auth.platform.modules.auth.interfaces.UserSessionResponse;
import java.util.Collection;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthPermissionSnapshotInvalidationService {

    private static final int SESSION_SCAN_PAGE_SIZE = 200;
    private static final int SESSION_SCAN_MAX_PAGES = 100;

    private final AuthPrincipalCacheService authPrincipalCacheService;
    private final SessionIndexService sessionIndexService;

    public AuthPermissionSnapshotInvalidationService(
            AuthPrincipalCacheService authPrincipalCacheService,
            SessionIndexService sessionIndexService
    ) {
        this.authPrincipalCacheService = authPrincipalCacheService;
        this.sessionIndexService = sessionIndexService;
    }

    public void invalidateAll() {
        authPrincipalCacheService.evictAll();
        invalidateIndexedSessions(null);
        invalidateCurrentSession();
    }

    public void invalidateUser(Long userId, String tenantId, String username) {
        authPrincipalCacheService.evictByUser(userId, tenantId, username);
        invalidateUserSessions(userId);
        invalidateCurrentSessionIfUser(userId);
    }

    public void invalidateUsers(Collection<UserSnapshotTarget> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        for (UserSnapshotTarget user : users) {
            if (user == null) {
                continue;
            }
            invalidateUser(user.userId(), user.tenantId(), user.username());
        }
    }

    public void invalidateTenant(String tenantId) {
        authPrincipalCacheService.evictAll();
        invalidateIndexedSessions(tenantId);
        invalidateCurrentSessionIfTenant(tenantId);
    }

    private void invalidateIndexedSessions(String tenantId) {
        for (int page = 0; page < SESSION_SCAN_MAX_PAGES; page++) {
            SessionIndexService.Page sessionPage = sessionIndexService.page(page, SESSION_SCAN_PAGE_SIZE)
                    .orElse(null);
            if (sessionPage == null || sessionPage.records().isEmpty()) {
                return;
            }
            for (SessionIndexService.IndexedSession indexedSession : sessionPage.records()) {
                if (indexedSession == null || indexedSession.response() == null) {
                    continue;
                }
                UserSessionResponse response = indexedSession.response();
                if (StringUtils.hasText(tenantId)
                        && !tenantId.equals(response.tenantId())
                        && !tenantId.equals(response.activeTenantId())) {
                    continue;
                }
                invalidateToken(response.sessionId());
            }
            if ((long) (page + 1) * SESSION_SCAN_PAGE_SIZE >= sessionPage.total()) {
                return;
            }
        }
    }

    private void invalidateUserSessions(Long userId) {
        if (userId == null) {
            return;
        }
        invalidateSaTokenUserSessions(userId);
        invalidateIndexedUserSessions(userId);
    }

    private void invalidateSaTokenUserSessions(Long userId) {
        try {
            for (String token : StpUtil.getTokenValueListByLoginId(userId)) {
                invalidateToken(token);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void invalidateIndexedUserSessions(Long userId) {
        for (int page = 0; page < SESSION_SCAN_MAX_PAGES; page++) {
            SessionIndexService.Page sessionPage = sessionIndexService.pageUser(userId, page, SESSION_SCAN_PAGE_SIZE)
                    .orElse(null);
            if (sessionPage == null || sessionPage.records().isEmpty()) {
                return;
            }
            for (SessionIndexService.IndexedSession indexedSession : sessionPage.records()) {
                if (indexedSession == null || indexedSession.response() == null) {
                    continue;
                }
                invalidateToken(indexedSession.response().sessionId());
            }
            if ((long) (page + 1) * SESSION_SCAN_PAGE_SIZE >= sessionPage.total()) {
                return;
            }
        }
    }

    private void invalidateCurrentSession() {
        try {
            invalidateToken(StpUtil.getTokenValue());
        } catch (RuntimeException ignored) {
        }
    }

    private void invalidateCurrentSessionIfUser(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            if (StpUtil.isLogin() && String.valueOf(userId).equals(String.valueOf(StpUtil.getLoginId()))) {
                invalidateCurrentSession();
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void invalidateCurrentSessionIfTenant(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return;
        }
        try {
            SaSession session = StpUtil.getTokenSession();
            String sessionTenantId = sessionString(session, "tenantId");
            String activeTenantId = sessionString(session, "activeTenantId");
            if (tenantId.equals(sessionTenantId) || tenantId.equals(activeTenantId)) {
                clearSessionSnapshot(session);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void invalidateToken(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        try {
            clearSessionSnapshot(StpUtil.getTokenSessionByToken(token));
        } catch (RuntimeException ignored) {
        }
    }

    private void clearSessionSnapshot(SaSession session) {
        if (session == null) {
            return;
        }
        session.delete("permissions");
        session.delete("permissionsTenantId");
        session.delete("roles");
    }

    private String sessionString(SaSession session, String key) {
        Object value = session == null ? null : session.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public record UserSnapshotTarget(Long userId, String tenantId, String username) {
    }
}