package com.enterprise.auth.platform.modules.auth.application;

import cn.dev33.satoken.exception.SaTokenContextException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.modules.auth.application.PlatformAdminSupport;
import com.enterprise.auth.platform.common.context.TenantContextSupport;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.auth.interfaces.PermissionSnapshotResponse;
import com.enterprise.auth.platform.modules.file.application.FileApplicationService;
import com.enterprise.auth.platform.modules.menu.api.MenuNode;
import com.enterprise.auth.platform.modules.role.application.RoleGrantQueryFacade;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PermissionSnapshotApplicationService {

    private final PlatformAdminSupport platformAdminSupport;
    private final RoleGrantQueryFacade roleGrantQueryFacade;
    private final FileApplicationService fileApplicationService;
    private final AuthzVersionService authzVersionService;

    public PermissionSnapshotApplicationService(
            PlatformAdminSupport platformAdminSupport,
            RoleGrantQueryFacade roleGrantQueryFacade,
            FileApplicationService fileApplicationService,
            AuthzVersionService authzVersionService
    ) {
        this.platformAdminSupport = platformAdminSupport;
        this.roleGrantQueryFacade = roleGrantQueryFacade;
        this.fileApplicationService = fileApplicationService;
        this.authzVersionService = authzVersionService;
    }

    public PermissionSnapshotResponse build(UserAccount user) {
        String activeTenantId = TenantContextSupport.currentTenantIdOr(user.tenantId());
        boolean superAdmin = platformAdminSupport.isPlatformSuperAdmin(user);
        Set<String> grants = roleGrantQueryFacade.resolveGrantKeys(activeTenantId, user.roles(), superAdmin);
        List<MenuNode> menus = roleGrantQueryFacade.resolveMenuTree(activeTenantId, user.roles(), superAdmin);
        PermissionSnapshotResponse snapshot = new PermissionSnapshotResponse(
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
        writeCurrentTokenSnapshot(activeTenantId, user.roles(), grants);
        return snapshot;
    }

    private void writeCurrentTokenSnapshot(String activeTenantId, Set<String> roles, Set<String> grants) {
        if (!StringUtils.hasText(activeTenantId)) {
            return;
        }
        try {
            SaSession tokenSession = StpUtil.getTokenSession();
            tokenSession.set("permissions", List.copyOf(grants));
            tokenSession.set("permissionsTenantId", activeTenantId);
            tokenSession.set("roles", List.copyOf(roles));
            AuthzVersionService.Versions versions = authzVersionService.currentVersions(activeTenantId);
            tokenSession.set("authzGlobalVersion", versions.global());
            tokenSession.set("authzTenantVersion", versions.tenant());
        } catch (SaTokenContextException ignored) {
        } catch (RuntimeException ignored) {
        }
    }
}
