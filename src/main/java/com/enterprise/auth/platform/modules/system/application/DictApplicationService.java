package com.enterprise.auth.platform.modules.system.application;

import com.enterprise.auth.platform.dto.model.PageResult;
import com.enterprise.auth.platform.dto.req.DictCrudRequest;
import com.enterprise.auth.platform.service.SystemManagementService;
import org.springframework.stereotype.Service;

@Service
public class DictApplicationService {

    private final SystemManagementService systemManagementService;

    public DictApplicationService(SystemManagementService systemManagementService) {
        this.systemManagementService = systemManagementService;
    }

    public PageResult<SystemManagementService.DictView> dicts(
            String dictType,
            String category,
            String keyword,
            int page,
            int size,
            String sortBy,
            String sortDirection
    ) {
        return systemManagementService.dicts(dictType, category, keyword, page, size, sortBy, sortDirection);
    }

    public SystemManagementService.DictView createDict(DictCrudRequest request) {
        return systemManagementService.createDict(request);
    }

    public SystemManagementService.DictView updateDict(Long id, DictCrudRequest request) {
        return systemManagementService.updateDict(id, request);
    }

    public void deleteDict(Long id) {
        systemManagementService.deleteDict(id);
    }
}