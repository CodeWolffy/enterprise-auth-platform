package com.enterprise.auth.platform.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.audit.service.AuditService;
import com.enterprise.auth.platform.catalog.CatalogService;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.config.PersistenceProperties;
import com.enterprise.auth.platform.persistence.entity.SysDeptEntity;
import com.enterprise.auth.platform.persistence.entity.SysRoleEntity;
import com.enterprise.auth.platform.persistence.entity.SysTenantEntity;
import com.enterprise.auth.platform.persistence.entity.SysUserEntity;
import com.enterprise.auth.platform.persistence.mapper.SysDeptMapper;
import com.enterprise.auth.platform.persistence.mapper.SysRoleMapper;
import com.enterprise.auth.platform.persistence.mapper.SysTenantMapper;
import com.enterprise.auth.platform.persistence.mapper.SysUserMapper;
import com.enterprise.auth.platform.security.SecuritySupport;
import com.enterprise.auth.platform.tenant.dto.CreateTenantRequest;
import com.enterprise.auth.platform.tenant.dto.UpdateTenantRequest;
import java.util.Map;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantManagementService {

    private final PersistenceProperties persistenceProperties;
    private final SysTenantMapper sysTenantMapper;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysDeptMapper sysDeptMapper;
    private final CatalogService catalogService;
    private final AuditService auditService;

    public TenantManagementService(
            PersistenceProperties persistenceProperties,
            @Nullable SysTenantMapper sysTenantMapper,
            @Nullable SysUserMapper sysUserMapper,
            @Nullable SysRoleMapper sysRoleMapper,
            @Nullable SysDeptMapper sysDeptMapper,
            CatalogService catalogService,
            AuditService auditService
    ) {
        this.persistenceProperties = persistenceProperties;
        this.sysTenantMapper = sysTenantMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysDeptMapper = sysDeptMapper;
        this.catalogService = catalogService;
        this.auditService = auditService;
    }

    @Transactional
    public CatalogService.TenantView create(CreateTenantRequest request) {
        requireDatabaseMode();
        String operator = SecuritySupport.currentOperator();
        if (existsTenant(request.tenantId())) {
            throw new BusinessException("租户标识已存在");
        }

        SysTenantEntity entity = new SysTenantEntity();
        entity.setTenantId(request.tenantId());
        entity.setTenantName(request.tenantName());
        entity.setPlatformLevel(request.platformLevel() ? 1 : 0);
        entity.setTenantStatus(request.tenantStatus() == null ? 1 : request.tenantStatus());
        entity.setExpireAt(request.expireAt());
        sysTenantMapper.insert(entity);

        auditService.record("TENANT_CREATED", operator, request.tenantId(), Map.of("tenantId", request.tenantId()));
        return catalogService.tenant(request.tenantId());
    }

    @Transactional
    public CatalogService.TenantView update(String tenantId, UpdateTenantRequest request) {
        requireDatabaseMode();
        SysTenantEntity entity = getTenant(tenantId);
        entity.setTenantName(request.tenantName());
        entity.setPlatformLevel(request.platformLevel() ? 1 : 0);
        if (request.tenantStatus() != null) {
            entity.setTenantStatus(request.tenantStatus());
        }
        entity.setExpireAt(request.expireAt());
        sysTenantMapper.updateById(entity);

        auditService.record("TENANT_UPDATED", SecuritySupport.currentOperator(), tenantId, Map.of("tenantId", tenantId));
        return catalogService.tenant(tenantId);
    }

    @Transactional
    public void delete(String tenantId) {
        requireDatabaseMode();
        String operator = SecuritySupport.currentOperator();
        SysTenantEntity entity = getTenant(tenantId);
        if ((sysUserMapper.selectCount(new LambdaQueryWrapper<SysUserEntity>().eq(SysUserEntity::getTenantId, tenantId).eq(SysUserEntity::getDeleted, 0)) > 0)
                || (sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRoleEntity>().eq(SysRoleEntity::getTenantId, tenantId).eq(SysRoleEntity::getDeleted, 0)) > 0)
                || (sysDeptMapper.selectCount(new LambdaQueryWrapper<SysDeptEntity>().eq(SysDeptEntity::getTenantId, tenantId).eq(SysDeptEntity::getDeleted, 0)) > 0)) {
            throw new BusinessException("租户下仍存在用户、角色或部门数据，暂不允许删除");
        }

        sysTenantMapper.deleteById(entity.getId());
        auditService.record("TENANT_DELETED", operator, tenantId, Map.of("tenantId", entity.getTenantId()));
    }

    private boolean existsTenant(String tenantId) {
        return sysTenantMapper.selectCount(new LambdaQueryWrapper<SysTenantEntity>()
                .eq(SysTenantEntity::getTenantId, tenantId)
                .eq(SysTenantEntity::getDeleted, 0)) > 0;
    }

    private SysTenantEntity getTenant(String tenantId) {
        SysTenantEntity entity = sysTenantMapper.selectOne(new LambdaQueryWrapper<SysTenantEntity>()
                .eq(SysTenantEntity::getTenantId, tenantId)
                .eq(SysTenantEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("租户不存在");
        }
        return entity;
    }

    private void requireDatabaseMode() {
        if (!persistenceProperties.databaseEnabled()
                || sysTenantMapper == null
                || sysUserMapper == null
                || sysRoleMapper == null
                || sysDeptMapper == null) {
            throw new BusinessException("当前为默认内存模式，暂未启用数据库写入能力");
        }
    }
}
