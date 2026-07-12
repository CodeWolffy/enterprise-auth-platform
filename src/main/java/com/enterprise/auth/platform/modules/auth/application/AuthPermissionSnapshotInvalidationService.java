package com.enterprise.auth.platform.modules.auth.application;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.modules.auth.infrastructure.AuthPrincipalCacheService;
import java.util.Collection;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 权限快照失效：全局/租户级走版本号 O(1)；单用户仍清理其 Sa-Token 会话快照（O 用户会话数）。
 */
@Service
public class AuthPermissionSnapshotInvalidationService {

    private final AuthPrincipalCacheService authPrincipalCacheService;
    private final AuthzVersionService authzVersionService;

    public AuthPermissionSnapshotInvalidationService(
            AuthPrincipalCacheService authPrincipalCacheService,
            AuthzVersionService authzVersionService
    ) {
        this.authPrincipalCacheService = authPrincipalCacheService;
        this.authzVersionService = authzVersionService;
    }

    public void invalidateAll() {
        authPrincipalCacheService.evictAll();
        authzVersionService.bumpGlobal();
        invalidateCurrentSession();
    }

    public void invalidateUser(Long userId, String tenantId, String username) {
        authPrincipalCacheService.evictByUser(userId, tenantId, username);
        invalidateSaTokenUserSessions(userId);
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
        authzVersionService.bumpTenant(tenantId);
        invalidateCurrentSessionIfTenant(tenantId);
    }

    private void invalidateSaTokenUserSessions(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            for (String token : StpUtil.getTokenValueListByLoginId(userId)) {
                invalidateToken(token);
            }
        } catch (RuntimeException ignored) {
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
        session.delete("authzGlobalVersion");
        session.delete("authzTenantVersion");
    }

    private String sessionString(SaSession session, String key) {
        Object value = session == null ? null : session.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public record UserSnapshotTarget(Long userId, String tenantId, String username) {
    }
}