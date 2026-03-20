package com.enterprise.auth.platform.dept.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.audit.service.AuditService;
import com.enterprise.auth.platform.catalog.CatalogService;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.config.PersistenceProperties;
import com.enterprise.auth.platform.dept.dto.DeptCrudRequest;
import com.enterprise.auth.platform.persistence.entity.SysDeptEntity;
import com.enterprise.auth.platform.persistence.entity.SysUserEntity;
import com.enterprise.auth.platform.persistence.mapper.SysDeptMapper;
import com.enterprise.auth.platform.persistence.mapper.SysUserMapper;
import com.enterprise.auth.platform.security.DataScopeService;
import com.enterprise.auth.platform.security.SecuritySupport;
import com.enterprise.auth.platform.tenant.TenantContext;
import java.util.Map;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DeptManagementService {

    private final PersistenceProperties persistenceProperties;
    private final SysDeptMapper sysDeptMapper;
    private final SysUserMapper sysUserMapper;
    private final AuditService auditService;
    private final DataScopeService dataScopeService;

    public DeptManagementService(
            PersistenceProperties persistenceProperties,
            @Nullable SysDeptMapper sysDeptMapper,
            @Nullable SysUserMapper sysUserMapper,
            CatalogService catalogService,
            AuditService auditService,
            DataScopeService dataScopeService
    ) {
        this.persistenceProperties = persistenceProperties;
        this.sysDeptMapper = sysDeptMapper;
        this.sysUserMapper = sysUserMapper;
        this.auditService = auditService;
        this.dataScopeService = dataScopeService;
    }

    @Transactional
    public CatalogService.DepartmentView create(DeptCrudRequest request) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        String operator = SecuritySupport.currentOperator();
        validateParentAccess(tenantId, request.parentId());
        validateLeaderAccess(tenantId, request.leaderUserId());

        SysDeptEntity entity = new SysDeptEntity();
        entity.setTenantId(tenantId);
        entity.setParentId(request.parentId());
        entity.setDeptCode(request.deptCode());
        entity.setDeptName(request.deptName());
        entity.setLeaderUserId(request.leaderUserId());
        sysDeptMapper.insert(entity);
        auditService.record("DEPT_CREATED", operator, tenantId, Map.of("deptId", entity.getId()));
        return new CatalogService.DepartmentView(entity.getId(), entity.getDeptCode(), entity.getDeptName(), entity.getParentId(), entity.getLeaderUserId());
    }

    @Transactional
    public CatalogService.DepartmentView update(Long deptId, DeptCrudRequest request) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        SysDeptEntity entity = getDept(deptId, tenantId);
        validateParentAccess(tenantId, request.parentId());
        validateLeaderAccess(tenantId, request.leaderUserId());

        entity.setParentId(request.parentId());
        entity.setDeptCode(request.deptCode());
        entity.setDeptName(request.deptName());
        entity.setLeaderUserId(request.leaderUserId());
        sysDeptMapper.updateById(entity);
        auditService.record("DEPT_UPDATED", SecuritySupport.currentOperator(), tenantId, Map.of("deptId", entity.getId()));
        return new CatalogService.DepartmentView(entity.getId(), entity.getDeptCode(), entity.getDeptName(), entity.getParentId(), entity.getLeaderUserId());
    }

    @Transactional
    public void delete(Long deptId) {
        requireDatabaseMode();
        String tenantId = currentTenantId();
        String operator = SecuritySupport.currentOperator();
        SysDeptEntity entity = getDept(deptId, tenantId);
        long childCount = sysDeptMapper.selectCount(new LambdaQueryWrapper<SysDeptEntity>()
                .eq(SysDeptEntity::getTenantId, tenantId)
                .eq(SysDeptEntity::getDeleted, 0)
                .eq(SysDeptEntity::getParentId, deptId));
        long userCount = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getTenantId, tenantId)
                .eq(SysUserEntity::getDeleted, 0)
                .eq(SysUserEntity::getDeptId, deptId));
        if (childCount > 0 || userCount > 0) {
            throw new BusinessException("部门下仍存在子部门或用户，暂不允许删除");
        }
        sysDeptMapper.deleteById(entity.getId());
        auditService.record("DEPT_DELETED", operator, tenantId, Map.of("deptId", deptId));
    }

    private SysDeptEntity getDept(Long deptId, String tenantId) {
        SysDeptEntity entity = sysDeptMapper.selectOne(new LambdaQueryWrapper<SysDeptEntity>()
                .eq(SysDeptEntity::getId, deptId)
                .eq(SysDeptEntity::getTenantId, tenantId)
                .eq(SysDeptEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("部门不存在");
        }
        if (!dataScopeService.canAccessDept(tenantId, entity.getId())) {
            throw new BusinessException("无权访问该部门");
        }
        return entity;
    }

    private void validateParentAccess(String tenantId, Long parentId) {
        if (parentId != null && !dataScopeService.canAccessDept(tenantId, parentId)) {
            throw new BusinessException("无权使用该父级部门");
        }
    }

    private void validateLeaderAccess(String tenantId, Long leaderUserId) {
        if (leaderUserId != null && !dataScopeService.canAccessUser(tenantId, leaderUserId)) {
            throw new BusinessException("无权指定该部门负责人");
        }
    }

    private void requireDatabaseMode() {
        if (!persistenceProperties.databaseEnabled() || sysDeptMapper == null || sysUserMapper == null) {
            throw new BusinessException("当前为默认内存模式，暂未启用数据库写入能力");
        }
    }

    private String currentTenantId() {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : "platform";
    }
}
