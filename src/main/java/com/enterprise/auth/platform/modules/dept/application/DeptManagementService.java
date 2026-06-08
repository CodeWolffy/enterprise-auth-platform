package com.enterprise.auth.platform.modules.dept.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.modules.audit.application.AuditService;
import com.enterprise.auth.platform.modules.resource.application.CatalogService;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.dept.interfaces.DeptCrudRequest;
import com.enterprise.auth.platform.modules.dept.infrastructure.entity.SysDeptEntity;
import com.enterprise.auth.platform.modules.dept.infrastructure.mapper.SysDeptMapper;
import com.enterprise.auth.platform.modules.user.application.UserQueryFacade;
import com.enterprise.auth.platform.common.authz.DataScopeService;
import com.enterprise.auth.platform.common.authz.SecuritySupport;
import com.enterprise.auth.platform.common.context.TenantContext;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DeptManagementService {

    private final SysDeptMapper sysDeptMapper;
    private final UserQueryFacade userQueryFacade;
    private final AuditService auditService;
    private final DataScopeService dataScopeService;

    public DeptManagementService(
            SysDeptMapper sysDeptMapper,
            UserQueryFacade userQueryFacade,
            CatalogService catalogService,
            AuditService auditService,
            DataScopeService dataScopeService
    ) {
        this.sysDeptMapper = sysDeptMapper;
        this.userQueryFacade = userQueryFacade;
        this.auditService = auditService;
        this.dataScopeService = dataScopeService;
    }

    @Transactional
    public CatalogService.DepartmentView create(DeptCrudRequest request) {
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
        entity.setLeaderName(normalizeText(request.leaderName()));
        entity.setLeaderPhone(normalizeText(request.leaderPhone()));
        entity.setOrderNo(request.orderNo() == null ? 0 : request.orderNo());
        entity.setEnabled(request.enabled() == null ? 1 : request.enabled());
        sysDeptMapper.insert(entity);
        auditService.record("DEPT_CREATED", operator, tenantId, Map.of("deptId", entity.getId()));
        return toDepartmentView(entity);
    }

    @Transactional
    public CatalogService.DepartmentView update(Long deptId, DeptCrudRequest request) {
        String tenantId = currentTenantId();
        SysDeptEntity entity = getDept(deptId, tenantId);
        validateParentAccess(tenantId, request.parentId());
        validateLeaderAccess(tenantId, request.leaderUserId());

        entity.setParentId(request.parentId());
        entity.setDeptCode(request.deptCode());
        entity.setDeptName(request.deptName());
        entity.setLeaderUserId(request.leaderUserId());
        entity.setLeaderName(normalizeText(request.leaderName()));
        entity.setLeaderPhone(normalizeText(request.leaderPhone()));
        entity.setOrderNo(request.orderNo() == null ? 0 : request.orderNo());
        entity.setEnabled(request.enabled() == null ? 1 : request.enabled());
        sysDeptMapper.updateById(entity);
        auditService.record("DEPT_UPDATED", SecuritySupport.currentOperator(), tenantId, Map.of("deptId", entity.getId()));
        return toDepartmentView(entity);
    }

    @Transactional
    public void delete(Long deptId) {
        String tenantId = currentTenantId();
        String operator = SecuritySupport.currentOperator();
        SysDeptEntity entity = getDept(deptId, tenantId);
        long childCount = sysDeptMapper.selectCount(new LambdaQueryWrapper<SysDeptEntity>()
                .eq(SysDeptEntity::getTenantId, tenantId)
                .eq(SysDeptEntity::getDeleted, 0)
                .eq(SysDeptEntity::getParentId, deptId));
        long userCount = userQueryFacade.countByDept(deptId);
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

    private String currentTenantId() {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : "platform";
    }

    private CatalogService.DepartmentView toDepartmentView(SysDeptEntity entity) {
        return new CatalogService.DepartmentView(
                entity.getId(),
                entity.getDeptCode(),
                entity.getDeptName(),
                entity.getParentId(),
                entity.getLeaderUserId(),
                entity.getLeaderName(),
                entity.getLeaderPhone(),
                entity.getOrderNo(),
                entity.getEnabled()
        );
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
