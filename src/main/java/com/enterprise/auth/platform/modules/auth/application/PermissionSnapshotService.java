package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.auth.interfaces.PermissionSnapshotResponse;
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