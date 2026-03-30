package com.enterprise.auth.platform.role.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.audit.service.AuditService;
import com.enterprise.auth.platform.catalog.CatalogService;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.model.DataScopeType;
import com.enterprise.auth.platform.config.PersistenceProperties;
import com.enterprise.auth.platform.persistence.entity.SysDeptEntity;
import com.enterprise.auth.platform.persistence.entity.SysRoleEntity;
import com.enterprise.auth.platform.persistence.entity.SysUserEntity;
import com.enterprise.auth.platform.persistence.entity.SysUserRoleEntity;
import com.enterprise.auth.platform.persistence.mapper.SysDeptMapper;
import com.enterprise.auth.platform.persistence.mapper.SysRoleMapper;
import com.enterprise.auth.platform.persistence.mapper.SysUserMapper;
import com.enterprise.auth.platform.persistence.mapper.SysUserRoleMapper;
import com.enterprise.auth.platform.role.dto.CreateRoleRequest;
import com.enterprise.auth.platform.role.dto.UpdateRoleRequest;
import com.enterprise.auth.platform.role.support.RolePayloadCodec;
import com.enterprise.auth.platform.security.AuthPrincipalCacheService;
import com.enterprise.auth.platform.security.SecuritySupport;
import com.enterprise.auth.platform.tenant.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RoleManagementService {

    private final PersistenceProperties persistenceProperties;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysDeptMapper sysDeptMapper;
    private final SysUserMapper sysUserMapper;
    private final CatalogService catalogService;
    private final AuditService auditService;
    private final AuthPrincipalCacheService authPrincipalCacheService;
    private final RolePayloadCodec rolePayloadCodec;

    public RoleManagementService(
            PersistenceProperties persistenceProperties,
            @Nullable SysRoleMapper sysRoleMapper,
            @Nullable SysUserRoleMapper sysUserRoleMapper,
            @Nullable SysDeptMapper sysDeptMapper,
            @Nullable SysUserMapper sysUserMapper,
            CatalogService catalogService,
            AuditService auditService,
            AuthPrincipalCacheService authPrincipalCacheService,
            RolePayloadCodec rolePayloadCodec
    ) {
        this.persistenceProperties = persistenceProperties;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysDeptMapper = sysDeptMapper;
        this.sysUserMapper = sysUserMapper;
        this.catalogService = catalogService;
        this.auditService = auditService;
        this.authPrincipalCacheService = authPrincipalCacheService;
        this.rolePayloadCodec = rolePayloadCodec;
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
        entity.setPermissionsJson(rolePayloadCodec.writePermissionCodes(Set.of()));
        applyDataScope(entity, tenantId, request.dataScopeType(), request.customDeptIds());
        sysRoleMapper.insert(entity);

        auditService.record("ROLE_CREATED", operator, tenantId, Map.of("roleId", entity.getId(), "roleCode", entity.getRoleCode()));
        return catalogService.role(entity.getRoleCode());
    }

    @Transactional
    public CatalogService.RoleView update(Long roleId, UpdateRoleRequest request) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        SysRoleEntity entity = getRole(roleId, tenantId);
        entity.setRoleName(request.roleName());
        entity.setRoleDesc(request.roleDesc());
        applyDataScope(entity, tenantId, request.dataScopeType(), request.customDeptIds());
        sysRoleMapper.updateById(entity);
        evictPrincipalsByRole(tenantId, roleId);

        auditService.record("ROLE_UPDATED", SecuritySupport.currentOperator(), tenantId, Map.of("roleId", entity.getId(), "roleCode", entity.getRoleCode()));
        return catalogService.role(entity.getRoleCode());
    }

    @Transactional
    public List<CatalogService.PermissionView> assignPermissions(Long roleId, Set<String> permissionCodes) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        String operator = SecuritySupport.currentOperator();
        SysRoleEntity entity = getRole(roleId, tenantId);
        List<CatalogService.PermissionView> assigned = catalogService.requirePermissionsByCodes(permissionCodes);

        entity.setPermissionsJson(rolePayloadCodec.writePermissionCodes(
                assigned.stream().map(CatalogService.PermissionView::permissionCode).toList()
        ));
        sysRoleMapper.updateById(entity);
        evictPrincipalsByRole(tenantId, roleId);
        auditService.record("ROLE_PERMISSION_ASSIGNED", operator, tenantId, Map.of("roleId", entity.getId(), "roleCode", entity.getRoleCode(), "permissionCodes", permissionCodes));
        return assigned;
    }

    public List<CatalogService.PermissionView> listAssignedPermissions(Long roleId) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        SysRoleEntity entity = getRole(roleId, tenantId);
        return catalogService.permissionsByCodes(rolePayloadCodec.readPermissionCodes(entity.getPermissionsJson()));
    }

    @Transactional
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

        sysRoleMapper.deleteById(entity.getId());
        evictPrincipalsByRole(tenantId, roleId);
        auditService.record("ROLE_DELETED", operator, tenantId, Map.of("roleId", roleId, "roleCode", entity.getRoleCode()));
    }

    private void applyDataScope(SysRoleEntity entity, String tenantId, DataScopeType scopeType, List<Long> customDeptIds) {
        entity.setDataScopeType(scopeType.name());
        if (scopeType != DataScopeType.CUSTOM) {
            entity.setDataScopeValueJson(null);
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
        entity.setDataScopeValueJson(rolePayloadCodec.writeDeptIds(normalizedDeptIds));
    }

    private void evictPrincipalsByRole(String tenantId, Long roleId) {
        List<Long> userIds = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRoleEntity>()
                        .eq(SysUserRoleEntity::getTenantId, tenantId)
                        .eq(SysUserRoleEntity::getRoleId, roleId))
                .stream()
                .map(SysUserRoleEntity::getUserId)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return;
        }
        if (sysUserMapper == null) {
            authPrincipalCacheService.evictAll();
            return;
        }
        List<SysUserEntity> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getTenantId, tenantId)
                .eq(SysUserEntity::getDeleted, 0)
                .in(SysUserEntity::getId, userIds));
        if (users.isEmpty()) {
            return;
        }
        for (SysUserEntity user : users) {
            authPrincipalCacheService.evictByUser(user.getId(), user.getTenantId(), user.getUsername());
        }
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
                || sysDeptMapper == null) {
            throw new BusinessException("当前为默认内存模式，暂未启用数据库写入能力");
        }
    }

    private String currentTenantId() {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : "platform";
    }
}
