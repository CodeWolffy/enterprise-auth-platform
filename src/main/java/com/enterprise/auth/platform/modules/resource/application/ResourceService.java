package com.enterprise.auth.platform.modules.resource.application;

import com.enterprise.auth.platform.common.authz.SecuritySupport;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.audit.application.AuditService;
import com.enterprise.auth.platform.modules.auth.application.AuthPermissionSnapshotInvalidationService;
import com.enterprise.auth.platform.modules.menu.application.MenuService;
import com.enterprise.auth.platform.modules.menu.domain.MenuTreeNode;
import com.enterprise.auth.platform.modules.menu.domain.MenuType;
import com.enterprise.auth.platform.modules.menu.interfaces.CreateMenuRequest;
import com.enterprise.auth.platform.modules.resource.domain.ResourceTreeNode;
import com.enterprise.auth.platform.modules.resource.domain.ResourceType;
import com.enterprise.auth.platform.modules.resource.interfaces.CreateResourceRequest;
import com.enterprise.auth.platform.modules.tenant.application.TenantResourceOverrideFacade;
import com.enterprise.auth.platform.modules.tenant.domain.TenantResourceOverrideItem;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantResourceOverrideEntity;
import com.enterprise.auth.platform.modules.tenant.interfaces.UpdateTenantResourceOverridesRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Deprecated
public class ResourceService {

    private final MenuService menuService;
    private final TenantResourceOverrideFacade tenantResourceOverrideFacade;
    private final AuditService auditService;
    private final AuthPermissionSnapshotInvalidationService permissionSnapshotInvalidationService;

    public ResourceService(
            MenuService menuService,
            TenantResourceOverrideFacade tenantResourceOverrideFacade,
            AuditService auditService,
            AuthPermissionSnapshotInvalidationService permissionSnapshotInvalidationService
    ) {
        this.menuService = menuService;
        this.tenantResourceOverrideFacade = tenantResourceOverrideFacade;
        this.auditService = auditService;
        this.permissionSnapshotInvalidationService = permissionSnapshotInvalidationService;
    }

    public List<ResourceTreeNode> templateTree() {
        return menuService.templateTree().stream()
                .map(this::toResourceNode)
                .toList();
    }

    @Transactional
    public ResourceTreeNode createResource(CreateResourceRequest request) {
        return toResourceNode(menuService.create(toMenuRequest(request)));
    }

    @Transactional
    public ResourceTreeNode updateResource(Long resourceId, CreateResourceRequest request) {
        try {
            return toResourceNode(menuService.update(resourceId, toMenuRequest(request)));
        } catch (BusinessException ex) {
            throw translateLegacyResourceMessage(ex);
        }
    }

    @Transactional
    public void deleteResource(Long resourceId) {
        menuService.delete(resourceId);
    }

    @Transactional
    public ResourceTreeNode updateSort(Long resourceId, Integer orderNo) {
        return toResourceNode(menuService.updateSort(resourceId, orderNo));
    }

    public List<TenantResourceOverrideItem> listTenantOverrides(String tenantId) {
        List<SysTenantResourceOverrideEntity> overrides = runWithTenant(tenantId, () -> tenantResourceOverrideFacade.listOverrides(tenantId));
        if (overrides.isEmpty()) {
            return List.of();
        }
        Map<Long, MenuTreeNode> menuById = templateMenuById();
        return overrides.stream()
                .map(override -> {
                    MenuTreeNode menu = menuById.get(override.getResourceId());
                    return new TenantResourceOverrideItem(
                            override.getResourceId(),
                            menu == null ? null : menu.resourceKey(),
                            menu == null ? null : menu.menuName(),
                            toBoolean(override.getEnabled()),
                            toBoolean(override.getVisible()),
                            override.getOrderNo(),
                            override.getTitleOverride(),
                            override.getIconOverride()
                    );
                })
                .toList();
    }

