package com.enterprise.auth.platform.modules.system.application;

import com.enterprise.auth.platform.dto.req.CategoryConfigRequest;
import com.enterprise.auth.platform.service.SystemManagementService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CategoryRuleApplicationService {

    private final SystemManagementService systemManagementService;

    public CategoryRuleApplicationService(SystemManagementService systemManagementService) {
        this.systemManagementService = systemManagementService;
    }

    public Map<String, List<SystemManagementService.CategoryOption>> categories() {
        return systemManagementService.categories();
    }

    public List<SystemManagementService.CategoryOption> categoryOptions(String targetType) {
        return systemManagementService.categoryOptions(targetType);
    }

    public SystemManagementService.CategoryAnalysis analyzeCategoryOption(String targetType, String code) {
        return systemManagementService.analyzeCategoryOption(targetType, code);
    }

    public SystemManagementService.CategoryOption createCategoryOption(String targetType, CategoryConfigRequest request) {
        return systemManagementService.createCategoryOption(targetType, request);
    }

    public SystemManagementService.CategoryOption updateCategoryOption(String targetType, String code, CategoryConfigRequest request) {
        return systemManagementService.updateCategoryOption(targetType, code, request);
    }

    public void deleteCategoryOption(String targetType, String code) {
        systemManagementService.deleteCategoryOption(targetType, code);
    }
}