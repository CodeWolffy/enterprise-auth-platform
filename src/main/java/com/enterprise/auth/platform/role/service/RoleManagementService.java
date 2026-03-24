package com.enterprise.auth.platform.role.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.audit.service.AuditService;
import com.enterprise.auth.platform.catalog.CatalogService;
import com.enterprise.auth.platform.common.model.DataScopeType;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.config.PersistenceProperties;
import com.enterprise.auth.platform.persistence.entity.SysDeptEntity;
import com.enterprise.auth.platform.persistence.entity.SysPermissionEntity;
import com.enterprise.auth.platform.persistence.entity.SysRoleDeptScopeEntity;
import com.enterprise.auth.platform.persistence.entity.SysRoleEntity;
import com.enterprise.auth.platform.persistence.entity.SysRolePermissionEntity;
import com.enterprise.auth.platform.persistence.entity.SysUserRoleEntity;
import com.enterprise.auth.platform.persistence.mapper.SysDeptMapper;
import com.enterprise.auth.platform.persistence.mapper.SysPermissionMapper;
import com.enterprise.auth.platform.persistence.mapper.SysRoleDeptScopeMapper;
import com.enterprise.auth.platform.persistence.mapper.SysRoleMapper;
import com.enterprise.auth.platform.persistence.mapper.SysRolePermissionMapper;
import com.enterprise.auth.platform.persistence.mapper.SysUserRoleMapper;
import com.enterprise.auth.platform.role.dto.CreateRoleRequest;
import com.enterprise.auth.platform.role.dto.UpdateRoleRequest;
import com.enterprise.auth.platform.security.SecuritySupport;
import com.enterprise.auth.platform.tenant.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RoleManagementService {

    private final PersistenceProperties persistenceProperties;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SysDeptMapper sysDeptMapper;
    private final SysRoleDeptScopeMapper sysRoleDeptScopeMapper;
    private final CatalogService catalogService;
    private final AuditService auditService;

    public RoleManagementService(
            PersistenceProperties persistenceProperties,
            @Nullable SysRoleMapper sysRoleMapper,
            @Nullable SysUserRoleMapper sysUserRoleMapper,
            @Nullable SysRolePermissionMapper sysRolePermissionMapper,
            @Nullable SysPermissionMapper sysPermissionMapper,
            @Nullable SysDeptMapper sysDeptMapper,
            @Nullable SysRoleDeptScopeMapper sysRoleDeptScopeMapper,
            CatalogService catalogService,
            AuditService auditService
    ) {
        this.persistenceProperties = persistenceProperties;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysRolePermissionMapper = sysRolePermissionMapper;
        this.sysPermissionMapper = sysPermissionMapper;
        this.sysDeptMapper = sysDeptMapper;
        this.sysRoleDeptScopeMapper = sysRoleDeptScopeMapper;
        this.catalogService = catalogService;
        this.auditService = auditService;
    }

    @Transactional
    public CatalogService.RoleView create(CreateRoleRequest request) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        String operator = SecuritySupport.currentOperator();
        if (existsRoleCode(tenantId, request.roleCode())) {
            throw new BusinessException("角色编码已存在");
        }

        SysRoleEntity entity = new SysRoleEntity();
        entity.setTenantId(tenantId);
        entity.setRoleCode(request.roleCode());
        entity.setRoleName(request.roleName());
        entity.setRoleDesc(request.roleDesc());
        entity.setDataScopeType(request.dataScopeType().name());
        sysRoleMapper.insert(entity);
        saveCustomDeptIds(tenantId, entity.getId(), request.dataScopeType(), request.customDeptIds());

        auditService.record("ROLE_CREATED", operator, tenantId, Map.of("roleId", entity.getId(), "roleCode", entity.getRoleCode()));
        return catalogService.role(entity.getRoleCode());
    }

    @Transactional
    @CacheEvict(value = "auth:principal", allEntries = true)
    public CatalogService.RoleView update(Long roleId, UpdateRoleRequest request) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        SysRoleEntity entity = getRole(roleId, tenantId);
        entity.setRoleName(request.roleName());
        entity.setRoleDesc(request.roleDesc());
        entity.setDataScopeType(request.dataScopeType().name());
        sysRoleMapper.updateById(entity);
        saveCustomDeptIds(tenantId, entity.getId(), request.dataScopeType(), request.customDeptIds());

        auditService.record("ROLE_UPDATED", SecuritySupport.currentOperator(), tenantId, Map.of("roleId", entity.getId(), "roleCode", entity.getRoleCode()));
        return catalogService.role(entity.getRoleCode());
    }

    @Transactional
    @CacheEvict(value = "auth:principal", allEntries = true)
    public List<CatalogService.PermissionView> assignPermissions(Long roleId, Set<String> permissionCodes) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        String operator = SecuritySupport.currentOperator();
        SysRoleEntity entity = getRole(roleId, tenantId);

        sysRolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermissionEntity>()
                .eq(SysRolePermissionEntity::getTenantId, tenantId)
                .eq(SysRolePermissionEntity::getRoleId, roleId));

        List<SysPermissionEntity> permissions = loadPermissions(tenantId, permissionCodes);
        for (SysPermissionEntity permission : permissions) {
            SysRolePermissionEntity link = new SysRolePermissionEntity();
            link.setTenantId(tenantId);
            link.setRoleId(roleId);
            link.setPermissionId(permission.getId());
            sysRolePermissionMapper.insert(link);
        }

        sysRoleMapper.updateById(entity);
        auditService.record("ROLE_PERMISSION_ASSIGNED", operator, tenantId, Map.of("roleId", entity.getId(), "roleCode", entity.getRoleCode(), "permissionCodes", permissionCodes));
        return catalogService.permissionsByCodes(permissionCodes);
    }

    public List<CatalogService.PermissionView> listAssignedPermissions(Long roleId) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        getRole(roleId, tenantId);
        List<Long> permissionIds = sysRolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermissionEntity>()
                        .eq(SysRolePermissionEntity::getTenantId, tenantId)
                        .eq(SysRolePermissionEntity::getRoleId, roleId))
                .stream()
                .map(SysRolePermissionEntity::getPermissionId)
                .toList();
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        Set<String> permissionCodes = sysPermissionMapper.selectList(new LambdaQueryWrapper<SysPermissionEntity>()
                        .eq(SysPermissionEntity::getTenantId, tenantId)
                        .eq(SysPermissionEntity::getDeleted, 0)
                        .in(SysPermissionEntity::getId, permissionIds))
                .stream()
                .map(SysPermissionEntity::getPermissionCode)
                .collect(Collectors.toSet());
        return catalogService.permissionsByCodes(permissionCodes);
    }

    @Transactional
    @CacheEvict(value = "auth:principal", allEntries = true)
    public void delete(Long roleId) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        String operator = SecuritySupport.currentOperator();
        SysRoleEntity entity = getRole(roleId, tenantId);
        long assignedUsers = sysUserRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRoleEntity>()
                .eq(SysUserRoleEntity::getTenantId, tenantId)
                .eq(SysUserRoleEntity::getRoleId, roleId));
        if (assignedUsers > 0) {
            throw new BusinessException("角色已分配给用户，暂不允许删除");
        }

        sysRolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermissionEntity>()
                .eq(SysRolePermissionEntity::getTenantId, tenantId)
                .eq(SysRolePermissionEntity::getRoleId, roleId));
        deleteCustomDeptIds(tenantId, entity.getId());
        sysRoleMapper.deleteById(entity.getId());
        auditService.record("ROLE_DELETED", operator, tenantId, Map.of("roleId", roleId, "roleCode", entity.getRoleCode()));
    }

    private List<SysPermissionEntity> loadPermissions(String tenantId, Set<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return List.of();
        }
        List<SysPermissionEntity> permissions = sysPermissionMapper.selectList(new LambdaQueryWrapper<SysPermissionEntity>()
                .eq(SysPermissionEntity::getTenantId, tenantId)
                .eq(SysPermissionEntity::getDeleted, 0)
                .in(SysPermissionEntity::getPermissionCode, permissionCodes));
        if (permissions.size() != permissionCodes.size()) {
            throw new BusinessException("存在无效的权限编码");
        }
        return permissions;
    }

    private boolean existsRoleCode(String tenantId, String roleCode) {
        return sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRoleEntity>()
                .eq(SysRoleEntity::getTenantId, tenantId)
                .eq(SysRoleEntity::getRoleCode, roleCode)
                .eq(SysRoleEntity::getDeleted, 0)) > 0;
    }

    private SysRoleEntity getRole(Long roleId, String tenantId) {
        SysRoleEntity entity = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRoleEntity>()
                .eq(SysRoleEntity::getId, roleId)
                .eq(SysRoleEntity::getTenantId, tenantId)
                .eq(SysRoleEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("角色不存在");
        }
        return entity;
    }

    private void requireDatabaseMode() {
        if (!persistenceProperties.databaseEnabled()
                || sysRoleMapper == null
                || sysUserRoleMapper == null
                || sysRolePermissionMapper == null
                || sysPermissionMapper == null
                || sysDeptMapper == null
                || sysRoleDeptScopeMapper == null) {
            throw new BusinessException("当前为默认内存模式，暂未启用数据库写入能力");
        }
    }

    private String currentTenantId() {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : "platform";
    }

    private void saveCustomDeptIds(String tenantId, Long roleId, DataScopeType scopeType, List<Long> customDeptIds) {
        if (scopeType != DataScopeType.CUSTOM) {
            deleteCustomDeptIds(tenantId, roleId);
            return;
        }
        List<Long> normalizedDeptIds = customDeptIds == null ? List.of() : customDeptIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (normalizedDeptIds.isEmpty()) {
            throw new BusinessException("自定义数据范围至少选择一个部门");
        }
        long validCount = sysDeptMapper.selectCount(new LambdaQueryWrapper<SysDeptEntity>()
                .eq(SysDeptEntity::getTenantId, tenantId)
                .eq(SysDeptEntity::getDeleted, 0)
                .in(SysDeptEntity::getId, normalizedDeptIds));
        if (validCount != normalizedDeptIds.size()) {
            throw new BusinessException("存在无效的自定义部门");
        }
        deleteCustomDeptIds(tenantId, roleId);
        String operator = SecuritySupport.currentOperator();
        for (Long deptId : normalizedDeptIds) {
            SysRoleDeptScopeEntity entity = new SysRoleDeptScopeEntity();
            entity.setTenantId(tenantId);
            entity.setRoleId(roleId);
            entity.setDeptId(deptId);
            entity.setCreatedBy(operator);
            entity.setUpdatedBy(operator);
            sysRoleDeptScopeMapper.insert(entity);
        }
    }

    private void deleteCustomDeptIds(String tenantId, Long roleId) {
        sysRoleDeptScopeMapper.delete(new LambdaQueryWrapper<SysRoleDeptScopeEntity>()
                .eq(SysRoleDeptScopeEntity::getTenantId, tenantId)
                .eq(SysRoleDeptScopeEntity::getRoleId, roleId));
    }
}
