package com.enterprise.auth.platform.modules.tenant.application;

import com.enterprise.auth.platform.dto.model.PageResult;
import com.enterprise.auth.platform.service.CatalogService;
import com.enterprise.auth.platform.service.TenantManagementService;
import org.springframework.stereotype.Service;

@Service
public class TenantDirectoryApplicationService {

    private final TenantManagementService tenantManagementService;

    public TenantDirectoryApplicationService(TenantManagementService tenantManagementService) {
        this.tenantManagementService = tenantManagementService;
    }

    public PageResult<CatalogService.TenantView> page(
            String keyword,
            Boolean platformLevel,
            Integer tenantStatus,
            int page,
            int size
    ) {
        return tenantManagementService.page(keyword, platformLevel, tenantStatus, page, size);
    }
}