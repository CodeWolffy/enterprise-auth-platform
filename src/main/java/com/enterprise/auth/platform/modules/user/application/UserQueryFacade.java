package com.enterprise.auth.platform.modules.user.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserRoleEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserRoleMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    public List<SysUserEntity> findEnabledByIds(String tenantId, Set<Long> userIds) {
        if (!StringUtils.hasText(tenantId) || userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        Set<Long> normalizedIds = userIds.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getTenantId, tenantId)
                .in(SysUserEntity::getId, normalizedIds)
                .eq(SysUserEntity::getEnabled, 1)
                .eq(SysUserEntity::getDeleted, 0));
    }

    public List<EnabledUser> findEnabledSummariesByIds(String tenantId, Set<Long> userIds) {
        return findEnabledByIds(tenantId, userIds).stream()
                .map(user -> new EnabledUser(user.getId(), user.getUsername()))
                .toList();
    }

    public Set<Long> listEnabledUserIds(String tenantId, Set<Long> userIds) {
        return findEnabledByIds(tenantId, userIds).stream()
                .map(SysUserEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public long countEnabledByIds(String tenantId, Set<Long> userIds) {
        if (!StringUtils.hasText(tenantId) || userIds == null || userIds.isEmpty()) {
            return 0;
        }
        Set<Long> normalizedIds = userIds.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalizedIds.isEmpty()) {
            return 0;
        }
        return sysUserMapper.selectCount(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getTenantId, tenantId)
                .in(SysUserEntity::getId, normalizedIds)
                .eq(SysUserEntity::getEnabled, 1)
                .eq(SysUserEntity::getDeleted, 0));
    }

    public long countExistingByIds(String tenantId, Set<Long> userIds) {
        if (!StringUtils.hasText(tenantId) || userIds == null || userIds.isEmpty()) {
            return 0;
        }
        Set<Long> normalizedIds = userIds.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalizedIds.isEmpty()) {
            return 0;
        }
        return sysUserMapper.selectCount(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getTenantId, tenantId)
                .in(SysUserEntity::getId, normalizedIds)
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

    public Set<Long> listUserIdsByRoles(String tenantId, Set<Long> roleIds) {
        if (!StringUtils.hasText(tenantId) || roleIds == null || roleIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> normalizedRoleIds = roleIds.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalizedRoleIds.isEmpty()) {
            return Set.of();
        }
        return sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRoleEntity>()
                        .eq(SysUserRoleEntity::getTenantId, tenantId)
                        .in(SysUserRoleEntity::getRoleId, normalizedRoleIds)
                        .select(SysUserRoleEntity::getUserId))
                .stream()
                .map(SysUserRoleEntity::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public record EnabledUser(Long id, String username) {
    }
}