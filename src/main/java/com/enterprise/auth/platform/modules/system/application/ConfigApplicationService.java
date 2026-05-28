package com.enterprise.auth.platform.modules.system.application;

import com.enterprise.auth.platform.dto.model.PageResult;
import com.enterprise.auth.platform.dto.req.ConfigCrudRequest;
import com.enterprise.auth.platform.service.SystemManagementService;
import org.springframework.stereotype.Service;

@Service
public class ConfigApplicationService {

    private final SystemManagementService systemManagementService;

    public ConfigApplicationService(SystemManagementService systemManagementService) {
        this.systemManagementService = systemManagementService;
    }

    public PageResult<SystemManagementService.ConfigView> configs(
            String category,
            String keyword,
            int page,
            int size,
            String sortBy,
            String sortDirection
    ) {
        return systemManagementService.configs(category, keyword, page, size, sortBy, sortDirection);
    }

    public SystemManagementService.ConfigView createConfig(ConfigCrudRequest request) {
        return systemManagementService.createConfig(request);
    }

    public SystemManagementService.ConfigView updateConfig(Long id, ConfigCrudRequest request) {
        return systemManagementService.updateConfig(id, request);
    }

    public void deleteConfig(Long id) {
        systemManagementService.deleteConfig(id);
    }
}