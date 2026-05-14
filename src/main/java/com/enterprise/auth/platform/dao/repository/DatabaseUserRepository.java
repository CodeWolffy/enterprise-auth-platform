package com.enterprise.auth.platform.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.dto.model.DataScopeType;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.dao.entity.SysRoleEntity;
import com.enterprise.auth.platform.dao.entity.SysUserEntity;
import com.enterprise.auth.platform.dao.entity.SysUserRoleEntity;
import com.enterprise.auth.platform.dao.mapper.SysRoleMapper;
import com.enterprise.auth.platform.dao.mapper.SysUserMapper;
import com.enterprise.auth.platform.dao.mapper.SysUserRoleMapper;
import com.enterprise.auth.platform.service.RolePayloadCodec;
import com.enterprise.auth.platform.service.ResourceService;
import com.enterprise.auth.platform.security.AuthPrincipalCacheService;
import com.enterprise.auth.platform.dto.model.UserAccount;
import java.util.HashSet;
import java.util.List;
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
    private final SysRoleMapper sysRoleMapper;
    private final AuthPrincipalCacheService authPrincipalCacheService;
    private final RolePayloadCodec rolePayloadCodec;
    private final ResourceService resourceService;

    public DatabaseUserRepository(
            SysUserMapper sysUserMapper,
            SysUserRoleMapper sysUserRoleMapper,
            SysRoleMapper sysRoleMapper,
            AuthPrincipalCacheService authPrincipalCacheService,
            RolePayloadCodec rolePayloadCodec,
            ResourceService resourceService
    ) {
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.authPrincipalCacheService = authPrincipalCacheService;
        this.rolePayloadCodec = rolePayloadCodec;
        this.resourceService = resourceService;
    }

    @Override
    @Cacheable(value = "auth:principal", key = "T(com.enterprise.auth.platform.security.AuthPrincipalCacheService).usernameKey(#tenantId, #username)", unless = "#result == null")
    public Optional<UserAccount> findByUsername(String tenantId, String username) {
        SysUserEntity entity = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getTenantId, tenantId)
                .eq(SysUserEntity::getUsername, username)
                .eq(SysUserEntity::getDeleted, 0)
                .last("limit 1"));
        return Optional.ofNullable(entity).map(this::toUserAccount);
    }

    @Override
    @Cacheable(value = "auth:principal", key = "T(com.enterprise.auth.platform.security.AuthPrincipalCacheService).currentTenantIdKey(#id)", unless = "#result == null")
    public Optional<UserAccount> findById(Long id) {
        SysUserEntity entity = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getId, id)
                .eq(SysUserEntity::getDeleted, 0)
                .last("limit 1"));
        return Optional.ofNullable(entity).map(this::toUserAccount);
    }

    @Override
    public List<UserAccount> findAll() {
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUserEntity>()
                        .eq(SysUserEntity::getDeleted, 0)
                        .orderByAsc(SysUserEntity::getId))
                .stream()
                .map(this::toUserAccount)
                .toList();
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
        entity.setPasswordUpdatedAt(TimeSupport.utcNowDateTime());
        sysUserMapper.updateById(entity);
        authPrincipalCacheService.evictByUser(entity.getId(), entity.getTenantId(), entity.getUsername());
    }

    private UserAccount toUserAccount(SysUserEntity user) {
        Set<String> roleCodes = loadRoleCodes(user.getTenantId(), user.getId());
        RoleData roleData = loadRoleData(user.getTenantId(), roleCodes);
        return new UserAccount(
                user.getId(),
                user.getTenantId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getEnabled() != null && user.getEnabled() == 1,
                roleCodes,
                roleData.permissionCodes(),
                roleData.customDeptIds(),
                roleData.dataScopeType(),
                user.getSessionVersion() == null ? 1 : user.getSessionVersion()
        );
    }

    private Set<String> loadRoleCodes(String tenantId, Long userId) {
        List<SysUserRoleEntity> links = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRoleEntity>()
                .eq(SysUserRoleEntity::getTenantId, tenantId)
                .eq(SysUserRoleEntity::getUserId, userId));
        if (links.isEmpty()) {
            return new HashSet<>();
        }
        List<Long> roleIds = links.stream().map(SysUserRoleEntity::getRoleId).distinct().toList();
        return sysRoleMapper.selectList(new LambdaQueryWrapper<SysRoleEntity>()
                        .eq(SysRoleEntity::getTenantId, tenantId)
                        .eq(SysRoleEntity::getDeleted, 0)
                        .in(SysRoleEntity::getId, roleIds))
                .stream()
                .map(SysRoleEntity::getRoleCode)
                .collect(Collectors.toSet());
    }

    private RoleData loadRoleData(String tenantId, Set<String> roleCodes) {
        if (roleCodes.isEmpty()) {
            return new RoleData(new HashSet<>(), DataScopeType.SELF, new HashSet<>());
        }
        List<SysRoleEntity> roles = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRoleEntity>()
                .eq(SysRoleEntity::getTenantId, tenantId)
                .eq(SysRoleEntity::getDeleted, 0)
                .in(SysRoleEntity::getRoleCode, roleCodes));
        DataScopeType dataScopeType = roles.stream()
                .map(SysRoleEntity::getDataScopeType)
                .map(this::parseScope)
                .max(java.util.Comparator.comparingInt(this::scopeWeight))
                .orElse(DataScopeType.SELF);
        Set<Long> customDeptIds = roles.stream()
                .filter(role -> parseScope(role.getDataScopeType()) == DataScopeType.CUSTOM)
                .flatMap(role -> rolePayloadCodec.readDeptIds(role.getDataScopeValueJson()).stream())
                .collect(Collectors.toSet());
        boolean superAdmin = "platform".equals(tenantId) && roleCodes.contains("ADMIN");
        Set<String> permissionCodes = resourceService.resolveGrantKeys(tenantId, roleCodes, superAdmin);
        return new RoleData(permissionCodes, dataScopeType, customDeptIds);
    }

    private DataScopeType parseScope(String value) {
        try {
            return DataScopeType.valueOf(value);
        } catch (Exception ignored) {
            return DataScopeType.SELF;
        }
    }

    private int scopeWeight(DataScopeType scopeType) {
        return switch (scopeType) {
            case SELF -> 1;
            case DEPT -> 2;
            case DEPT_AND_CHILDREN -> 3;
            case CUSTOM -> 4;
            case ALL -> 5;
        };
    }

    private record RoleData(Set<String> permissionCodes, DataScopeType dataScopeType, Set<Long> customDeptIds) {
    }
}
