package com.enterprise.auth.platform.modules.user.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class UserStatsFacade {

    private final SysUserMapper sysUserMapper;

    public UserStatsFacade(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    public long countUsers(String tenantId, boolean platformScope, Optional<Set<Long>> visibleUserIds) {
        LambdaQueryWrapper<SysUserEntity> wrapper = new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getDeleted, 0);
        if (!platformScope) {
            wrapper.eq(SysUserEntity::getTenantId, tenantId);
            applyVisibleIds(wrapper, visibleUserIds, SysUserEntity::getId);
        }
        return sysUserMapper.selectCount(wrapper);
    }

    private <T> void applyVisibleIds(
            LambdaQueryWrapper<T> wrapper,
            Optional<Set<Long>> visibleIds,
            SFunction<T, ?> column
    ) {
        visibleIds.ifPresent(ids -> {
            if (ids.isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in(column, ids);
            }
        });
    }
}