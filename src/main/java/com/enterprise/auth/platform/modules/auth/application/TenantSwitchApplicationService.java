package com.enterprise.auth.platform.modules.auth.application;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.authz.PlatformAdminSupport;
import com.enterprise.auth.platform.common.context.AuthContextHolder;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantEntity;
import com.enterprise.auth.platform.modules.tenant.application.TenantProfileFacade;
import com.enterprise.auth.platform.modules.auth.domain.SessionPrincipal;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.auth.interfaces.PermissionSnapshotResponse;
import com.enterprise.auth.platform.modules.auth.application.PermissionSnapshotApplicationService;
import com.enterprise.auth.platform.modules.auth.application.SessionIndexService;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TenantSwitchApplicationService {

    private final PlatformAdminSupport platformAdminSupport;
    private final PermissionSnapshotApplicationService permissionSnapshotApplicationService;
    private final SessionIndexService sessionIndexService;
    private final TenantProfileFacade tenantProfileFacade;

    public TenantSwitchApplicationService(
            PlatformAdminSupport platformAdminSupport,
            PermissionSnapshotApplicationService permissionSnapshotApplicationService,
            SessionIndexService sessionIndexService,
            TenantProfileFacade tenantProfileFacade
    ) {
        this.platformAdminSupport = platformAdminSupport;
        this.permissionSnapshotApplicationService = permissionSnapshotApplicationService;
        this.sessionIndexService = sessionIndexService;
        this.tenantProfileFacade = tenantProfileFacade;
    }

    public PermissionSnapshotResponse switchTenant(UserAccount currentUser, String targetTenantId) {
        String normalizedTargetTenantId = StringUtils.hasText(targetTenantId) ? targetTenantId.trim() : "";
        if (!StringUtils.hasText(normalizedTargetTenantId)) {
            throw new BusinessException("VALIDATION_ERROR", "目标租户不能为空");
        }
        if (!platformAdminSupport.canSwitchTenant(currentUser, normalizedTargetTenantId)) {
            throw new BusinessException("ACCESS_DENIED", "无权切换到目标租户");
        }
        SysTenantEntity tenant = tenantProfileFacade.findByTenantId(normalizedTargetTenantId)
                .orElse(null);
        if (tenant == null) {
            throw new BusinessException("TENANT_NOT_FOUND", "租户不存在");
        }
        tenantProfileFacade.ensureTenantAccessible(tenant);

        SaSession tokenSession = StpUtil.getTokenSession();
        String fromTenantId = sessionString(tokenSession, "activeTenantId", currentUser.tenantId());
        String sessionId = StpUtil.getTokenValue();

        String previousTenantId = TenantContext.getTenantId();
        boolean switched = false;
        try {
            tokenSession.set("activeTenantId", normalizedTargetTenantId);
            tokenSession.delete("permissions");
            tokenSession.delete("permissionsTenantId");
            tokenSession.delete("roles");
            switched = true;
            TenantContext.setTenantId(normalizedTargetTenantId);
            AuthContextHolder.set(currentUser, new SessionPrincipal(
                    sessionId,
                    normalizedTargetTenantId,
                    currentUser.tenantId(),
                    false
            ));
            PermissionSnapshotResponse snapshot = permissionSnapshotApplicationService.build(currentUser);
            sessionIndexService.updateActiveTenant(sessionId, normalizedTargetTenantId);
            return snapshot;
        } catch (RuntimeException ex) {
            if (switched) {
                tokenSession.set("activeTenantId", fromTenantId);
                tokenSession.delete("permissions");
                tokenSession.delete("permissionsTenantId");
                tokenSession.delete("roles");
                sessionIndexService.updateActiveTenant(sessionId, fromTenantId);
            }
            throw ex;
        } finally {
            if (StringUtils.hasText(previousTenantId)) {
                TenantContext.setTenantId(previousTenantId);
            } else {
                TenantContext.clear();
            }
        }
    }

    private String sessionString(SaSession session, String key, String fallback) {
        Object value = session.get(key);
        return value == null ? fallback : String.valueOf(value);
    }
}