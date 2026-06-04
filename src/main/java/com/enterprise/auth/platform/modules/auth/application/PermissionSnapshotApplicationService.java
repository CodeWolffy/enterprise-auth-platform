package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.common.authz.PlatformAdminSupport;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.auth.interfaces.MenuNode;
import com.enterprise.auth.platform.modules.auth.interfaces.PermissionSnapshotResponse;
import com.enterprise.auth.platform.modules.file.application.FileApplicationService;
import com.enterprise.auth.platform.modules.resource.application.ResourceQueryFacade;
import com.enterprise.auth.platform.modules.role.application.RoleGrantQueryFacade;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PermissionSnapshotApplicationService {

    private final PlatformAdminSupport platformAdminSupport;
    private final RoleGrantQueryFacade roleGrantQueryFacade;
    private final ResourceQueryFacade resourceQueryFacade;
    private final FileApplicationService fileApplicationService;

    public PermissionSnapshotApplicationService(
            PlatformAdminSupport platformAdminSupport,
            RoleGrantQueryFacade roleGrantQueryFacade,
            ResourceQueryFacade resourceQueryFacade,
            FileApplicationService fileApplicationService
    ) {
        this.platformAdminSupport = platformAdminSupport;
        this.roleGrantQueryFacade = roleGrantQueryFacade;
        this.resourceQueryFacade = resourceQueryFacade;
        this.fileApplicationService = fileApplicationService;
    }

    public PermissionSnapshotResponse build(UserAccount user) {
        String activeTenantId = TenantContext.getTenantId();
        if (!StringUtils.hasText(activeTenantId)) {
            activeTenantId = user.tenantId();
        }
        boolean superAdmin = platformAdminSupport.isPlatformSuperAdmin(user);
        Set<String> grants = roleGrantQueryFacade.resolveGrantKeys(activeTenantId, user.roles(), superAdmin);
        List<MenuNode> menus = resourceQueryFacade.resolveMenuTree(activeTenantId, user.roles(), superAdmin);
        return new PermissionSnapshotResponse(
                user.id(),
                user.username(),
                activeTenantId,
                user.tenantId(),
                user.roles(),
                grants,
                user.dataScopeType(),
                user.customDeptIds(),
                menus,
                user.avatarFileKey(),
                fileApplicationService.publicUrl(user.avatarFileKey()),
                superAdmin
        );
    }
}