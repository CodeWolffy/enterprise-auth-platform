package com.enterprise.auth.platform.modules.dept.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.modules.iam.api.IamDeptQueryPort;
import com.enterprise.auth.platform.modules.dept.infrastructure.entity.SysDeptEntity;
import com.enterprise.auth.platform.modules.dept.infrastructure.mapper.SysDeptMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DeptQueryFacade implements IamDeptQueryPort {

    private final SysDeptMapper sysDeptMapper;

    public DeptQueryFacade(SysDeptMapper sysDeptMapper) {
        this.sysDeptMapper = sysDeptMapper;
    }

    @Override
    public long countByIds(String tenantId, List<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) return 0;
        return sysDeptMapper.selectCount(new LambdaQueryWrapper<SysDeptEntity>()
                .eq(SysDeptEntity::getTenantId, tenantId)
                .eq(SysDeptEntity::getDeleted, 0)
                .in(SysDeptEntity::getId, deptIds));
    }
}
