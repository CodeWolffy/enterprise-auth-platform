package com.enterprise.auth.platform.modules.dept.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.enterprise.auth.platform.modules.log.application.LogPublisher;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.dept.interfaces.DeptCrudRequest;
import com.enterprise.auth.platform.modules.dept.infrastructure.entity.SysDeptEntity;
import com.enterprise.auth.platform.modules.dept.infrastructure.mapper.SysDeptMapper;
import com.enterprise.auth.platform.modules.tenant.application.TenantProfileFacade;
import com.enterprise.auth.platform.modules.user.application.UserQueryFacade;
import com.enterprise.auth.platform.common.authz.DataScopeService;
import com.enterprise.auth.platform.common.authz.SecuritySupport;
import com.enterprise.auth.platform.common.context.TenantContextSupport;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DeptManagementService {

    private final SysDeptMapper sysDeptMapper;
    private final UserQueryFacade userQueryFacade;
    private final LogPublisher logPublisher;
    private final DataScopeService dataScopeService;
    private final TenantProfileFacade tenantProfileFacade;
    private final DeptCatalogFacade deptCatalogFacade;

    public DeptManagementService(
            SysDeptMapper sysDeptMapper,
            UserQueryFacade userQueryFacade,
            DeptCatalogFacade deptCatalogFacade,
            LogPublisher logPublisher,
            DataScopeService dataScopeService,
            TenantProfileFacade tenantProfileFacade
    ) {
        this.sysDeptMapper = sysDeptMapper;
        this.userQueryFacade = userQueryFacade;
        this.logPublisher = logPublisher;
        this.dataScopeService = dataScopeService;
        this.tenantProfileFacade = tenantProfileFacade;
        this.deptCatalogFacade = deptCatalogFacade;
    }

    @Transactional
    public DepartmentView create(DeptCrudRequest request) {
        String tenantId = resolveTargetTenantId(request.tenantId());
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
        return toDepartmentView(entity);
    }

    @Transactional
    public DepartmentView update(Long deptId, DeptCrudRequest request) {
        SysDeptEntity entity = getDept(deptId);
        String tenantId = entity.getTenantId();
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
        return toDepartmentView(entity);
    }

    @Transactional
    public void delete(Long deptId) {
        String operator = SecuritySupport.currentOperator();
        SysDeptEntity entity = getDept(deptId);
        String tenantId = entity.getTenantId();
        long childCount = sysDeptMapper.selectCount(new LambdaQueryWrapper<SysDeptEntity>()
                .eq(SysDeptEntity::getTenantId, tenantId)
                .eq(SysDeptEntity::getDeleted, 0)
                .eq(SysDeptEntity::getParentId, deptId));
        long userCount = userQueryFacade.countByDept(tenantId, deptId);
        if (childCount > 0 || userCount > 0) {
            throw new BusinessException("部门下仍存在子部门或用户，暂不允许删除");
        }
        sysDeptMapper.deleteById(entity.getId());
    }

    private SysDeptEntity getDept(Long deptId) {
        SysDeptEntity entity;
        if (dataScopeService.isPlatformSuperAdmin()) {
            entity = InterceptorIgnoreHelper.execute(
                    IgnoreStrategy.builder().tenantLine(true).build(),
                    () -> sysDeptMapper.selectOne(new LambdaQueryWrapper<SysDeptEntity>()
                            .eq(SysDeptEntity::getId, deptId)
                            .eq(SysDeptEntity::getDeleted, 0)
                            .last("limit 1"))
            );
        } else {
            entity = sysDeptMapper.selectOne(new LambdaQueryWrapper<SysDeptEntity>()
                    .eq(SysDeptEntity::getId, deptId)
                .eq(SysDeptEntity::getTenantId, TenantContextSupport.currentTenantIdOrPlatform())
                    .eq(SysDeptEntity::getDeleted, 0)
                    .last("limit 1"));
        }
        if (entity == null) {
            throw new BusinessException("部门不存在");
        }
        if (!dataScopeService.canAccessDept(entity.getTenantId(), entity.getId())) {
            throw new BusinessException("无权访问该部门");
        }
        return entity;
    }

    private void validateParentAccess(String tenantId, Long parentId) {
        if (parentId == null) {
            return;
        }
        if (!dataScopeService.canAccessDept(tenantId, parentId) || !deptExists(tenantId, parentId)) {
            throw new BusinessException("无权使用该父级部门");
        }
    }

    private void validateLeaderAccess(String tenantId, Long leaderUserId) {
        if (leaderUserId == null) {
            return;
        }
        if (!dataScopeService.canAccessUser(tenantId, leaderUserId)
                || userQueryFacade.countExistingByIds(tenantId, java.util.Set.of(leaderUserId)) != 1) {
            throw new BusinessException("无权指定该部门负责人");
        }
    }

    private boolean deptExists(String tenantId, Long deptId) {
        return sysDeptMapper.selectCount(new LambdaQueryWrapper<SysDeptEntity>()
                .eq(SysDeptEntity::getTenantId, tenantId)
                .eq(SysDeptEntity::getId, deptId)
                .eq(SysDeptEntity::getDeleted, 0)) == 1;
    }

    private String resolveTargetTenantId(String requestedTenantId) {
        String currentTenantId = TenantContextSupport.currentTenantIdOrPlatform();
        if (!dataScopeService.isPlatformSuperAdmin()) {
            return currentTenantId;
        }
        String targetTenantId = StringUtils.hasText(requestedTenantId) ? requestedTenantId.trim() : currentTenantId;
        tenantProfileFacade.findByTenantId(targetTenantId)
                .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "租户不存在"));
        return targetTenantId;
    }

    private DepartmentView toDepartmentView(SysDeptEntity entity) {
        return deptCatalogFacade.toView(entity);
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
