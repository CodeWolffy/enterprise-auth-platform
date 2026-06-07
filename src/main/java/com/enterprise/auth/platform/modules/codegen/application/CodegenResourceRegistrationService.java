package com.enterprise.auth.platform.modules.codegen.application;

import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.auth.application.AuthPermissionSnapshotInvalidationService;
import com.enterprise.auth.platform.modules.menu.application.MenuTemplateMutationFacade;
import com.enterprise.auth.platform.modules.menu.application.MenuTemplateMutationFacade.MenuTemplateMutation;
import com.enterprise.auth.platform.modules.menu.application.MenuTemplateMutationFacade.MenuTemplateNode;
import com.enterprise.auth.platform.modules.role.application.RoleMenuMutationFacade;
import com.enterprise.auth.platform.modules.tenant.infrastructure.TenantProperties;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CodegenResourceRegistrationService {

    private static final String GENERATED_MENU_PARENT_KEY = "generated";
    private static final String GENERATED_API_PARENT_KEY = "api.generated";

    private final MenuTemplateMutationFacade menuTemplateMutationFacade;
    private final RoleMenuMutationFacade roleMenuMutationFacade;
    private final AuthPermissionSnapshotInvalidationService permissionSnapshotInvalidationService;
    private final TenantProperties tenantProperties;

    public CodegenResourceRegistrationService(
            MenuTemplateMutationFacade menuTemplateMutationFacade,
            RoleMenuMutationFacade roleMenuMutationFacade,
            AuthPermissionSnapshotInvalidationService permissionSnapshotInvalidationService,
            TenantProperties tenantProperties
    ) {
        this.menuTemplateMutationFacade = menuTemplateMutationFacade;
        this.roleMenuMutationFacade = roleMenuMutationFacade;
        this.permissionSnapshotInvalidationService = permissionSnapshotInvalidationService;
        this.tenantProperties = tenantProperties;
    }

    @Transactional
    public List<String> register(String moduleName, String title) {
        String safeModule = moduleName == null ? "" : moduleName.trim();
        if (!safeModule.matches("^[A-Za-z][A-Za-z0-9_]*$")) {
            throw new BusinessException("VALIDATION_ERROR", "moduleName 格式不合法");
        }
        String templateTenantId = platformTenantId();
        String grantTenantId = activeTenantId();
        Long menuId = ensureMenuWithParents(templateTenantId, safeModule, title);
        Long readApiId = ensureApi(templateTenantId, "api.generated." + safeModule + ".read", safeModule + " 读", safeModule + ":read", 900);
        Long writeApiId = ensureApi(templateTenantId, "api.generated." + safeModule + ".write", safeModule + " 写", safeModule + ":write", 910);

        List<String> grantKeys = new ArrayList<>();
        grantKeys.add(safeModule + ":read");
        grantKeys.add(safeModule + ":write");
        List<Long> assignedMenuIds = List.of(menuId, readApiId, writeApiId);
        assignToAdminRole(grantTenantId, assignedMenuIds);
        permissionSnapshotInvalidationService.invalidateAll();
        return grantKeys;
    }

    private String activeTenantId() {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : platformTenantId();
    }

    private String platformTenantId() {
        return StringUtils.hasText(tenantProperties.platformTenantId()) ? tenantProperties.platformTenantId() : "platform";
    }

    private Long ensureMenuWithParents(String tenantId, String moduleName, String title) {
        ensureParentMenu(tenantId, GENERATED_MENU_PARENT_KEY, "已生成模块");
        ensureParentApi(tenantId);
        return ensureMenu(tenantId, moduleName, title);
    }

    private void ensureParentMenu(String tenantId, String resourceKey, String resourceName) {
        if (menuTemplateMutationFacade.findByKeyAndType(tenantId, resourceKey, "DIR").isPresent()) {
            return;
        }
        MenuTemplateNode platformManagement = menuTemplateMutationFacade.findByKey(tenantId, "platform-management").orElse(null);
        menuTemplateMutationFacade.create(new MenuTemplateMutation(
                tenantId,
                platformManagement == null ? null : platformManagement.id(),
                platformManagement == null ? "" : joinAncestor(platformManagement.ancestors(), platformManagement.id()),
                "DIR",
                resourceKey,
                resourceName,
                null,
                null,
                null,
                null,
                "Files",
                75,
                1,
                1,
                1
        ));
    }

    private void ensureParentApi(String tenantId) {
        if (menuTemplateMutationFacade.findByKeyAndType(tenantId, GENERATED_API_PARENT_KEY, "DIR").isPresent()) {
            return;
        }
        MenuTemplateNode apiParent = menuTemplateMutationFacade.findByKey(tenantId, "api").orElse(null);
        if (apiParent == null) {
            return;
        }
        menuTemplateMutationFacade.create(new MenuTemplateMutation(
                tenantId,
                apiParent.id(),
                joinAncestor(apiParent.ancestors(), apiParent.id()),
                "DIR",
                GENERATED_API_PARENT_KEY,
                "生成模块 API",
                null,
                null,
                null,
                null,
                null,
                990,
                0,
                1,
                1
        ));
    }

    private Long ensureMenu(String tenantId, String moduleName, String title) {
        String menuKey = "generated." + moduleName;
        MenuTemplateNode existing = menuTemplateMutationFacade.findByKey(tenantId, menuKey).orElse(null);
        if (existing != null) {
            return existing.id();
        }
        MenuTemplateNode parent = menuTemplateMutationFacade.findByKey(tenantId, GENERATED_MENU_PARENT_KEY)
                .orElseThrow(() -> new BusinessException("PARENT_MENU_MISSING", "已生成模块父菜单未就绪"));
        return menuTemplateMutationFacade.create(new MenuTemplateMutation(
                tenantId,
                parent.id(),
                joinAncestor(parent.ancestors(), parent.id()),
                "MENU",
                menuKey,
                (StringUtils.hasText(title) ? title : moduleName) + "（生成产物）",
                menuKey,
                moduleName + ":read",
                "/platform/generated/" + moduleName,
                "CodegenView",
                "Files",
                100,
                1,
                1,
                0
        ));
    }

    private Long ensureApi(String tenantId, String resourceKey, String resourceName, String grantKey, int orderNo) {
        MenuTemplateNode existing = menuTemplateMutationFacade.findByKey(tenantId, resourceKey).orElse(null);
        if (existing != null) {
            return existing.id();
        }
        MenuTemplateNode parent = menuTemplateMutationFacade.findByKey(tenantId, GENERATED_API_PARENT_KEY).orElse(null);
        return menuTemplateMutationFacade.create(new MenuTemplateMutation(
                tenantId,
                parent == null ? null : parent.id(),
                parent == null ? "" : joinAncestor(parent.ancestors(), parent.id()),
                "API",
                resourceKey,
                resourceName,
                null,
                grantKey,
                null,
                null,
                null,
                orderNo,
                0,
                1,
                0
        ));
    }

    private void assignToAdminRole(String tenantId, List<Long> menuIds) {
        Set<Long> expandedMenuIds = new LinkedHashSet<>();
        for (Long menuId : menuIds) {
            menuTemplateMutationFacade.findById(platformTenantId(), menuId).ifPresent(menu -> {
                if (StringUtils.hasText(menu.ancestors())) {
                    for (String ancestor : menu.ancestors().split(",")) {
                        if (!StringUtils.hasText(ancestor)) {
                            continue;
                        }
                        try {
                            expandedMenuIds.add(Long.parseLong(ancestor.trim()));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                expandedMenuIds.add(menu.id());
            });
        }
        roleMenuMutationFacade.assignMenuIdsToRoleCode(tenantId, "ADMIN", expandedMenuIds);
    }

    private String joinAncestor(String ancestors, Long id) {
        String prefix = StringUtils.hasText(ancestors) ? ancestors : "";
        return StringUtils.hasText(prefix) ? prefix + "," + id : String.valueOf(id);
    }
}