package com.enterprise.auth.platform.modules.dept.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.modules.auth.application.DataScopeDeptQuery;
import com.enterprise.auth.platform.modules.dept.infrastructure.entity.SysDeptEntity;
import com.enterprise.auth.platform.modules.dept.infrastructure.mapper.SysDeptMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DataScopeDeptQueryAdapter implements DataScopeDeptQuery {

    private final SysDeptMapper sysDeptMapper;

    public DataScopeDeptQueryAdapter(SysDeptMapper sysDeptMapper) {
        this.sysDeptMapper = sysDeptMapper;
    }

    @Override
    public List<ScopedDept> listActive(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return List.of();
        }
        return sysDeptMapper.selectList(new LambdaQueryWrapper<SysDeptEntity>()
                        .eq(SysDeptEntity::getTenantId, tenantId)
                        .eq(SysDeptEntity::getDeleted, 0))
                .stream()
                .map(dept -> new ScopedDept(dept.getId(), dept.getParentId()))
                .toList();
    }
}