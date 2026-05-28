package com.enterprise.auth.platform.service;

import com.enterprise.auth.platform.dto.model.UserAccount;
import com.enterprise.auth.platform.dto.resp.PermissionSnapshotResponse;
import com.enterprise.auth.platform.modules.auth.application.PermissionSnapshotApplicationService;
import org.springframework.stereotype.Service;

@Service
public class PermissionSnapshotService {

    private final PermissionSnapshotApplicationService permissionSnapshotApplicationService;

    public PermissionSnapshotService(PermissionSnapshotApplicationService permissionSnapshotApplicationService) {
        this.permissionSnapshotApplicationService = permissionSnapshotApplicationService;
    }

    public PermissionSnapshotResponse build(UserAccount user) {
        return permissionSnapshotApplicationService.build(user);
    }
}