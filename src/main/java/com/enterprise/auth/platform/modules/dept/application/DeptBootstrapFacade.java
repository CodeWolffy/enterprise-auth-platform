package com.enterprise.auth.platform.modules.dept.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.modules.dept.infrastructure.entity.SysDeptEntity;
import com.enterprise.auth.platform.modules.dept.infrastructure.mapper.SysDeptMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DeptBootstrapFacade {

    private static final String ROOT_DEPT_CODE = "ROOT";
    private final SysDeptMapper sysDeptMapper;

    public DeptBootstrapFacade(SysDeptMapper sysDeptMapper) {
        this.sysDeptMapper = sysDeptMapper;
    }

    public Long ensureRootDept(String tenantId, String deptName) {
        SysDeptEntity existing = sysDeptMapper.selectOne(new LambdaQueryWrapper<SysDeptEntity>()
                .eq(SysDeptEntity::getTenantId, tenantId)
                .eq(SysDeptEntity::getDeptCode, ROOT_DEPT_CODE)
                .eq(SysDeptEntity::getDeleted, 0)
                .last("limit 1"));
        if (existing != null) {
            return existing.getId();
        }
        SysDeptEntity entity = new SysDeptEntity();
        entity.setTenantId(tenantId);
        entity.setParentId(null);
        entity.setDeptCode(ROOT_DEPT_CODE);
        entity.setDeptName(StringUtils.hasText(deptName) ? deptName.trim() : "根部门");
        entity.setLeaderUserId(null);
        entity.setLeaderName(null);
        entity.setLeaderPhone(null);
        entity.setOrderNo(0);
        entity.setEnabled(1);
        sysDeptMapper.insert(entity);
        return entity.getId();
    }
}
