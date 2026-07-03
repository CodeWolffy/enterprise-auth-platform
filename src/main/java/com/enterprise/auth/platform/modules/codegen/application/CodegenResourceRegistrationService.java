package com.enterprise.auth.platform.modules.codegen.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.auth.application.AuthPermissionSnapshotInvalidationService;
import com.enterprise.auth.platform.modules.menu.application.MenuTemplateMutationFacade;
import com.enterprise.auth.platform.modules.menu.application.MenuTemplateMutationFacade.MenuTemplateMutation;
import com.enterprise.auth.platform.modules.menu.application.MenuTemplateMutationFacade.MenuTemplateNode;
import com.enterprise.auth.platform.modules.role.application.RoleMenuMutationFacade;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantMenuEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantMenuMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.TenantProperties;
import java.util.ArrayList;
import java.util.Collection;
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
    private static final List<GeneratedAction> GENERATED_ACTIONS = List.of(
            new GeneratedAction("page", "列表", 100),
            new GeneratedAction("get", "查询", 110),
            new GeneratedAction("add", "新增", 120),
            new GeneratedAction("edit", "修改", 130),
            new GeneratedAction("del", "删除", 140)
    );

    private final MenuTemplateMutationFacade menuTemplateMutationFacade;
    private final RoleMenuMutationFacade roleMenuMutationFacade;
    private final SysTenantMenuMapper sysTenantMenuMapper;
    private final AuthPermissionSnapshotInvalidationService permissionSnapshotInvalidationService;
    private final TenantProperties tenantProperties;

    public CodegenResourceRegistrationService(
            MenuTemplateMutationFacade menuTemplateMutationFacade,
            RoleMenuMutationFacade roleMenuMutationFacade,
            SysTenantMenuMapper sysTenantMenuMapper,
            AuthPermissionSnapshotInvalidationService permissionSnapshotInvalidationService,
            TenantProperties tenantProperties
    ) {
        this.menuTemplateMutationFacade = menuTemplateMutationFacade;
        this.roleMenuMutationFacade = roleMenuMutationFacade;
        this.sysTenantMenuMapper = sysTenantMenuMapper;
        this.permissionSnapshotInvalidationService = permissionSnapshotInvalidationService;
        this.tenantProperties = tenantProperties;
    }

    @Transactional
    public List<String> register(String tableName, String moduleName, String title) {
        String safeModule = moduleName == null ? "" : moduleName.trim();
        if (!safeModule.matches("^[A-Za-z][A-Za-z0-9_]*$")) {
            throw new BusinessException("VALIDATION_ERROR", "moduleName 格式不合法");
        }
        String templateTenantId = platformTenantId();
        String grantTenantId = activeTenantId();
        Long menuId = ensureMenuWithParents(templateTenantId, tableName, safeModule, title);
        List<Long> actionIds = ensureActions(templateTenantId, menuId, safeModule, title);

        List<String> grantKeys = GENERATED_ACTIONS.stream()
                .map(action -> safeModule + ":" + action.code())
                .toList();
        List<Long> directMenuIds = new ArrayList<>();
        directMenuIds.add(menuId);
        directMenuIds.addAll(actionIds);
        Set<Long> assignedMenuIds = expandWithAncestors(directMenuIds);
        assignToTenant(grantTenantId, assignedMenuIds);
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

    private Long ensureMenuWithParents(String tenantId, String tableName, String moduleName, String title) {
        ensureParentMenu(tenantId);
        return ensureMenu(tenantId, tableName, moduleName, title);
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
                null,
                "Files",
                75
        ));
    }

    private List<Long> ensureActions(String tenantId, Long parentId, String moduleName, String title) {
        List<Long> ids = new ArrayList<>();
        String labelPrefix = StringUtils.hasText(title) ? title : moduleName;
        for (GeneratedAction action : GENERATED_ACTIONS) {
            ids.add(ensureAction(tenantId, parentId, moduleName + ":" + action.code(), labelPrefix + action.label(), action.sort()));
        }
        return ids;
    }

    private Long ensureAction(String tenantId, Long parentId, String permission, String name, int sort) {
        MenuTemplateNode existing = menuTemplateMutationFacade.findByKeyAndType(tenantId, permission, "1").orElse(null);
        if (existing != null) {
            return existing.id();
        }
        return menuTemplateMutationFacade.create(new MenuTemplateMutation(
                tenantId,
                parentId,
                "1",
                name,
                permission,
                null,
                null,
                null,
                sort
        ));
    }

    private Long ensureMenu(String tenantId, String tableName, String moduleName, String title) {
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
                generatedComponent(tableName, title, moduleName),
                "Files",
                100
        ));
    }

    private record GeneratedAction(String code, String label, int sort) {
    }

    private Set<Long> expandWithAncestors(Collection<Long> menuIds) {
        Map<Long, MenuTemplateNode> knownNodes = new LinkedHashMap<>();
        for (Long menuId : menuIds) {
            menuTemplateMutationFacade.findById(platformTenantId(), menuId).ifPresent(node -> knownNodes.put(node.id(), node));
        }
        Set<Long> expandedMenuIds = new LinkedHashSet<>();
        for (MenuTemplateNode node : List.copyOf(knownNodes.values())) {
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
        return expandedMenuIds;
    }

    private void assignToTenant(String tenantId, Collection<Long> menuIds) {
        if (!StringUtils.hasText(tenantId) || menuIds == null || menuIds.isEmpty()) {
            return;
        }
        TenantContext.runWithTenant(tenantId, () -> {
            for (Long menuId : menuIds) {
                if (menuId == null) {
                    continue;
                }
                Long existing = sysTenantMenuMapper.selectCount(new LambdaQueryWrapper<SysTenantMenuEntity>()
                        .eq(SysTenantMenuEntity::getTenantId, tenantId)
                        .eq(SysTenantMenuEntity::getMenuId, menuId));
                if (existing != null && existing > 0) {
                    continue;
                }
                SysTenantMenuEntity relation = new SysTenantMenuEntity();
                relation.setTenantId(tenantId);
                relation.setMenuId(menuId);
                sysTenantMenuMapper.insert(relation);
            }
            return null;
        });
    }

    private void assignToAdminRole(String tenantId, Collection<Long> menuIds) {
        roleMenuMutationFacade.assignMenuIdsToRoleCode(tenantId, "ADMIN", menuIds);
    }

    private String generatedComponent(String tableName, String title, String moduleName) {
        String kebabName = toKebab(StringUtils.hasText(title) ? title : moduleName);
        String prefix = StringUtils.hasText(tableName) && tableName.trim().startsWith("sys_") ? "upms/" : "";
        return prefix + kebabName + "/index";
    }

    private String toKebab(String value) {
        String source = StringUtils.hasText(value) ? value.trim() : "generated";
        String kebab = source
                .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1-$2")
                .replace('_', '-')
                .toLowerCase();
        return kebab.replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
    }
}
