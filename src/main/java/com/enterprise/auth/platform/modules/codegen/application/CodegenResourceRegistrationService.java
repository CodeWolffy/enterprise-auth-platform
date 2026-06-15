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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CodegenResourceRegistrationService {

    private static final String GENERATED_MENU_PARENT_PATH = "/platform/generated";
    private static final String GENERATED_API_PARENT_PERMISSION = "upms:generated";

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
        Long readApiId = ensureApi(templateTenantId, safeModule + ":read", safeModule + " 读", 900);
        Long writeApiId = ensureApi(templateTenantId, safeModule + ":write", safeModule + " 写", 910);

        List<String> grantKeys = new ArrayList<>();
        grantKeys.add(safeModule + ":read");
        grantKeys.add(safeModule + ":write");
        assignToAdminRole(grantTenantId, List.of(menuId, readApiId, writeApiId));
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
        ensureParentMenu(tenantId);
        ensureParentApi(tenantId);
        return ensureMenu(tenantId, moduleName, title);
    }

    private void ensureParentMenu(String tenantId) {
        if (menuTemplateMutationFacade.findByKeyAndType(tenantId, GENERATED_MENU_PARENT_PATH, "0").isPresent()) {
            return;
        }
        MenuTemplateNode platformManagement = menuTemplateMutationFacade.findByKey(tenantId, "/platform").orElse(null);
        menuTemplateMutationFacade.create(new MenuTemplateMutation(
                tenantId,
                platformManagement == null ? null : platformManagement.id(),
                "0",
                "已生成模块",
                null,
                GENERATED_MENU_PARENT_PATH,
                "Layout",
                "Files",
                75
        ));
    }

    private void ensureParentApi(String tenantId) {
        if (menuTemplateMutationFacade.findByKeyAndType(tenantId, GENERATED_API_PARENT_PERMISSION, "1").isPresent()) {
            return;
        }
        menuTemplateMutationFacade.create(new MenuTemplateMutation(
                tenantId,
                null,
                "1",
                "生成模块 API",
                GENERATED_API_PARENT_PERMISSION,
                null,
                null,
                null,
                990
        ));
    }

    private Long ensureMenu(String tenantId, String moduleName, String title) {
        String path = GENERATED_MENU_PARENT_PATH + "/" + moduleName;
        MenuTemplateNode existing = menuTemplateMutationFacade.findByKey(tenantId, path).orElse(null);
        if (existing != null) {
            return existing.id();
        }
        MenuTemplateNode parent = menuTemplateMutationFacade.findByKey(tenantId, GENERATED_MENU_PARENT_PATH)
                .orElseThrow(() -> new BusinessException("PARENT_MENU_MISSING", "已生成模块父菜单未就绪"));
        return menuTemplateMutationFacade.create(new MenuTemplateMutation(
                tenantId,
                parent.id(),
                "0",
                (StringUtils.hasText(title) ? title : moduleName) + "（生成产物）",
                null,
                path,
                "CodegenView",
                "Files",
                100
        ));
    }

    private Long ensureApi(String tenantId, String permission, String name, int sort) {
        MenuTemplateNode existing = menuTemplateMutationFacade.findByKey(tenantId, permission).orElse(null);
        if (existing != null) {
            return existing.id();
        }
        MenuTemplateNode parent = menuTemplateMutationFacade.findByKey(tenantId, GENERATED_API_PARENT_PERMISSION).orElse(null);
        return menuTemplateMutationFacade.create(new MenuTemplateMutation(
                tenantId,
                parent == null ? null : parent.id(),
                "1",
                name,
                permission,
                null,
                null,
                null,
                sort
        ));
    }

    private void assignToAdminRole(String tenantId, List<Long> menuIds) {
        Map<Long, MenuTemplateNode> knownNodes = new LinkedHashMap<>();
        for (Long menuId : menuIds) {
            menuTemplateMutationFacade.findById(platformTenantId(), menuId).ifPresent(node -> knownNodes.put(node.id(), node));
        }
        Set<Long> expandedMenuIds = new LinkedHashSet<>();
        for (MenuTemplateNode node : knownNodes.values()) {
            Long parentId = node.parentId();
            while (parentId != null) {
                Long currentParentId = parentId;
                MenuTemplateNode parent = knownNodes.computeIfAbsent(currentParentId,
                        id -> menuTemplateMutationFacade.findById(platformTenantId(), id).orElse(null));
                if (parent == null) {
                    break;
                }
                expandedMenuIds.add(parent.id());
                parentId = parent.parentId();
            }
            expandedMenuIds.add(node.id());
        }
        roleMenuMutationFacade.assignMenuIdsToRoleCode(tenantId, "ADMIN", expandedMenuIds);
    }
}