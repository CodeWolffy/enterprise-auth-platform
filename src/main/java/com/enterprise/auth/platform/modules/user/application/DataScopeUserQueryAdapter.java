package com.enterprise.auth.platform.modules.user.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.modules.iam.api.DataScopeUserQuery;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DataScopeUserQueryAdapter implements DataScopeUserQuery {

    private final SysUserMapper sysUserMapper;

    public DataScopeUserQueryAdapter(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public Optional<ScopedUser> findActive(Long userId, String tenantId) {
        if (userId == null || !StringUtils.hasText(tenantId)) {
            return Optional.empty();
        }
        SysUserEntity entity = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getId, userId)
                .eq(SysUserEntity::getTenantId, tenantId)
                .eq(SysUserEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(new ScopedUser(entity.getId(), entity.getUsername(), entity.getDeptId()));
    }

    @Override
    public List<ScopedUser> listByDeptIds(String tenantId, Set<Long> deptIds) {
        if (!StringUtils.hasText(tenantId) || deptIds == null || deptIds.isEmpty()) {
            return List.of();
        }
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUserEntity>()
                        .eq(SysUserEntity::getTenantId, tenantId)
                        .eq(SysUserEntity::getDeleted, 0)
                        .in(SysUserEntity::getDeptId, deptIds))
                .stream()
                .map(user -> new ScopedUser(user.getId(), user.getUsername(), user.getDeptId()))
                .toList();
    }
}
