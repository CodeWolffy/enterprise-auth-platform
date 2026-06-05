package com.enterprise.auth.platform.modules.codegen.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.auth.infrastructure.AuthPrincipalCacheService;
import com.enterprise.auth.platform.modules.resource.infrastructure.entity.SysResourceEntity;
import com.enterprise.auth.platform.modules.resource.infrastructure.mapper.SysResourceMapper;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleEntity;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleResourceEntity;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMapper;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleResourceMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.TenantProperties;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CodegenResourceRegistrationService {

    private static final String GENERATED_MENU_PARENT_KEY = "generated";
    private static final String GENERATED_API_PARENT_KEY = "api.generated";

    private final SysResourceMapper sysResourceMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysRoleResourceMapper sysRoleResourceMapper;
    private final AuthPrincipalCacheService authPrincipalCacheService;
    private final TenantProperties tenantProperties;

    public CodegenResourceRegistrationService(
            SysResourceMapper sysResourceMapper,
            SysRoleMapper sysRoleMapper,
            SysRoleResourceMapper sysRoleResourceMapper,
            AuthPrincipalCacheService authPrincipalCacheService,
            TenantProperties tenantProperties
    ) {
        this.sysResourceMapper = sysResourceMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysRoleResourceMapper = sysRoleResourceMapper;
        this.authPrincipalCacheService = authPrincipalCacheService;
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
        Long menuId = runWithTenant(templateTenantId, () -> {
            ensureParentMenu(templateTenantId, GENERATED_MENU_PARENT_KEY, "已生成模块");
            ensureParentApi(templateTenantId);
            return ensureMenu(templateTenantId, safeModule, title);
        });
        Long readApiId = runWithTenant(templateTenantId, () -> ensureApi(templateTenantId, "api.generated." + safeModule + ".read", safeModule + " 读", safeModule + ":read", 900));
        Long writeApiId = runWithTenant(templateTenantId, () -> ensureApi(templateTenantId, "api.generated." + safeModule + ".write", safeModule + " 写", safeModule + ":write", 910));

        List<String> grantKeys = new ArrayList<>();
        grantKeys.add(safeModule + ":read");
        grantKeys.add(safeModule + ":write");
        runWithTenant(grantTenantId, () -> {
            assignToAdminRole(grantTenantId, menuId);
            assignToAdminRole(grantTenantId, readApiId);
            assignToAdminRole(grantTenantId, writeApiId);
            return null;
        });
        authPrincipalCacheService.evictAll();
        return grantKeys;
    }

    private String activeTenantId() {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : platformTenantId();
    }

    private String platformTenantId() {
        return StringUtils.hasText(tenantProperties.platformTenantId()) ? tenantProperties.platformTenantId() : "platform";
    }

    private <T> T runWithTenant(String tenantId, Supplier<T> supplier) {
        String previousTenantId = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenantId);
            return supplier.get();
        } finally {
            if (StringUtils.hasText(previousTenantId)) {
                TenantContext.setTenantId(previousTenantId);
            } else {
                TenantContext.clear();
            }
        }
    }

    private void ensureParentMenu(String tenantId, String resourceKey, String resourceName) {
        SysResourceEntity existing = sysResourceMapper.selectOne(new LambdaQueryWrapper<SysResourceEntity>()
                .eq(SysResourceEntity::getTenantId, tenantId)
                .eq(SysResourceEntity::getResourceKey, resourceKey)
                .eq(SysResourceEntity::getResourceType, "DIR")
                .eq(SysResourceEntity::getDeleted, 0)
                .last("limit 1"));
        if (existing != null) {
            return;
        }
        SysResourceEntity platformManagement = sysResourceMapper.selectOne(new LambdaQueryWrapper<SysResourceEntity>()
                .eq(SysResourceEntity::getTenantId, tenantId)
                .eq(SysResourceEntity::getResourceKey, "platform-management")
                .eq(SysResourceEntity::getDeleted, 0)
                .last("limit 1"));
        SysResourceEntity entity = new SysResourceEntity();
        entity.setTenantId(tenantId);
        entity.setParentId(platformManagement == null ? null : platformManagement.getId());
        entity.setAncestors(platformManagement == null ? "" : platformManagement.getAncestors() + "," + platformManagement.getId());
        entity.setResourceType("DIR");
        entity.setResourceKey(resourceKey);
        entity.setResourceName(resourceName);
        entity.setIcon("Files");
        entity.setOrderNo(75);
        entity.setVisible(1);
        entity.setEnabled(1);
        entity.setIsSystem(1);
        sysResourceMapper.insert(entity);
    }

    private void ensureParentApi(String tenantId) {
        SysResourceEntity existing = sysResourceMapper.selectOne(new LambdaQueryWrapper<SysResourceEntity>()
                .eq(SysResourceEntity::getTenantId, tenantId)
                .eq(SysResourceEntity::getResourceKey, GENERATED_API_PARENT_KEY)
                .eq(SysResourceEntity::getResourceType, "DIR")
                .eq(SysResourceEntity::getDeleted, 0)
                .last("limit 1"));
        if (existing != null) {
            return;
        }
        SysResourceEntity apiParent = sysResourceMapper.selectOne(new LambdaQueryWrapper<SysResourceEntity>()
                .eq(SysResourceEntity::getTenantId, tenantId)
                .eq(SysResourceEntity::getResourceKey, "api")
                .eq(SysResourceEntity::getDeleted, 0)
                .last("limit 1"));
        if (apiParent == null) {
            return;
        }
        SysResourceEntity entity = new SysResourceEntity();
        entity.setTenantId(tenantId);
        entity.setParentId(apiParent.getId());
        entity.setAncestors(apiParent.getAncestors() + "," + apiParent.getId());
        entity.setResourceType("DIR");
        entity.setResourceKey("api.generated");
        entity.setResourceName("生成模块 API");
        entity.setOrderNo(990);
        entity.setVisible(0);
        entity.setEnabled(1);
        entity.setIsSystem(1);
        sysResourceMapper.insert(entity);
    }

    private Long ensureMenu(String tenantId, String moduleName, String title) {
        String menuKey = "generated." + moduleName;
        SysResourceEntity existing = sysResourceMapper.selectOne(new LambdaQueryWrapper<SysResourceEntity>()
                .eq(SysResourceEntity::getTenantId, tenantId)
                .eq(SysResourceEntity::getResourceKey, menuKey)
                .eq(SysResourceEntity::getDeleted, 0)
                .last("limit 1"));
        if (existing != null) {
            return existing.getId();
        }
        SysResourceEntity parent = sysResourceMapper.selectOne(new LambdaQueryWrapper<SysResourceEntity>()
                .eq(SysResourceEntity::getTenantId, tenantId)
                .eq(SysResourceEntity::getResourceKey, GENERATED_MENU_PARENT_KEY)
                .eq(SysResourceEntity::getDeleted, 0)
                .last("limit 1"));
        if (parent == null) {
            throw new BusinessException("PARENT_MENU_MISSING", "已生成模块父菜单未就绪");
        }
        SysResourceEntity entity = new SysResourceEntity();
        entity.setTenantId(tenantId);
        entity.setParentId(parent.getId());
        entity.setAncestors(parent.getAncestors() + "," + parent.getId());
        entity.setResourceType("MENU");
        entity.setResourceKey(menuKey);
        entity.setResourceName((StringUtils.hasText(title) ? title : moduleName) + "（生成产物）");
        entity.setRouteKey(menuKey);
        entity.setGrantKey(moduleName + ":read");
        entity.setPath("/platform/generated/" + moduleName);
        entity.setComponent("CodegenView");
        entity.setIcon("Files");
        entity.setOrderNo(100);
        entity.setVisible(1);
        entity.setEnabled(1);
        entity.setIsSystem(0);
        sysResourceMapper.insert(entity);
        return entity.getId();
    }

    private Long ensureApi(String tenantId, String resourceKey, String resourceName, String grantKey, int orderNo) {
        SysResourceEntity existing = sysResourceMapper.selectOne(new LambdaQueryWrapper<SysResourceEntity>()
                .eq(SysResourceEntity::getTenantId, tenantId)
                .eq(SysResourceEntity::getResourceKey, resourceKey)
                .eq(SysResourceEntity::getDeleted, 0)
                .last("limit 1"));
        if (existing != null) {
            return existing.getId();
        }
        SysResourceEntity parent = sysResourceMapper.selectOne(new LambdaQueryWrapper<SysResourceEntity>()
                .eq(SysResourceEntity::getTenantId, tenantId)
                .eq(SysResourceEntity::getResourceKey, "api.generated")
                .eq(SysResourceEntity::getDeleted, 0)
                .last("limit 1"));
        SysResourceEntity entity = new SysResourceEntity();
        entity.setTenantId(tenantId);
        entity.setParentId(parent == null ? null : parent.getId());
        entity.setAncestors(parent == null ? "" : (parent.getAncestors() + "," + parent.getId()));
        entity.setResourceType("API");
        entity.setResourceKey(resourceKey);
        entity.setResourceName(resourceName);
        entity.setGrantKey(grantKey);
        entity.setOrderNo(orderNo);
        entity.setVisible(0);
        entity.setEnabled(1);
        entity.setIsSystem(0);
        sysResourceMapper.insert(entity);
        return entity.getId();
    }

    private void assignToAdminRole(String tenantId, Long resourceId) {
        SysRoleEntity adminRole = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRoleEntity>()
                .eq(SysRoleEntity::getTenantId, tenantId)
                .eq(SysRoleEntity::getRoleCode, "ADMIN")
                .eq(SysRoleEntity::getDeleted, 0)
                .last("limit 1"));
        if (adminRole == null) {
            return;
        }
        Set<String> ancestorIds = new LinkedHashSet<>();
        SysResourceEntity resource = runWithTenant(platformTenantId(), () -> sysResourceMapper.selectById(resourceId));
        if (resource != null) {
            if (StringUtils.hasText(resource.getAncestors())) {
                for (String ancestor : resource.getAncestors().split(",")) {
                    if (StringUtils.hasText(ancestor)) {
                        ancestorIds.add(ancestor.trim());
                    }
                }
            }
        }
        ancestorIds.add(String.valueOf(resourceId));
        for (String ancestorId : ancestorIds) {
            Long aid;
            try {
                aid = Long.parseLong(ancestorId);
            } catch (NumberFormatException ex) {
                continue;
            }
            Long existing = sysRoleResourceMapper.selectCount(new LambdaQueryWrapper<SysRoleResourceEntity>()
                    .eq(SysRoleResourceEntity::getTenantId, tenantId)
                    .eq(SysRoleResourceEntity::getRoleId, adminRole.getId())
                    .eq(SysRoleResourceEntity::getResourceId, aid));
            if (existing == null || existing == 0) {
                SysRoleResourceEntity relation = new SysRoleResourceEntity();
                relation.setTenantId(tenantId);
                relation.setRoleId(adminRole.getId());
                relation.setResourceId(aid);
                sysRoleResourceMapper.insert(relation);
            }
        }
    }
}