package com.enterprise.auth.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.service.AuditService;
import com.enterprise.auth.platform.common.convention.exception.BusinessException;
import com.enterprise.auth.platform.dto.resp.MenuNode;
import com.enterprise.auth.platform.dto.model.ResourceType;
import com.enterprise.auth.platform.dao.entity.SysResourceEntity;
import com.enterprise.auth.platform.dao.entity.SysRoleEntity;
import com.enterprise.auth.platform.dao.entity.SysRoleResourceEntity;
import com.enterprise.auth.platform.dao.entity.SysTenantResourceOverrideEntity;
import com.enterprise.auth.platform.dao.mapper.SysResourceMapper;
import com.enterprise.auth.platform.dao.mapper.SysRoleMapper;
import com.enterprise.auth.platform.dao.mapper.SysRoleResourceMapper;
import com.enterprise.auth.platform.dao.mapper.SysTenantResourceOverrideMapper;
import com.enterprise.auth.platform.dto.req.CreateResourceRequest;
import com.enterprise.auth.platform.dto.req.CreateResourceRequest;
import com.enterprise.auth.platform.dto.model.ResourceTreeNode;
import com.enterprise.auth.platform.dto.model.TenantResourceOverrideItem;
import com.enterprise.auth.platform.security.SecuritySupport;
import com.enterprise.auth.platform.common.TenantContext;
import com.enterprise.auth.platform.config.TenantProperties;
import com.enterprise.auth.platform.dto.req.UpdateTenantResourceOverridesRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ResourceService {

    private final SysResourceMapper sysResourceMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysRoleResourceMapper sysRoleResourceMapper;
    private final SysTenantResourceOverrideMapper sysTenantResourceOverrideMapper;
    private final TenantProperties tenantProperties;
    private final AuditService auditService;

    public ResourceService(
            SysResourceMapper sysResourceMapper,
            SysRoleMapper sysRoleMapper,
            SysRoleResourceMapper sysRoleResourceMapper,
            SysTenantResourceOverrideMapper sysTenantResourceOverrideMapper,
            TenantProperties tenantProperties,
            AuditService auditService
    ) {
        this.sysResourceMapper = sysResourceMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysRoleResourceMapper = sysRoleResourceMapper;
        this.sysTenantResourceOverrideMapper = sysTenantResourceOverrideMapper;
        this.tenantProperties = tenantProperties;
        this.auditService = auditService;
    }

    public Set<String> resolveGrantKeys(String activeTenantId, Set<String> roleCodes, boolean superAdmin) {
        List<SysResourceEntity> template = listTemplateResources();
        Map<Long, EffectiveResource> effectiveById = mergeEffectiveResources(activeTenantId, template);
        if (effectiveById.isEmpty()) {
            return Set.of();
        }
        Set<Long> grantedIds = superAdmin
                ? new LinkedHashSet<>(effectiveById.keySet())
                : listGrantedResourceIdsByRoleCodes(activeTenantId, roleCodes);
        if (grantedIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> expanded = expandWithAncestors(grantedIds, effectiveById);
        return expanded.stream()
                .map(effectiveById::get)
                .filter(Objects::nonNull)
                .filter(resource -> hierarchyEnabled(resource, effectiveById))
                .map(EffectiveResource::grantKey)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public List<MenuNode> resolveMenuTree(String activeTenantId, Set<String> roleCodes, boolean superAdmin) {
        List<SysResourceEntity> template = listTemplateResources();
        Map<Long, EffectiveResource> effectiveById = mergeEffectiveResources(activeTenantId, template);
        if (effectiveById.isEmpty()) {
            return List.of();
        }

        Set<Long> grantedIds = superAdmin
                ? new LinkedHashSet<>(effectiveById.keySet())
                : listGrantedResourceIdsByRoleCodes(activeTenantId, roleCodes);
        if (grantedIds.isEmpty()) {
            return List.of();
        }

        Set<Long> expanded = expandWithAncestors(grantedIds, effectiveById);
        Map<Long, MenuNodeBuilder> nodes = new LinkedHashMap<>();
        for (Long resourceId : expanded) {
            EffectiveResource resource = effectiveById.get(resourceId);
            if (resource == null) {
                continue;
            }
            if (!(resource.resourceType() == ResourceType.DIR || resource.resourceType() == ResourceType.MENU)) {
                continue;
            }
            if (!hierarchyEnabled(resource, effectiveById) || !hierarchyVisible(resource, effectiveById)) {
                continue;
            }
            nodes.put(resource.id(), MenuNodeBuilder.from(resource));
        }

        if (nodes.isEmpty()) {
            return List.of();
        }

        List<MenuNodeBuilder> roots = new ArrayList<>();
        for (MenuNodeBuilder node : nodes.values()) {
            if (node.parentId == null || !nodes.containsKey(node.parentId)) {
                roots.add(node);
                continue;
            }
            nodes.get(node.parentId).children.add(node);
        }

        Comparator<MenuNodeBuilder> comparator = Comparator
                .comparingInt((MenuNodeBuilder node) -> node.orderNo == null ? Integer.MAX_VALUE : node.orderNo)
                .thenComparingLong(node -> node.id == null ? Long.MAX_VALUE : node.id);
        roots.sort(comparator);
        roots.forEach(root -> sortChildrenRecursively(root, comparator));

        List<MenuNode> tree = roots.stream().map(MenuNodeBuilder::toMenuNode).toList();
        if (tree.size() == 1 && "root".equals(tree.get(0).code())) {
            return tree.get(0).children();
        }
        return tree;
    }

    public List<ResourceTreeNode> templateTree() {
        List<SysResourceEntity> template = listTemplateResources();
        return toResourceTree(template);
    }

    public List<ResourceTreeNode> effectiveTree(String tenantId) {
        List<EffectiveResource> effective = new ArrayList<>(mergeEffectiveResources(tenantId, listTemplateResources()).values());
        return toEffectiveTree(effective);
    }

    @Transactional
    public ResourceTreeNode createResource(CreateResourceRequest request) {
        requirePlatformTenant();
        validateGrantKey(request.grantKey());
        SysResourceEntity parent = request.parentId() == null ? null : getTemplateResource(request.parentId());
        validateResourceShape(request.resourceType(), parent, request.routeKey(), request.path(), request.component(), request.grantKey());

        SysResourceEntity entity = new SysResourceEntity();
        entity.setTenantId(platformTenantId());
        entity.setParentId(request.parentId());
        entity.setAncestors(resolveAncestors(parent));
        entity.setResourceType(request.resourceType().name());
        entity.setResourceKey(request.resourceKey().trim());
        entity.setResourceName(request.resourceName().trim());
        entity.setRouteKey(blankToNull(request.routeKey()));
        entity.setGrantKey(blankToNull(request.grantKey()));
        entity.setPath(blankToNull(request.path()));
        entity.setComponent(blankToNull(request.component()));
        entity.setIcon(blankToNull(request.icon()));
        entity.setOrderNo(request.orderNo() == null ? 0 : request.orderNo());
        entity.setVisible(Boolean.FALSE.equals(request.visible()) ? 0 : 1);
        entity.setEnabled(Boolean.FALSE.equals(request.enabled()) ? 0 : 1);
        entity.setIsSystem(0);
        try {
            runWithTenant(platformTenantId(), () -> {
                sysResourceMapper.insert(entity);
                return null;
            });
        } catch (Exception ex) {
            throw new BusinessException("资源键已存在或数据不合法");
        }
        auditService.record("RESOURCE_CREATED", SecuritySupport.currentOperator(), platformTenantId(), Map.of("resourceId", entity.getId(), "resourceKey", entity.getResourceKey()));
        return toResourceNode(entity, List.of());
    }

    @Transactional
    public ResourceTreeNode updateResource(Long resourceId, CreateResourceRequest request) {
        requirePlatformTenant();
        validateGrantKey(request.grantKey());
        SysResourceEntity entity = getTemplateResource(resourceId);
        if (entity.getIsSystem() != null && entity.getIsSystem() == 1 && !Objects.equals(entity.getResourceType(), request.resourceType().name())) {
            throw new BusinessException("系统资源不允许修改资源类型");
        }
        SysResourceEntity parent = request.parentId() == null ? null : getTemplateResource(request.parentId());
        if (parent != null && parent.getId().equals(resourceId)) {
            throw new BusinessException("资源父节点不能是自身");
        }
        if (parent != null && parseAncestors(parent.getAncestors()).contains(resourceId)) {
            throw new BusinessException("资源父节点不能是当前节点的子孙节点");
        }
        validateResourceShape(request.resourceType(), parent, request.routeKey(), request.path(), request.component(), request.grantKey());

        entity.setParentId(request.parentId());
        entity.setAncestors(resolveAncestors(parent));
        entity.setResourceType(request.resourceType().name());
        entity.setResourceKey(request.resourceKey().trim());
        entity.setResourceName(request.resourceName().trim());
        entity.setRouteKey(blankToNull(request.routeKey()));
        entity.setGrantKey(blankToNull(request.grantKey()));
        entity.setPath(blankToNull(request.path()));
        entity.setComponent(blankToNull(request.component()));
        entity.setIcon(blankToNull(request.icon()));
        entity.setOrderNo(request.orderNo() == null ? 0 : request.orderNo());
        entity.setVisible(Boolean.FALSE.equals(request.visible()) ? 0 : 1);
        entity.setEnabled(Boolean.FALSE.equals(request.enabled()) ? 0 : 1);

        try {
            runWithTenant(platformTenantId(), () -> {
                sysResourceMapper.updateById(entity);
                return null;
            });
        } catch (Exception ex) {
            throw new BusinessException("资源键已存在或数据不合法");
        }

        refreshDescendantAncestors(resourceId);
        auditService.record("RESOURCE_UPDATED", SecuritySupport.currentOperator(), platformTenantId(), Map.of("resourceId", entity.getId(), "resourceKey", entity.getResourceKey()));
        return toResourceNode(entity, List.of());
    }

    @Transactional
    public void deleteResource(Long resourceId) {
        requirePlatformTenant();
        SysResourceEntity entity = getTemplateResource(resourceId);
        if (entity.getIsSystem() != null && entity.getIsSystem() == 1) {
            throw new BusinessException("系统资源不允许删除");
        }
        long children = runWithTenant(platformTenantId(), () -> sysResourceMapper.selectCount(new LambdaQueryWrapper<SysResourceEntity>()
                .eq(SysResourceEntity::getTenantId, platformTenantId())
                .eq(SysResourceEntity::getDeleted, 0)
                .eq(SysResourceEntity::getParentId, resourceId)));
        if (children > 0) {
            throw new BusinessException("请先删除子资源");
        }
        long linked = runWithTenant(platformTenantId(), () -> sysRoleResourceMapper.selectCount(new LambdaQueryWrapper<SysRoleResourceEntity>()
                .eq(SysRoleResourceEntity::getResourceId, resourceId)));
        if (linked > 0) {
            throw new BusinessException("资源已被角色使用，无法删除");
        }
        runWithTenant(platformTenantId(), () -> {
            sysResourceMapper.deleteById(resourceId);
            return null;
        });
        auditService.record("RESOURCE_DELETED", SecuritySupport.currentOperator(), platformTenantId(), Map.of("resourceId", resourceId, "resourceKey", entity.getResourceKey()));
    }

    @Transactional
    public ResourceTreeNode updateSort(Long resourceId, Integer orderNo) {
        requirePlatformTenant();
        SysResourceEntity entity = getTemplateResource(resourceId);
        entity.setOrderNo(orderNo == null ? 0 : orderNo);
        runWithTenant(platformTenantId(), () -> {
            sysResourceMapper.updateById(entity);
            return null;
        });
        auditService.record("RESOURCE_SORT_UPDATED", SecuritySupport.currentOperator(), platformTenantId(), Map.of("resourceId", resourceId, "orderNo", entity.getOrderNo()));
        return toResourceNode(entity, List.of());
    }

    public Set<Long> listRoleResourceIds(String tenantId, Long roleId) {
        return runWithTenant(tenantId, () -> sysRoleResourceMapper.selectList(new LambdaQueryWrapper<SysRoleResourceEntity>()
                        .eq(SysRoleResourceEntity::getTenantId, tenantId)
                        .eq(SysRoleResourceEntity::getRoleId, roleId))
                .stream()
                .map(SysRoleResourceEntity::getResourceId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    @Transactional
    public Set<Long> assignRoleResources(String tenantId, Long roleId, Set<Long> requestedResourceIds) {
        List<SysResourceEntity> template = listTemplateResources();
        Map<Long, SysResourceEntity> templateById = template.stream().collect(Collectors.toMap(SysResourceEntity::getId, value -> value));
        Set<Long> normalized = requestedResourceIds == null ? Set.of() : requestedResourceIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (Long resourceId : normalized) {
            if (!templateById.containsKey(resourceId)) {
                throw new BusinessException("存在无效的资源 ID");
            }
        }

        Set<Long> expanded = new LinkedHashSet<>();
        for (Long resourceId : normalized) {
            SysResourceEntity resource = templateById.get(resourceId);
            if (resource == null) {
                continue;
            }
            expanded.addAll(parseAncestors(resource.getAncestors()));
            expanded.add(resourceId);
        }

        runWithTenant(tenantId, () -> {
            sysRoleResourceMapper.delete(new LambdaQueryWrapper<SysRoleResourceEntity>()
                    .eq(SysRoleResourceEntity::getTenantId, tenantId)
                    .eq(SysRoleResourceEntity::getRoleId, roleId));
            if (!expanded.isEmpty()) {
                for (Long resourceId : expanded) {
                    SysRoleResourceEntity relation = new SysRoleResourceEntity();
                    relation.setTenantId(tenantId);
                    relation.setRoleId(roleId);
                    relation.setResourceId(resourceId);
                    sysRoleResourceMapper.insert(relation);
                }
            }
            return null;
        });
        return expanded;
    }

    public List<TenantResourceOverrideItem> listTenantOverrides(String tenantId) {
        List<SysTenantResourceOverrideEntity> overrides = runWithTenant(tenantId, () -> sysTenantResourceOverrideMapper.selectList(
                new LambdaQueryWrapper<SysTenantResourceOverrideEntity>()
                        .eq(SysTenantResourceOverrideEntity::getTenantId, tenantId)
                        .orderByAsc(SysTenantResourceOverrideEntity::getId)
        ));
        if (overrides.isEmpty()) {
            return List.of();
        }
        Map<Long, SysResourceEntity> resourceById = listTemplateResources().stream()
                .collect(Collectors.toMap(SysResourceEntity::getId, value -> value));
        return overrides.stream()
                .map(override -> {
                    SysResourceEntity resource = resourceById.get(override.getResourceId());
                    return new TenantResourceOverrideItem(
                            override.getResourceId(),
                            resource == null ? null : resource.getResourceKey(),
                            resource == null ? null : resource.getResourceName(),
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
        Map<Long, SysResourceEntity> resourceById = listTemplateResources().stream()
                .collect(Collectors.toMap(SysResourceEntity::getId, value -> value));
        runWithTenant(tenantId, () -> {
            for (UpdateTenantResourceOverridesRequest.OverrideItem item : request.overrides()) {
                if (!resourceById.containsKey(item.resourceId())) {
                    throw new BusinessException("存在无效的资源 ID");
                }
                SysTenantResourceOverrideEntity existing = sysTenantResourceOverrideMapper.selectOne(new LambdaQueryWrapper<SysTenantResourceOverrideEntity>()
                        .eq(SysTenantResourceOverrideEntity::getTenantId, tenantId)
                        .eq(SysTenantResourceOverrideEntity::getResourceId, item.resourceId())
                        .last("limit 1"));
                if (item.enabled() == null
                        && item.visible() == null
                        && item.orderNo() == null
                        && !StringUtils.hasText(item.titleOverride())
                        && !StringUtils.hasText(item.iconOverride())) {
                    if (existing != null) {
                        sysTenantResourceOverrideMapper.deleteById(existing.getId());
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
                    sysTenantResourceOverrideMapper.insert(entity);
                } else {
                    sysTenantResourceOverrideMapper.updateById(entity);
                }
            }
            return null;
        });
        auditService.record("TENANT_RESOURCE_OVERRIDE_UPDATED", SecuritySupport.currentOperator(), tenantId, Map.of("tenantId", tenantId, "count", request.overrides().size()));
        return listTenantOverrides(tenantId);
    }

    private Set<Long> listGrantedResourceIdsByRoleCodes(String tenantId, Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Set.of();
        }
        List<Long> roleIds = runWithTenant(tenantId, () -> sysRoleMapper.selectList(new LambdaQueryWrapper<SysRoleEntity>()
                        .eq(SysRoleEntity::getTenantId, tenantId)
                        .eq(SysRoleEntity::getDeleted, 0)
                        .in(SysRoleEntity::getRoleCode, roleCodes))
                .stream()
                .map(SysRoleEntity::getId)
                .distinct()
                .toList());
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        return runWithTenant(tenantId, () -> sysRoleResourceMapper.selectList(new LambdaQueryWrapper<SysRoleResourceEntity>()
                        .eq(SysRoleResourceEntity::getTenantId, tenantId)
                        .in(SysRoleResourceEntity::getRoleId, roleIds))
                .stream()
                .map(SysRoleResourceEntity::getResourceId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    private Map<Long, EffectiveResource> mergeEffectiveResources(String tenantId, List<SysResourceEntity> templateResources) {
        Map<Long, SysTenantResourceOverrideEntity> overrideMap = runWithTenant(tenantId, () -> sysTenantResourceOverrideMapper.selectList(
                        new LambdaQueryWrapper<SysTenantResourceOverrideEntity>()
                                .eq(SysTenantResourceOverrideEntity::getTenantId, tenantId))
                .stream()
                .collect(Collectors.toMap(
                        SysTenantResourceOverrideEntity::getResourceId,
                        value -> value,
                        (left, right) -> right
                )));
        Map<Long, EffectiveResource> result = new LinkedHashMap<>();
        for (SysResourceEntity resource : templateResources) {
            SysTenantResourceOverrideEntity override = overrideMap.get(resource.getId());
            boolean enabled = override != null && override.getEnabled() != null
                    ? override.getEnabled() == 1
                    : resource.getEnabled() != null && resource.getEnabled() == 1;
            boolean visible = override != null && override.getVisible() != null
                    ? override.getVisible() == 1
                    : resource.getVisible() != null && resource.getVisible() == 1;
            Integer orderNo = override != null && override.getOrderNo() != null ? override.getOrderNo() : resource.getOrderNo();
            String title = override != null && StringUtils.hasText(override.getTitleOverride()) ? override.getTitleOverride() : resource.getResourceName();
            String icon = override != null && StringUtils.hasText(override.getIconOverride()) ? override.getIconOverride() : resource.getIcon();
            result.put(resource.getId(), new EffectiveResource(
                    resource.getId(),
                    resource.getParentId(),
                    resource.getAncestors(),
                    parseType(resource.getResourceType()),
                    resource.getResourceKey(),
                    title,
                    resource.getRouteKey(),
                    resource.getGrantKey(),
                    resource.getPath(),
                    resource.getComponent(),
                    icon,
                    orderNo == null ? 0 : orderNo,
                    visible,
                    enabled,
                    resource.getIsSystem() != null && resource.getIsSystem() == 1
            ));
        }
        return result;
    }

    private List<SysResourceEntity> listTemplateResources() {
        return runWithTenant(platformTenantId(), () -> sysResourceMapper.selectList(new LambdaQueryWrapper<SysResourceEntity>()
                .eq(SysResourceEntity::getTenantId, platformTenantId())
                .eq(SysResourceEntity::getDeleted, 0)
                .orderByAsc(SysResourceEntity::getOrderNo)
                .orderByAsc(SysResourceEntity::getId)));
    }

    private boolean hierarchyEnabled(EffectiveResource resource, Map<Long, EffectiveResource> effectiveById) {
        if (!resource.enabled()) {
            return false;
        }
        for (Long ancestorId : parseAncestors(resource.ancestors())) {
            EffectiveResource ancestor = effectiveById.get(ancestorId);
            if (ancestor == null || !ancestor.enabled()) {
                return false;
            }
        }
        return true;
    }

    private boolean hierarchyVisible(EffectiveResource resource, Map<Long, EffectiveResource> effectiveById) {
        if (!resource.visible()) {
            return false;
        }
        for (Long ancestorId : parseAncestors(resource.ancestors())) {
            EffectiveResource ancestor = effectiveById.get(ancestorId);
            if (ancestor == null || !ancestor.visible()) {
                return false;
            }
        }
        return true;
    }

    private Set<Long> expandWithAncestors(Collection<Long> resourceIds, Map<Long, EffectiveResource> effectiveById) {
        Set<Long> expanded = new LinkedHashSet<>();
        for (Long resourceId : resourceIds) {
            EffectiveResource resource = effectiveById.get(resourceId);
            if (resource == null) {
                continue;
            }
            expanded.addAll(parseAncestors(resource.ancestors()));
            expanded.add(resourceId);
        }
        return expanded;
    }

    private List<ResourceTreeNode> toResourceTree(List<SysResourceEntity> resources) {
        List<EffectiveResource> converted = resources.stream().map(resource -> new EffectiveResource(
                resource.getId(),
                resource.getParentId(),
                resource.getAncestors(),
                parseType(resource.getResourceType()),
                resource.getResourceKey(),
                resource.getResourceName(),
                resource.getRouteKey(),
                resource.getGrantKey(),
                resource.getPath(),
                resource.getComponent(),
                resource.getIcon(),
                resource.getOrderNo() == null ? 0 : resource.getOrderNo(),
                resource.getVisible() != null && resource.getVisible() == 1,
                resource.getEnabled() != null && resource.getEnabled() == 1,
                resource.getIsSystem() != null && resource.getIsSystem() == 1
        )).toList();
        return toEffectiveTree(converted);
    }

    private List<ResourceTreeNode> toEffectiveTree(List<EffectiveResource> resources) {
        Map<Long, ResourceNodeBuilder> nodes = resources.stream()
                .collect(Collectors.toMap(
                        EffectiveResource::id,
                        ResourceNodeBuilder::from,
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
        List<ResourceNodeBuilder> roots = new ArrayList<>();
        for (ResourceNodeBuilder node : nodes.values()) {
            if (node.parentId == null || !nodes.containsKey(node.parentId)) {
                roots.add(node);
                continue;
            }
            nodes.get(node.parentId).children.add(node);
        }
        Comparator<ResourceNodeBuilder> comparator = Comparator
                .comparingInt((ResourceNodeBuilder node) -> node.orderNo == null ? Integer.MAX_VALUE : node.orderNo)
                .thenComparingLong(node -> node.id == null ? Long.MAX_VALUE : node.id);
        roots.sort(comparator);
        roots.forEach(root -> sortResourceChildrenRecursively(root, comparator));
        return roots.stream().map(ResourceNodeBuilder::toTreeNode).toList();
    }

    private ResourceTreeNode toResourceNode(SysResourceEntity entity, List<ResourceTreeNode> children) {
        return new ResourceTreeNode(
                entity.getId(),
                entity.getResourceKey(),
                entity.getResourceName(),
                parseType(entity.getResourceType()),
                entity.getParentId(),
                entity.getAncestors(),
                entity.getRouteKey(),
                entity.getGrantKey(),
                entity.getPath(),
                entity.getComponent(),
                entity.getIcon(),
                entity.getOrderNo() == null ? 0 : entity.getOrderNo(),
                entity.getVisible() != null && entity.getVisible() == 1,
                entity.getEnabled() != null && entity.getEnabled() == 1,
                entity.getIsSystem() != null && entity.getIsSystem() == 1,
                children
        );
    }

    private void refreshDescendantAncestors(Long parentId) {
        List<SysResourceEntity> children = runWithTenant(platformTenantId(), () -> sysResourceMapper.selectList(new LambdaQueryWrapper<SysResourceEntity>()
                .eq(SysResourceEntity::getTenantId, platformTenantId())
                .eq(SysResourceEntity::getDeleted, 0)
                .eq(SysResourceEntity::getParentId, parentId)));
        if (children.isEmpty()) {
            return;
        }
        SysResourceEntity parent = getTemplateResource(parentId);
        String ancestors = resolveAncestors(parent);
        for (SysResourceEntity child : children) {
            child.setAncestors(ancestors);
            runWithTenant(platformTenantId(), () -> {
                sysResourceMapper.updateById(child);
                return null;
            });
            refreshDescendantAncestors(child.getId());
        }
    }

    private String resolveAncestors(SysResourceEntity parent) {
        if (parent == null) {
            return "";
        }
        if (!StringUtils.hasText(parent.getAncestors())) {
            return String.valueOf(parent.getId());
        }
        return parent.getAncestors() + "," + parent.getId();
    }

    private void validateResourceShape(
            ResourceType childType,
            SysResourceEntity parent,
            String routeKey,
            String path,
            String component,
            String grantKey
    ) {
        validateParentType(childType, parent);
        if (childType == ResourceType.MENU) {
            if (!StringUtils.hasText(routeKey) || !StringUtils.hasText(path) || !StringUtils.hasText(component)) {
                throw new BusinessException("菜单资源必须配置路由标识、路径和组件");
            }
            if (!StringUtils.hasText(grantKey)) {
                throw new BusinessException("菜单资源必须配置读权限授权键");
            }
        }
        if ((childType == ResourceType.BUTTON || childType == ResourceType.API) && !StringUtils.hasText(grantKey)) {
            throw new BusinessException("按钮/API 权限必须配置授权键");
        }
    }

    private void validateParentType(ResourceType childType, SysResourceEntity parent) {
        if (parent == null) {
            if (childType == ResourceType.BUTTON) {
                throw new BusinessException("按钮权限必须挂在菜单节点下");
            }
            return;
        }
        ResourceType parentType = parseType(parent.getResourceType());
        if (parentType == ResourceType.BUTTON || parentType == ResourceType.API) {
            throw new BusinessException("按钮/API 资源不能作为父节点");
        }
        if (parentType == ResourceType.MENU && childType != ResourceType.BUTTON && childType != ResourceType.API) {
            throw new BusinessException("菜单节点下只允许挂按钮或 API 权限");
        }
        if (childType == ResourceType.BUTTON && parentType != ResourceType.MENU) {
            throw new BusinessException("按钮权限必须挂在菜单节点下");
        }
    }

    private void validateGrantKey(String grantKey) {
        if (!StringUtils.hasText(grantKey)) {
            return;
        }
        if (!grantKey.matches("^[a-zA-Z0-9]+:[a-zA-Z0-9]+(?:[:][a-zA-Z0-9_-]+)?$")) {
            throw new BusinessException("授权键格式不合法");
        }
    }

    private void requirePlatformTenant() {
        if (!platformTenantId().equals(currentTenantId())) {
            throw new BusinessException("仅平台租户允许维护资源模板");
        }
    }

    private SysResourceEntity getTemplateResource(Long resourceId) {
        SysResourceEntity entity = runWithTenant(platformTenantId(), () -> sysResourceMapper.selectOne(new LambdaQueryWrapper<SysResourceEntity>()
                .eq(SysResourceEntity::getTenantId, platformTenantId())
                .eq(SysResourceEntity::getId, resourceId)
                .eq(SysResourceEntity::getDeleted, 0)
                .last("limit 1")));
        if (entity == null) {
            throw new BusinessException("资源不存在");
        }
        return entity;
    }

    private String currentTenantId() {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : platformTenantId();
    }

    private String platformTenantId() {
        return StringUtils.hasText(tenantProperties.platformTenantId()) ? tenantProperties.platformTenantId() : "platform";
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

    private ResourceType parseType(String value) {
        try {
            return ResourceType.valueOf(value);
        } catch (Exception ignored) {
            return ResourceType.BUTTON;
        }
    }

    private Set<Long> parseAncestors(String ancestors) {
        if (!StringUtils.hasText(ancestors)) {
            return Set.of();
        }
        return java.util.Arrays.stream(ancestors.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> {
                    try {
                        return Long.parseLong(value);
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
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

    private void sortChildrenRecursively(MenuNodeBuilder node, Comparator<MenuNodeBuilder> comparator) {
        node.children.sort(comparator);
        for (MenuNodeBuilder child : node.children) {
            sortChildrenRecursively(child, comparator);
        }
    }

    private void sortResourceChildrenRecursively(ResourceNodeBuilder node, Comparator<ResourceNodeBuilder> comparator) {
        node.children.sort(comparator);
        for (ResourceNodeBuilder child : node.children) {
            sortResourceChildrenRecursively(child, comparator);
        }
    }

    private record EffectiveResource(
            Long id,
            Long parentId,
            String ancestors,
            ResourceType resourceType,
            String resourceKey,
            String resourceName,
            String routeKey,
            String grantKey,
            String path,
            String component,
            String icon,
            Integer orderNo,
            boolean visible,
            boolean enabled,
            boolean system
    ) {
    }

    private static final class MenuNodeBuilder {
        private final Long id;
        private final Long parentId;
        private final String code;
        private final String title;
        private final String path;
        private final String component;
        private final String routeKey;
        private final String icon;
        private final Integer orderNo;
        private final List<MenuNodeBuilder> children = new ArrayList<>();

        private MenuNodeBuilder(
                Long id,
                Long parentId,
                String code,
                String title,
                String path,
                String component,
                String routeKey,
                String icon,
                Integer orderNo
        ) {
            this.id = id;
            this.parentId = parentId;
            this.code = code;
            this.title = title;
            this.path = path;
            this.component = component;
            this.routeKey = routeKey;
            this.icon = icon;
            this.orderNo = orderNo;
        }

        private static MenuNodeBuilder from(EffectiveResource resource) {
            return new MenuNodeBuilder(
                    resource.id(),
                    resource.parentId(),
                    resource.resourceKey(),
                    resource.resourceName(),
                    resource.path(),
                    resource.component(),
                    resource.routeKey(),
                    resource.icon(),
                    resource.orderNo()
            );
        }

        private MenuNode toMenuNode() {
            return new MenuNode(id, code, title, path, component, routeKey, icon, orderNo, children.stream().map(MenuNodeBuilder::toMenuNode).toList());
        }
    }

    private static final class ResourceNodeBuilder {
        private final Long id;
        private final Long parentId;
        private final String resourceKey;
        private final String resourceName;
        private final ResourceType resourceType;
        private final String ancestors;
        private final String routeKey;
        private final String grantKey;
        private final String path;
        private final String component;
        private final String icon;
        private final Integer orderNo;
        private final boolean visible;
        private final boolean enabled;
        private final boolean system;
        private final List<ResourceNodeBuilder> children = new ArrayList<>();

        private ResourceNodeBuilder(
                Long id,
                Long parentId,
                String resourceKey,
                String resourceName,
                ResourceType resourceType,
                String ancestors,
                String routeKey,
                String grantKey,
                String path,
                String component,
                String icon,
                Integer orderNo,
                boolean visible,
                boolean enabled,
                boolean system
        ) {
            this.id = id;
            this.parentId = parentId;
            this.resourceKey = resourceKey;
            this.resourceName = resourceName;
            this.resourceType = resourceType;
            this.ancestors = ancestors;
            this.routeKey = routeKey;
            this.grantKey = grantKey;
            this.path = path;
            this.component = component;
            this.icon = icon;
            this.orderNo = orderNo;
            this.visible = visible;
            this.enabled = enabled;
            this.system = system;
        }

        private static ResourceNodeBuilder from(EffectiveResource resource) {
            return new ResourceNodeBuilder(
                    resource.id(),
                    resource.parentId(),
                    resource.resourceKey(),
                    resource.resourceName(),
                    resource.resourceType(),
                    resource.ancestors(),
                    resource.routeKey(),
                    resource.grantKey(),
                    resource.path(),
                    resource.component(),
                    resource.icon(),
                    resource.orderNo(),
                    resource.visible(),
                    resource.enabled(),
                    resource.system()
            );
        }

        private ResourceTreeNode toTreeNode() {
            return new ResourceTreeNode(
                    id,
                    resourceKey,
                    resourceName,
                    resourceType,
                    parentId,
                    ancestors,
                    routeKey,
                    grantKey,
                    path,
                    component,
                    icon,
                    orderNo,
                    visible,
                    enabled,
                    system,
                    children.stream().map(ResourceNodeBuilder::toTreeNode).toList()
            );
        }
    }
}
