package com.enterprise.auth.platform.modules.dept.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.modules.auth.application.DataScopeService;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.context.TenantContextSupport;
import com.enterprise.auth.platform.modules.dept.infrastructure.entity.SysDeptEntity;
import com.enterprise.auth.platform.modules.dept.infrastructure.mapper.SysDeptMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DeptCatalogFacade {

    private final SysDeptMapper sysDeptMapper;
    private final DataScopeService dataScopeService;

    public DeptCatalogFacade(SysDeptMapper sysDeptMapper, DataScopeService dataScopeService) {
        this.sysDeptMapper = sysDeptMapper;
        this.dataScopeService = dataScopeService;
    }

    public List<SysDeptEntity> listDepartments(String tenantId) {
        boolean globalScope = com.enterprise.auth.platform.common.context.TenantContext.isGlobalScope();
        return sysDeptMapper.selectList(new LambdaQueryWrapper<SysDeptEntity>()
                .eq(!globalScope, SysDeptEntity::getTenantId, tenantId)
                .eq(SysDeptEntity::getDeleted, 0)
                .orderByAsc(SysDeptEntity::getTenantId)
                .orderByAsc(SysDeptEntity::getOrderNo)
                .orderByAsc(SysDeptEntity::getId));
    }

    public List<DeptItem> listDeptItems(String tenantId) {
        return listDepartments(tenantId).stream()
                .map(d -> new DeptItem(
                        d.getId(),
                        d.getTenantId(),
                        d.getDeptCode(),
                        d.getDeptName(),
                        d.getParentId(),
                        d.getLeaderUserId(),
                        d.getLeaderName(),
                        d.getLeaderPhone(),
                        d.getOrderNo(),
                        d.getEnabled()
                ))
                .toList();
    }

    public record DeptItem(
            Long id,
            String tenantId,
            String deptCode,
            String deptName,
            Long parentId,
            Long leaderUserId,
            String leaderName,
            String leaderPhone,
            Integer orderNo,
            Integer enabled
    ) {}

    public List<DepartmentView> departments() {
        String tenantId = TenantContextSupport.currentTenantIdOrPlatform();
        boolean globalScope = TenantContext.isGlobalScope() || dataScopeService.isPlatformSuperAdmin();
        List<DeptItem> items = globalScope && !TenantContext.isGlobalScope()
                ? TenantContext.runWithGlobalScope(tenantId, () -> listDeptItems(tenantId))
                : listDeptItems(tenantId);
        if (!globalScope) {
            java.util.Set<Long> visibleDeptIds = dataScopeService.visibleDeptIds(tenantId).orElse(null);
            if (visibleDeptIds != null) {
                items = items.stream()
                        .filter(item -> item.id() != null && visibleDeptIds.contains(item.id()))
                        .toList();
            }
        }
        return items.stream().map(this::toView).toList();
    }

    DepartmentView toView(SysDeptEntity entity) {
        return new DepartmentView(
                entity.getId(), entity.getTenantId(), entity.getDeptCode(), entity.getDeptName(),
                entity.getParentId(), entity.getLeaderUserId(), entity.getLeaderName(), entity.getLeaderPhone(),
                entity.getOrderNo(), entity.getEnabled());
    }

    private DepartmentView toView(DeptItem item) {
        return new DepartmentView(
                item.id(), item.tenantId(), item.deptCode(), item.deptName(), item.parentId(),
                item.leaderUserId(), item.leaderName(), item.leaderPhone(), item.orderNo(), item.enabled());
    }
}
