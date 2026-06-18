package com.enterprise.auth.platform.modules.user.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserRoleEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserRoleMapper;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class UserBootstrapFacade {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    public UserBootstrapFacade(SysUserMapper sysUserMapper, SysUserRoleMapper sysUserRoleMapper) {
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
    }

    public AdminUserResult ensureAdminUser(EnsureAdminUserRequest request) {
        SysUserEntity existing = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getTenantId, request.tenantId())
                .eq(SysUserEntity::getUsername, request.username())
                .eq(SysUserEntity::getDeleted, 0)
                .last("limit 1"));
        if (existing != null) {
            return new AdminUserResult(existing.getId(), existing.getUsername());
        }

        String username = nextAvailableUsername(request.username());
        SysUserEntity entity = new SysUserEntity();
        entity.setTenantId(request.tenantId());
        entity.setDeptId(request.deptId());
        entity.setUsername(username);
        entity.setDisplayName(request.displayName());
        entity.setMobile(null);
        entity.setEmail(null);
        entity.setAvatarFileKey(null);
        entity.setPasswordHash(request.passwordHash());
        entity.setEnabled(1);
        entity.setSessionVersion(1);
        entity.setMustChangePassword(request.mustChangePassword());
        entity.setPasswordUpdatedAt(TimeSupport.utcNowDateTime());
        sysUserMapper.insert(entity);
        return new AdminUserResult(entity.getId(), entity.getUsername());
    }

    public void ensureUserRole(String tenantId, Long userId, Long roleId) {
        Long existing = sysUserRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRoleEntity>()
                .eq(SysUserRoleEntity::getTenantId, tenantId)
                .eq(SysUserRoleEntity::getUserId, userId)
                .eq(SysUserRoleEntity::getRoleId, roleId));
        if (Optional.ofNullable(existing).orElse(0L) > 0) {
            return;
        }
        SysUserRoleEntity relation = new SysUserRoleEntity();
        relation.setTenantId(tenantId);
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        sysUserRoleMapper.insert(relation);
    }

    public String nextAvailableUsername(String preferredUsername) {
        if (sysUserMapper.countActiveByUsername(preferredUsername) == 0) {
            return preferredUsername;
        }
        for (int i = 1; i <= 100; i++) {
            String candidate = preferredUsername + "_" + i;
            if (sysUserMapper.countActiveByUsername(candidate) == 0) {
                return candidate;
            }
        }
        return preferredUsername + "_" + System.currentTimeMillis();
    }

    public record EnsureAdminUserRequest(
            String tenantId,
            Long deptId,
            String username,
            String displayName,
            String passwordHash,
            int mustChangePassword
    ) {
    }

    public record AdminUserResult(
            Long userId,
            String username
    ) {
    }
}