    @Transactional
    public List<TenantResourceOverrideItem> updateTenantOverrides(String tenantId, UpdateTenantResourceOverridesRequest request) {
        Map<Long, MenuTreeNode> menuById = templateMenuById();
        runWithTenant(tenantId, () -> {
            for (UpdateTenantResourceOverridesRequest.OverrideItem item : request.overrides()) {
                if (!menuById.containsKey(item.resourceId())) {
                    throw new BusinessException("存在无效的菜单权限 ID");
                }
                SysTenantResourceOverrideEntity existing = tenantResourceOverrideFacade.findByResourceId(tenantId, item.resourceId());
                if (item.enabled() == null
                        && item.visible() == null
                        && item.orderNo() == null
                        && !StringUtils.hasText(item.titleOverride())
                        && !StringUtils.hasText(item.iconOverride())) {
                    if (existing != null) {
                        tenantResourceOverrideFacade.deleteById(existing.getId());
                    }
                    continue;
                }
                SysTenantResourceOverrideEntity entity = existing == null ? new SysTenantResourceOverrideEntity() : existing;
                entity.setTenantId(tenantId);
                entity.setResourceId(item.resourceId());
                entity.setEnabled(toFlag(item.enabled()));
                entity.setVisible(toFlag(item.visible()));
                entity.setOrderNo(item.orderNo());
                entity.setTitleOverride(blankToNull(item.titleOverride()));
                entity.setIconOverride(blankToNull(item.iconOverride()));
                if (existing == null) {
                    tenantResourceOverrideFacade.insert(entity);
                } else {
                    tenantResourceOverrideFacade.updateById(entity);
                }
            }
            return null;
        });
        permissionSnapshotInvalidationService.invalidateAll();
        auditService.record("TENANT_RESOURCE_OVERRIDE_UPDATED", SecuritySupport.currentOperator(), tenantId,
                Map.of("tenantId", tenantId, "count", request.overrides().size()));
        return listTenantOverrides(tenantId);
    }

    private CreateMenuRequest toMenuRequest(CreateResourceRequest request) {
        return new CreateMenuRequest(
                request.parentId(),
                MenuType.valueOf(request.resourceType().name()),
                request.resourceKey(),
                request.resourceName(),
                request.routeKey(),
                request.grantKey(),
                request.path(),
                request.component(),
                null,
                request.icon(),
                request.orderNo(),
                request.visible(),
                request.enabled(),
                false,
                null
        );
    }

    private ResourceTreeNode toResourceNode(MenuTreeNode menu) {
        return new ResourceTreeNode(
                menu.id(),
                menu.resourceKey(),
                menu.menuName(),
                ResourceType.valueOf(menu.menuType()),
                menu.parentId(),
                menu.ancestors(),
                menu.routeKey(),
                menu.grantKey(),
                menu.path(),
                menu.component(),
                menu.icon(),
                menu.orderNo(),
                menu.visible(),
                menu.enabled(),
                menu.system(),
                menu.children() == null ? List.of() : menu.children().stream().map(this::toResourceNode).toList()
        );
    }

    private Map<Long, MenuTreeNode> templateMenuById() {
        Map<Long, MenuTreeNode> result = new LinkedHashMap<>();
        for (MenuTreeNode root : menuService.templateTree()) {
            flatten(root, result);
        }
        return result;
    }

    private void flatten(MenuTreeNode node, Map<Long, MenuTreeNode> result) {
        if (node.id() != null) {
            result.put(node.id(), node);
        }
        List<MenuTreeNode> children = node.children() == null ? List.of() : new ArrayList<>(node.children());
        for (MenuTreeNode child : children) {
            flatten(child, result);
        }
    }

    private Integer toFlag(Boolean value) {
        if (value == null) {
            return null;
        }
        return value ? 1 : 0;
    }

    private Boolean toBoolean(Integer value) {
        if (value == null) {
            return null;
        }
        return value == 1;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BusinessException translateLegacyResourceMessage(BusinessException ex) {
        if ("系统节点不允许修改资源唯一标识".equals(ex.getMessage())) {
            return new BusinessException(ex.code(), "系统资源不允许修改资源键", ex.status(), ex.details());
        }
        return ex;
    }

    private <T> T runWithTenant(String tenantId, Supplier<T> supplier) {
        String previous = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenantId);
            return supplier.get();
        } finally {
            if (StringUtils.hasText(previous)) {
                TenantContext.setTenantId(previous);
            } else {
                TenantContext.clear();
            }
        }
    }
}