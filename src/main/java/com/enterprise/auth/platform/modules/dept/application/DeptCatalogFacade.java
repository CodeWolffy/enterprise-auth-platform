package com.enterprise.auth.platform.modules.dept.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.modules.dept.infrastructure.entity.SysDeptEntity;
import com.enterprise.auth.platform.modules.dept.infrastructure.mapper.SysDeptMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DeptCatalogFacade {

    private final SysDeptMapper sysDeptMapper;

    public DeptCatalogFacade(SysDeptMapper sysDeptMapper) {
        this.sysDeptMapper = sysDeptMapper;
    }

    public List<SysDeptEntity> listDepartments(String tenantId) {
        return sysDeptMapper.selectList(new LambdaQueryWrapper<SysDeptEntity>()
                .eq(SysDeptEntity::getTenantId, tenantId)
                .eq(SysDeptEntity::getDeleted, 0)
                .orderByAsc(SysDeptEntity::getOrderNo)
                .orderByAsc(SysDeptEntity::getId));
    }

    public List<DeptItem> listDeptItems(String tenantId) {
        return listDepartments(tenantId).stream()
                .map(d -> new DeptItem(
                        d.getId(),
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
            String deptCode,
            String deptName,
            Long parentId,
            Long leaderUserId,
            String leaderName,
            String leaderPhone,
            Integer orderNo,
            Integer enabled
    ) {}
}