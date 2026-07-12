package com.enterprise.auth.platform.modules.auth.application;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.modules.auth.application.PlatformAdminSupport;
import com.enterprise.auth.platform.modules.auth.domain.AuthContextHolder;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.context.TenantContextSupport;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.auth.application.PermissionSnapshotApplicationService;
import com.enterprise.auth.platform.modules.auth.application.SessionIndexService;
import com.enterprise.auth.platform.modules.auth.domain.SessionPrincipal;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.auth.interfaces.PermissionSnapshotResponse;
import com.enterprise.auth.platform.modules.log.application.LogPublisher;
import com.enterprise.auth.platform.modules.tenant.application.TenantProfileFacade;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TenantSwitchApplicationService {

    private final PlatformAdminSupport platformAdminSupport;
    private final LogPublisher logPublisher;
    private final PermissionSnapshotApplicationService permissionSnapshotApplicationService;
    private final SessionIndexService sessionIndexService;
    private final TenantProfileFacade tenantProfileFacade;

    public TenantSwitchApplicationService(
            PlatformAdminSupport platformAdminSupport,
            LogPublisher logPublisher,
            PermissionSnapshotApplicationService permissionSnapshotApplicationService,
            SessionIndexService sessionIndexService,
            TenantProfileFacade tenantProfileFacade
    ) {
        this.platformAdminSupport = platformAdminSupport;
        this.logPublisher = logPublisher;
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
        String fromTenantId = sessionString(tokenSession, "activeTenantId", null);
        if (!StringUtils.hasText(fromTenantId)) {
            throw new BusinessException("SESSION_INVALID", "会话缺少活跃租户");
        }
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
            logPublisher.publish("TENANT_SWITCH", currentUser.username(), normalizedTargetTenantId, Map.of(
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

    public List<SwitchableTenantView> switchableTenants(UserAccount currentUser) {
        final String activeTenantId = TenantContextSupport.currentTenantIdOr(currentUser.tenantId());
        String originTenantId = currentUser.tenantId();
        Instant now = TimeSupport.now();

        if (!platformAdminSupport.isPlatformSuperAdmin(currentUser)) {
            return tenantProfileFacade.findByTenantId(originTenantId)
                    .map(tenant -> List.of(toSwitchableTenantView(
                            toTenantRecord(tenant),
                            activeTenantId,
                            originTenantId,
                            now
                    )))
                    .orElseGet(List::of);
        }

        String platformTenantId = platformAdminSupport.platformTenantId();
        List<TenantProfileFacade.TenantRecord> tenants = TenantContext.runWithGlobalScope(
                platformTenantId,
                tenantProfileFacade::listTenantRecords
        );
        return tenants.stream()
                .map(tenant -> toSwitchableTenantView(tenant, activeTenantId, originTenantId, now))
                .toList();
    }

    private String sessionString(SaSession session, String key, String fallback) {
        Object value = session.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private SwitchableTenantView toSwitchableTenantView(
            TenantProfileFacade.TenantRecord tenant,
            String activeTenantId,
            String originTenantId,
            Instant now
    ) {
        String disabledReason = disabledReason(tenant, now);
        return new SwitchableTenantView(
                tenant.tenantId(),
                tenant.tenantName(),
                tenant.platformLevel() != null && tenant.platformLevel() == 1,
                tenant.tenantStatus(),
                tenant.tenantId().equals(activeTenantId),
                tenant.tenantId().equals(originTenantId),
                disabledReason == null,
                disabledReason
        );
    }

    private TenantProfileFacade.TenantRecord toTenantRecord(SysTenantEntity tenant) {
        return new TenantProfileFacade.TenantRecord(
                tenant.getTenantId(),
                tenant.getTenantName(),
                tenant.getPlatformLevel(),
                tenant.getTenantStatus(),
                tenant.getAuthBeginAt(),
                tenant.getExpireAt(),
                tenant.getLogoUrl(),
                tenant.getContactName(),
                tenant.getContactPhone(),
                tenant.getContactEmail(),
                tenant.getWebsite(),
                tenant.getAddress(),
                tenant.getLifecycleNote(),
                tenant.getPackageCode()
        );
    }

    private String disabledReason(TenantProfileFacade.TenantRecord tenant, Instant now) {
        if (tenant.tenantStatus() == null || tenant.tenantStatus() != 1) {
            return "租户已停用";
        }
        if (tenant.authBeginAt() != null && tenant.authBeginAt().isAfter(now)) {
            return "租户授权尚未生效";
        }
        if (tenant.expireAt() != null && !tenant.expireAt().isAfter(now)) {
            return "租户授权已过期";
        }
        return null;
    }

    public record SwitchableTenantView(
            String tenantId,
            String name,
            boolean platformLevel,
            Integer tenantStatus,
            boolean active,
            boolean origin,
            boolean switchable,
            String disabledReason
    ) {
    }
}
