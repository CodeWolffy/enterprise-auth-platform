package com.enterprise.auth.platform.modules.tenant.application;

import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.tenant.application.TenantManagementService;
import org.springframework.stereotype.Service;

@Service
public class TenantDirectoryApplicationService {

    private final TenantManagementService tenantManagementService;

    public TenantDirectoryApplicationService(TenantManagementService tenantManagementService) {
        this.tenantManagementService = tenantManagementService;
    }

    public PageResult<TenantView> page(
            String keyword,
            Boolean platformLevel,
            Integer tenantStatus,
            int page,
            int size
    ) {
        return tenantManagementService.page(keyword, platformLevel, tenantStatus, page, size);
    }

    public TenantView detail(String tenantId) {
        return tenantManagementService.detail(tenantId);
    }
}
