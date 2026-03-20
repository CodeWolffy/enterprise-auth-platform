package com.enterprise.auth.platform.permission.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.audit.service.AuditService;
import com.enterprise.auth.platform.catalog.CatalogService;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.config.PersistenceProperties;
import com.enterprise.auth.platform.permission.dto.CreatePermissionRequest;
import com.enterprise.auth.platform.permission.dto.UpdatePermissionRequest;
import com.enterprise.auth.platform.persistence.entity.SysPermissionEntity;
import com.enterprise.auth.platform.persistence.entity.SysRolePermissionEntity;
import com.enterprise.auth.platform.persistence.mapper.SysPermissionMapper;
import com.enterprise.auth.platform.persistence.mapper.SysRolePermissionMapper;
import com.enterprise.auth.platform.security.SecuritySupport;
import com.enterprise.auth.platform.tenant.TenantContext;
import java.util.Map;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PermissionManagementService {

    private final PersistenceProperties persistenceProperties;
    private final SysPermissionMapper sysPermissionMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final CatalogService catalogService;
    private final AuditService auditService;

    public PermissionManagementService(
            PersistenceProperties persistenceProperties,
            @Nullable SysPermissionMapper sysPermissionMapper,
            @Nullable SysRolePermissionMapper sysRolePermissionMapper,
            CatalogService catalogService,
            AuditService auditService
    ) {
        this.persistenceProperties = persistenceProperties;
        this.sysPermissionMapper = sysPermissionMapper;
        this.sysRolePermissionMapper = sysRolePermissionMapper;
        this.catalogService = catalogService;
        this.auditService = auditService;
    }

    @Transactional
    public CatalogService.PermissionView create(CreatePermissionRequest request) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        String operator = SecuritySupport.currentOperator();
        if (existsPermissionCode(tenantId, request.permissionCode())) {
            throw new BusinessException("权限编码已存在");
        }

        SysPermissionEntity entity = new SysPermissionEntity();
        entity.setTenantId(tenantId);
        entity.setResourceCode(request.resourceCode());
        entity.setActionCode(request.actionCode());
        entity.setScopeCode(request.scopeCode());
        entity.setPermissionName(request.permissionName());
        entity.setPermissionCode(request.permissionCode());
        sysPermissionMapper.insert(entity);

        auditService.record("PERMISSION_CREATED", operator, tenantId, Map.of("permissionId", entity.getId(), "permissionCode", entity.getPermissionCode()));
        return getView(entity.getId(), tenantId);
    }

    @Transactional
    public CatalogService.PermissionView update(Long permissionId, UpdatePermissionRequest request) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        SysPermissionEntity entity = getEntity(permissionId, tenantId);

        if (!entity.getPermissionCode().equals(request.permissionCode())
                && existsPermissionCode(tenantId, request.permissionCode())) {
            throw new BusinessException("权限编码已存在");
        }

        entity.setResourceCode(request.resourceCode());
        entity.setActionCode(request.actionCode());
        entity.setScopeCode(request.scopeCode());
        entity.setPermissionName(request.permissionName());
        entity.setPermissionCode(request.permissionCode());
        sysPermissionMapper.updateById(entity);

        auditService.record("PERMISSION_UPDATED", SecuritySupport.currentOperator(), tenantId, Map.of("permissionId", entity.getId(), "permissionCode", entity.getPermissionCode()));
        return getView(entity.getId(), tenantId);
    }

    @Transactional
    public void delete(Long permissionId) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        String operator = SecuritySupport.currentOperator();
        SysPermissionEntity entity = getEntity(permissionId, tenantId);
        long assignedRoles = sysRolePermissionMapper.selectCount(new LambdaQueryWrapper<SysRolePermissionEntity>()
                .eq(SysRolePermissionEntity::getTenantId, tenantId)
                .eq(SysRolePermissionEntity::getPermissionId, permissionId));
        if (assignedRoles > 0) {
            throw new BusinessException("权限已分配给角色，暂不允许删除");
        }
        sysPermissionMapper.deleteById(entity.getId());
        auditService.record("PERMISSION_DELETED", operator, tenantId, Map.of("permissionId", permissionId, "permissionCode", entity.getPermissionCode()));
    }

    private CatalogService.PermissionView getView(Long permissionId, String tenantId) {
        SysPermissionEntity entity = getEntity(permissionId, tenantId);
        return new CatalogService.PermissionView(
                entity.getId(),
                entity.getResourceCode(),
                entity.getActionCode(),
                entity.getScopeCode(),
                entity.getPermissionName(),
                entity.getPermissionCode()
        );
    }

    private SysPermissionEntity getEntity(Long permissionId, String tenantId) {
        SysPermissionEntity entity = sysPermissionMapper.selectOne(new LambdaQueryWrapper<SysPermissionEntity>()
                .eq(SysPermissionEntity::getId, permissionId)
                .eq(SysPermissionEntity::getTenantId, tenantId)
                .eq(SysPermissionEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("权限不存在");
        }
        return entity;
    }

    private boolean existsPermissionCode(String tenantId, String permissionCode) {
        return sysPermissionMapper.selectCount(new LambdaQueryWrapper<SysPermissionEntity>()
                .eq(SysPermissionEntity::getTenantId, tenantId)
                .eq(SysPermissionEntity::getPermissionCode, permissionCode)
                .eq(SysPermissionEntity::getDeleted, 0)) > 0;
    }

    private void requireDatabaseMode() {
        if (!persistenceProperties.databaseEnabled() || sysPermissionMapper == null || sysRolePermissionMapper == null) {
            throw new BusinessException("当前为默认内存模式，暂未启用数据库写入能力");
        }
    }

    private String currentTenantId() {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : "platform";
    }
}
