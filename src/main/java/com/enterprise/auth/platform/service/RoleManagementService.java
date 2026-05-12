package com.enterprise.auth.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.service.AuditService;
import com.enterprise.auth.platform.service.CatalogService;
import com.enterprise.auth.platform.common.convention.exception.BusinessException;
import com.enterprise.auth.platform.dto.model.DataScopeType;
import com.enterprise.auth.platform.dao.entity.SysDeptEntity;
import com.enterprise.auth.platform.dao.entity.SysRoleEntity;
import com.enterprise.auth.platform.dao.entity.SysUserEntity;
import com.enterprise.auth.platform.dao.entity.SysUserRoleEntity;
import com.enterprise.auth.platform.dao.mapper.SysDeptMapper;
import com.enterprise.auth.platform.dao.mapper.SysRoleMapper;
import com.enterprise.auth.platform.dao.mapper.SysUserMapper;
import com.enterprise.auth.platform.dao.mapper.SysUserRoleMapper;
import com.enterprise.auth.platform.dto.req.CreateRoleRequest;
import com.enterprise.auth.platform.dto.req.CreateRoleRequest;
import com.enterprise.auth.platform.service.RolePayloadCodec;
import com.enterprise.auth.platform.service.ResourceService;
import com.enterprise.auth.platform.security.AuthPrincipalCacheService;
import com.enterprise.auth.platform.security.SecuritySupport;
import com.enterprise.auth.platform.common.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RoleManagementService {

    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysDeptMapper sysDeptMapper;
    private final SysUserMapper sysUserMapper;
    private final CatalogService catalogService;
    private final AuditService auditService;
    private final AuthPrincipalCacheService authPrincipalCacheService;
    private final RolePayloadCodec rolePayloadCodec;
    private final ResourceService resourceService;

    public RoleManagementService(
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper,
            SysDeptMapper sysDeptMapper,
            SysUserMapper sysUserMapper,
            CatalogService catalogService,
            AuditService auditService,
            AuthPrincipalCacheService authPrincipalCacheService,
            RolePayloadCodec rolePayloadCodec,
            ResourceService resourceService
    ) {
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysDeptMapper = sysDeptMapper;
        this.sysUserMapper = sysUserMapper;
        this.catalogService = catalogService;
        this.auditService = auditService;
        this.authPrincipalCacheService = authPrincipalCacheService;
        this.rolePayloadCodec = rolePayloadCodec;
        this.resourceService = resourceService;
    }

    @Transactional
    public CatalogService.RoleView create(CreateRoleRequest request) {
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
        applyDataScope(entity, tenantId, request.dataScopeType(), request.customDeptIds());
        sysRoleMapper.insert(entity);

        auditService.record("ROLE_CREATED", operator, tenantId, Map.of("roleId", entity.getId(), "roleCode", entity.getRoleCode()));
        return catalogService.role(entity.getRoleCode());
    }

    @Transactional
    public CatalogService.RoleView update(Long roleId, CreateRoleRequest request) {
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
    public Set<Long> assignResources(Long roleId, Set<Long> resourceIds) {
        String tenantId = currentTenantId();
        String operator = SecuritySupport.currentOperator();
        SysRoleEntity entity = getRole(roleId, tenantId);
        Set<Long> assigned = resourceService.assignRoleResources(tenantId, roleId, resourceIds);
        evictPrincipalsByRole(tenantId, roleId);
        auditService.record("ROLE_RESOURCE_ASSIGNED", operator, tenantId, Map.of("roleId", entity.getId(), "roleCode", entity.getRoleCode(), "resourceIds", assigned));
        return assigned;
    }

    public Set<Long> listAssignedResources(Long roleId) {
        String tenantId = currentTenantId();
        getRole(roleId, tenantId);
        return resourceService.listRoleResourceIds(tenantId, roleId);
    }

    @Transactional
    public void delete(Long roleId) {
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

    private String currentTenantId() {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : "platform";
    }
}
