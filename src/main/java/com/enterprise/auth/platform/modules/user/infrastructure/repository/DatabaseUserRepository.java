package com.enterprise.auth.platform.modules.user.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.cache.CacheNames;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.modules.iam.api.IamRoleQueryPort;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserRoleEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserRoleMapper;
import com.enterprise.auth.platform.modules.user.api.UserAuthorizationInvalidationPort;
import com.enterprise.auth.platform.modules.user.application.AuthenticationUser;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

@Primary
@Repository
public class DatabaseUserRepository implements UserRepository {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final UserAuthorizationInvalidationPort authorizationInvalidationPort;
    private final IamRoleQueryPort roleQueryPort;

    public DatabaseUserRepository(
            SysUserMapper sysUserMapper,
            SysUserRoleMapper sysUserRoleMapper,
            UserAuthorizationInvalidationPort authorizationInvalidationPort,
            IamRoleQueryPort roleQueryPort
    ) {
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.authorizationInvalidationPort = authorizationInvalidationPort;
        this.roleQueryPort = roleQueryPort;
    }

    @Override
    public Optional<AuthenticationUser> findByUsername(String tenantId, String username) {
        SysUserEntity entity = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getTenantId, tenantId)
                .eq(SysUserEntity::getUsername, username)
                .eq(SysUserEntity::getDeleted, 0)
                .last("limit 1"));
        return Optional.ofNullable(entity).map(user -> toAuthenticationUser(user, true));
    }

    @Override
    @Cacheable(value = CacheNames.AUTH_PRINCIPAL, key = "T(com.enterprise.auth.platform.modules.auth.infrastructure.AuthPrincipalCacheService).currentTenantIdKey(#id)", unless = "#result == null")
    public Optional<AuthenticationUser> findById(Long id) {
        SysUserEntity entity = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getId, id)
                .eq(SysUserEntity::getDeleted, 0)
                .last("limit 1"));
        return Optional.ofNullable(entity).map(user -> toAuthenticationUser(user, false));
    }

    @Override
    public void incrementSessionVersion(Long userId) {
        SysUserEntity entity = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getId, userId)
                .eq(SysUserEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            return;
        }
        entity.setSessionVersion((entity.getSessionVersion() == null ? 1 : entity.getSessionVersion()) + 1);
        entity.setUpdatedBy(entity.getUsername());
        entity.setPasswordUpdatedAt(TimeSupport.now());
        sysUserMapper.updateById(entity);
        authorizationInvalidationPort.invalidateUser(entity.getId(), entity.getTenantId(), entity.getUsername());
    }

    private AuthenticationUser toAuthenticationUser(SysUserEntity user, boolean includePasswordHash) {
        Set<Long> roleIds = loadRoleIds(user.getTenantId(), user.getId());
        IamRoleQueryPort.RoleAuthorization roleData = roleQueryPort.resolveAuthorization(user.getTenantId(), roleIds);
        return new AuthenticationUser(
                user.getId(),
                user.getTenantId(),
                user.getUsername(),
                includePasswordHash ? user.getPasswordHash() : null,
                user.getEnabled() != null && user.getEnabled() == 1,
                roleData.roleCodes(),
                roleData.permissionCodes(),
                roleData.customDeptIds(),
                roleData.dataScopeType(),
                user.getSessionVersion() == null ? 1 : user.getSessionVersion(),
                user.getAvatarFileKey(),
                user.getMustChangePassword() != null && user.getMustChangePassword() == 1,
                user.getPasswordUpdatedAt()
        );
    }

    private Set<Long> loadRoleIds(String tenantId, Long userId) {
        return sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRoleEntity>()
                .eq(SysUserRoleEntity::getTenantId, tenantId)
                .eq(SysUserRoleEntity::getUserId, userId))
                .stream()
                .map(SysUserRoleEntity::getRoleId)
                .collect(Collectors.toSet());
    }
}
