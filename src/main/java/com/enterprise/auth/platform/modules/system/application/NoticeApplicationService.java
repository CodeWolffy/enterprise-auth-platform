package com.enterprise.auth.platform.modules.system.application;

import com.enterprise.auth.platform.dto.model.PageResult;
import com.enterprise.auth.platform.dto.req.NoticeCrudRequest;
import com.enterprise.auth.platform.service.SystemManagementService;
import org.springframework.stereotype.Service;

@Service
public class NoticeApplicationService {

    private final SystemManagementService systemManagementService;

    public NoticeApplicationService(SystemManagementService systemManagementService) {
        this.systemManagementService = systemManagementService;
    }

    public PageResult<SystemManagementService.NoticeView> notices(
            Boolean published,
            String workflowStatus,
            String keyword,
            int page,
            int size,
            String sortBy,
            String sortDirection
    ) {
        return systemManagementService.notices(published, workflowStatus, keyword, page, size, sortBy, sortDirection);
    }

    public SystemManagementService.NoticeView createNotice(NoticeCrudRequest request) {
        return systemManagementService.createNotice(request);
    }

    public SystemManagementService.NoticeView updateNotice(Long id, NoticeCrudRequest request) {
        return systemManagementService.updateNotice(id, request);
    }

    public void deleteNotice(Long id) {
        systemManagementService.deleteNotice(id);
    }
}