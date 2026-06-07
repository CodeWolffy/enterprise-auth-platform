package com.enterprise.auth.platform.modules.codegen.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.auth.application.AuthPermissionSnapshotInvalidationService;
import com.enterprise.auth.platform.modules.menu.infrastructure.entity.SysMenuEntity;
import com.enterprise.auth.platform.modules.menu.infrastructure.mapper.SysMenuMapper;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleEntity;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleMenuEntity;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMapper;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMenuMapper;
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

    private final SysMenuMapper sysMenuMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final AuthPermissionSnapshotInvalidationService permissionSnapshotInvalidationService;
    private final TenantProperties tenantProperties;

    public CodegenResourceRegistrationService(
            SysMenuMapper sysMenuMapper,
            SysRoleMapper sysRoleMapper,
            SysRoleMenuMapper sysRoleMenuMapper,
            AuthPermissionSnapshotInvalidationService permissionSnapshotInvalidationService,
            TenantProperties tenantProperties
    ) {
        this.sysMenuMapper = sysMenuMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
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
        SysMenuEntity existing = sysMenuMapper.selectOne(new LambdaQueryWrapper<SysMenuEntity>()
                .eq(SysMenuEntity::getTenantId, tenantId)
                .eq(SysMenuEntity::getResourceKey, resourceKey)
                .eq(SysMenuEntity::getMenuType, "DIR")
                .eq(SysMenuEntity::getDeleted, 0)
                .last("limit 1"));
        if (existing != null) {
            return;
        }
        SysMenuEntity platformManagement = sysMenuMapper.selectOne(new LambdaQueryWrapper<SysMenuEntity>()
                .eq(SysMenuEntity::getTenantId, tenantId)
                .eq(SysMenuEntity::getResourceKey, "platform-management")
                .eq(SysMenuEntity::getDeleted, 0)
                .last("limit 1"));
        SysMenuEntity entity = new SysMenuEntity();
        entity.setTenantId(tenantId);
        entity.setParentId(platformManagement == null ? null : platformManagement.getId());
        entity.setAncestors(platformManagement == null ? "" : platformManagement.getAncestors() + "," + platformManagement.getId());
        entity.setMenuType("DIR");
        entity.setResourceKey(resourceKey);
        entity.setMenuName(resourceName);
        entity.setIcon("Files");
        entity.setOrderNo(75);
        entity.setVisible(1);
        entity.setEnabled(1);
        entity.setIsSystem(1);
        sysMenuMapper.insert(entity);
    }

    private void ensureParentApi(String tenantId) {
        SysMenuEntity existing = sysMenuMapper.selectOne(new LambdaQueryWrapper<SysMenuEntity>()
                .eq(SysMenuEntity::getTenantId, tenantId)
                .eq(SysMenuEntity::getResourceKey, GENERATED_API_PARENT_KEY)
                .eq(SysMenuEntity::getMenuType, "DIR")
                .eq(SysMenuEntity::getDeleted, 0)
                .last("limit 1"));
        if (existing != null) {
            return;
        }
        SysMenuEntity apiParent = sysMenuMapper.selectOne(new LambdaQueryWrapper<SysMenuEntity>()
                .eq(SysMenuEntity::getTenantId, tenantId)
                .eq(SysMenuEntity::getResourceKey, "api")
                .eq(SysMenuEntity::getDeleted, 0)
                .last("limit 1"));
        if (apiParent == null) {
            return;
        }
        SysMenuEntity entity = new SysMenuEntity();
        entity.setTenantId(tenantId);
        entity.setParentId(apiParent.getId());
        entity.setAncestors(apiParent.getAncestors() + "," + apiParent.getId());
        entity.setMenuType("DIR");
        entity.setResourceKey("api.generated");
        entity.setMenuName("生成模块 API");
        entity.setOrderNo(990);
        entity.setVisible(0);
        entity.setEnabled(1);
        entity.setIsSystem(1);
        sysMenuMapper.insert(entity);
    }

    private Long ensureMenu(String tenantId, String moduleName, String title) {
        String menuKey = "generated." + moduleName;
        SysMenuEntity existing = sysMenuMapper.selectOne(new LambdaQueryWrapper<SysMenuEntity>()
                .eq(SysMenuEntity::getTenantId, tenantId)
                .eq(SysMenuEntity::getResourceKey, menuKey)
                .eq(SysMenuEntity::getDeleted, 0)
                .last("limit 1"));
        if (existing != null) {
            return existing.getId();
        }
        SysMenuEntity parent = sysMenuMapper.selectOne(new LambdaQueryWrapper<SysMenuEntity>()
                .eq(SysMenuEntity::getTenantId, tenantId)
                .eq(SysMenuEntity::getResourceKey, GENERATED_MENU_PARENT_KEY)
                .eq(SysMenuEntity::getDeleted, 0)
                .last("limit 1"));
        if (parent == null) {
            throw new BusinessException("PARENT_MENU_MISSING", "已生成模块父菜单未就绪");
        }
        SysMenuEntity entity = new SysMenuEntity();
        entity.setTenantId(tenantId);
        entity.setParentId(parent.getId());
        entity.setAncestors(parent.getAncestors() + "," + parent.getId());
        entity.setMenuType("MENU");
        entity.setResourceKey(menuKey);
        entity.setMenuName((StringUtils.hasText(title) ? title : moduleName) + "（生成产物）");
        entity.setRouteKey(menuKey);
        entity.setGrantKey(moduleName + ":read");
        entity.setPath("/platform/generated/" + moduleName);
        entity.setComponent("CodegenView");
        entity.setIcon("Files");
        entity.setOrderNo(100);
        entity.setVisible(1);
        entity.setEnabled(1);
        entity.setIsSystem(0);
        sysMenuMapper.insert(entity);
        return entity.getId();
    }

    private Long ensureApi(String tenantId, String resourceKey, String resourceName, String grantKey, int orderNo) {
        SysMenuEntity existing = sysMenuMapper.selectOne(new LambdaQueryWrapper<SysMenuEntity>()
                .eq(SysMenuEntity::getTenantId, tenantId)
                .eq(SysMenuEntity::getResourceKey, resourceKey)
                .eq(SysMenuEntity::getDeleted, 0)
                .last("limit 1"));
        if (existing != null) {
            return existing.getId();
        }
        SysMenuEntity parent = sysMenuMapper.selectOne(new LambdaQueryWrapper<SysMenuEntity>()
                .eq(SysMenuEntity::getTenantId, tenantId)
                .eq(SysMenuEntity::getResourceKey, "api.generated")
                .eq(SysMenuEntity::getDeleted, 0)
                .last("limit 1"));
        SysMenuEntity entity = new SysMenuEntity();
        entity.setTenantId(tenantId);
        entity.setParentId(parent == null ? null : parent.getId());
        entity.setAncestors(parent == null ? "" : (parent.getAncestors() + "," + parent.getId()));
        entity.setMenuType("API");
        entity.setResourceKey(resourceKey);
        entity.setMenuName(resourceName);
        entity.setGrantKey(grantKey);
        entity.setOrderNo(orderNo);
        entity.setVisible(0);
        entity.setEnabled(1);
        entity.setIsSystem(0);
        sysMenuMapper.insert(entity);
        return entity.getId();
    }

    private void assignToAdminRole(String tenantId, Long menuId) {
        SysRoleEntity adminRole = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRoleEntity>()
                .eq(SysRoleEntity::getTenantId, tenantId)
                .eq(SysRoleEntity::getRoleCode, "ADMIN")
                .eq(SysRoleEntity::getDeleted, 0)
                .last("limit 1"));
        if (adminRole == null) {
            return;
        }
        Set<String> ancestorIds = new LinkedHashSet<>();
        SysMenuEntity menu = runWithTenant(platformTenantId(), () -> sysMenuMapper.selectById(menuId));
        if (menu != null) {
            if (StringUtils.hasText(menu.getAncestors())) {
                for (String ancestor : menu.getAncestors().split(",")) {
                    if (StringUtils.hasText(ancestor)) {
                        ancestorIds.add(ancestor.trim());
                    }
                }
            }
        }
        ancestorIds.add(String.valueOf(menuId));
        for (String ancestorId : ancestorIds) {
            Long aid;
            try {
                aid = Long.parseLong(ancestorId);
            } catch (NumberFormatException ex) {
                continue;
            }
            Long existing = sysRoleMenuMapper.selectCount(new LambdaQueryWrapper<SysRoleMenuEntity>()
                    .eq(SysRoleMenuEntity::getTenantId, tenantId)
                    .eq(SysRoleMenuEntity::getRoleId, adminRole.getId())
                    .eq(SysRoleMenuEntity::getMenuId, aid));
            if (existing == null || existing == 0) {
                SysRoleMenuEntity relation = new SysRoleMenuEntity();
                relation.setTenantId(tenantId);
                relation.setRoleId(adminRole.getId());
                relation.setMenuId(aid);
                sysRoleMenuMapper.insert(relation);
            }
        }
    }
}