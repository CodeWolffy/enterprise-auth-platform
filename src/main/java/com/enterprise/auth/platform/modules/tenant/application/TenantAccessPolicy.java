package com.enterprise.auth.platform.modules.tenant.application;

import com.enterprise.auth.platform.common.authz.PlatformAdminSupport;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.security.CurrentUserService;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TenantAccessPolicy {

    private final PlatformAdminSupport platformAdminSupport;
    private final CurrentUserService currentUserService;

    public TenantAccessPolicy(
            PlatformAdminSupport platformAdminSupport,
            CurrentUserService currentUserService
    ) {
        this.platformAdminSupport = platformAdminSupport;
        this.currentUserService = currentUserService;
    }

    public boolean isPlatformSuperAdmin() {
        return currentUser().map(platformAdminSupport::isPlatformSuperAdmin).orElse(false);
    }

    public String currentTenantId() {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : "platform";
    }

    public void ensureTenantReadable(String tenantId) {
        if (isPlatformSuperAdmin()) {
            return;
        }
        if (!currentTenantId().equals(tenantId)) {
            throw new BusinessException("ACCESS_DENIED", "无权访问此租户");
        }
    }

    public void requirePlatformSuperAdmin() {
        if (!isPlatformSuperAdmin()) {
            throw new BusinessException("ACCESS_DENIED", "需要平台超级管理员权限");
        }
    }

    private Optional<UserAccount> currentUser() {
        return currentUserService.currentUser();
    }
}