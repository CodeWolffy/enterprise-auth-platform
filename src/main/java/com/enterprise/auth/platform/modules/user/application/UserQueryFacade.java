package com.enterprise.auth.platform.modules.user.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserRoleEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserRoleMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UserQueryFacade {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    public UserQueryFacade(SysUserMapper sysUserMapper, SysUserRoleMapper sysUserRoleMapper) {
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
    }

    public long countByDept(Long deptId) {
        return sysUserMapper.selectCount(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getDeptId, deptId)
                .eq(SysUserEntity::getDeleted, 0));
    }

    public List<SysUserEntity> findByIds(List<Long> userIds) {
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUserEntity>()
                .in(SysUserEntity::getId, userIds)
                .eq(SysUserEntity::getDeleted, 0));
    }

    public long countUsersByRole(Long roleId) {
        return sysUserRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRoleEntity>()
                .eq(SysUserRoleEntity::getRoleId, roleId));
    }

    public List<Long> listUserIdsByRole(Long roleId) {
        return sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRoleEntity>()
                .eq(SysUserRoleEntity::getRoleId, roleId)
                .select(SysUserRoleEntity::getUserId))
                .stream()
                .map(SysUserRoleEntity::getUserId)
                .toList();
    }
}