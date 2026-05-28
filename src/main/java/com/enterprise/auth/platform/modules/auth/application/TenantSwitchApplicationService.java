package com.enterprise.auth.platform.modules.auth.application;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.authz.PlatformAdminSupport;
import com.enterprise.auth.platform.common.context.AuthContextHolder;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.dao.entity.SysTenantEntity;
import com.enterprise.auth.platform.dao.mapper.SysTenantMapper;
import com.enterprise.auth.platform.dto.model.SessionPrincipal;
import com.enterprise.auth.platform.dto.model.UserAccount;
import com.enterprise.auth.platform.dto.resp.PermissionSnapshotResponse;
import com.enterprise.auth.platform.modules.auth.application.PermissionSnapshotApplicationService;
import com.enterprise.auth.platform.service.AuditService;
import com.enterprise.auth.platform.service.SessionIndexService;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TenantSwitchApplicationService {

    private final AuditService auditService;
    private final PlatformAdminSupport platformAdminSupport;
    private final PermissionSnapshotApplicationService permissionSnapshotApplicationService;
    private final SessionIndexService sessionIndexService;
    private final SysTenantMapper sysTenantMapper;

    public TenantSwitchApplicationService(
            AuditService auditService,
            PlatformAdminSupport platformAdminSupport,
            PermissionSnapshotApplicationService permissionSnapshotApplicationService,
            SessionIndexService sessionIndexService,
            SysTenantMapper sysTenantMapper
    ) {
        this.auditService = auditService;
        this.platformAdminSupport = platformAdminSupport;
        this.permissionSnapshotApplicationService = permissionSnapshotApplicationService;
        this.sessionIndexService = sessionIndexService;
        this.sysTenantMapper = sysTenantMapper;
    }

    public PermissionSnapshotResponse switchTenant(UserAccount currentUser, String targetTenantId) {
        String normalizedTargetTenantId = StringUtils.hasText(targetTenantId) ? targetTenantId.trim() : "";
        if (!StringUtils.hasText(normalizedTargetTenantId)) {
            throw new BusinessException("VALIDATION_ERROR", "目标租户不能为空");
        }
        if (!platformAdminSupport.canSwitchTenant(currentUser, normalizedTargetTenantId)) {
            throw new BusinessException("ACCESS_DENIED", "无权切换到目标租户");
        }
        SysTenantEntity tenant = sysTenantMapper.selectOne(new LambdaQueryWrapper<SysTenantEntity>()
                .eq(SysTenantEntity::getTenantId, normalizedTargetTenantId)
                .eq(SysTenantEntity::getDeleted, 0)
                .last("limit 1"));
        if (tenant == null) {
            throw new BusinessException("TENANT_NOT_FOUND", "租户不存在");
        }
        if (tenant.getTenantStatus() == null || tenant.getTenantStatus() != 1) {
            throw new BusinessException("TENANT_DISABLED", "租户已停用");
        }

        SaSession tokenSession = StpUtil.getTokenSession();
        String fromTenantId = sessionString(tokenSession, "activeTenantId", currentUser.tenantId());
        String sessionId = StpUtil.getTokenValue();

        String previousTenantId = TenantContext.getTenantId();
        boolean switched = false;
        try {
            tokenSession.set("activeTenantId", normalizedTargetTenantId);
            tokenSession.delete("permissions");
            tokenSession.delete("roles");
            switched = true;
            TenantContext.setTenantId(normalizedTargetTenantId);
            AuthContextHolder.set(currentUser, new SessionPrincipal(
                    sessionId,
                    normalizedTargetTenantId,
                    currentUser.tenantId()
            ));
            PermissionSnapshotResponse snapshot = permissionSnapshotApplicationService.build(currentUser);
            sessionIndexService.updateActiveTenant(sessionId, normalizedTargetTenantId);
            auditService.record("TENANT_SWITCH", currentUser.username(), normalizedTargetTenantId, Map.of(
                    "sessionId", sessionId,
                    "operatorTenantId", currentUser.tenantId(),
                    "fromTenantId", fromTenantId,
                    "targetTenantId", normalizedTargetTenantId
            ));
            return snapshot;
        } catch (RuntimeException ex) {
            if (switched) {
                tokenSession.set("activeTenantId", fromTenantId);
                tokenSession.delete("permissions");
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