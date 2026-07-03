package com.enterprise.auth.platform.modules.user.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.cache.CacheNames;
import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserRoleEntity;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMapper;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserRoleMapper;
import com.enterprise.auth.platform.modules.role.application.RolePayloadCodec;
import com.enterprise.auth.platform.modules.role.application.RoleGrantQueryFacade;
import com.enterprise.auth.platform.modules.auth.application.AuthPermissionSnapshotInvalidationService;
import com.enterprise.auth.platform.modules.user.application.AuthenticationUser;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

@Primary
@Repository
public class DatabaseUserRepository implements UserRepository {

    private static final Logger log = LoggerFactory.getLogger(DatabaseUserRepository.class);

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final AuthPermissionSnapshotInvalidationService permissionSnapshotInvalidationService;
    private final RolePayloadCodec rolePayloadCodec;
    private final RoleGrantQueryFacade roleGrantQueryFacade;

    public DatabaseUserRepository(
            SysUserMapper sysUserMapper,
            SysUserRoleMapper sysUserRoleMapper,
            SysRoleMapper sysRoleMapper,
            AuthPermissionSnapshotInvalidationService permissionSnapshotInvalidationService,
            RolePayloadCodec rolePayloadCodec,
            RoleGrantQueryFacade roleGrantQueryFacade
    ) {
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.permissionSnapshotInvalidationService = permissionSnapshotInvalidationService;
        this.rolePayloadCodec = rolePayloadCodec;
        this.roleGrantQueryFacade = roleGrantQueryFacade;
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
    public List<AuthenticationUser> findAll() {
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUserEntity>()
                        .eq(SysUserEntity::getDeleted, 0)
                        .orderByAsc(SysUserEntity::getId))
                .stream()
                .map(user -> toAuthenticationUser(user, false))
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
        entity.setPasswordUpdatedAt(TimeSupport.now());
        sysUserMapper.updateById(entity);
        permissionSnapshotInvalidationService.invalidateUser(entity.getId(), entity.getTenantId(), entity.getUsername());
    }

    private AuthenticationUser toAuthenticationUser(SysUserEntity user, boolean includePasswordHash) {
        List<SysRoleEntity> roles = loadRoles(user.getTenantId(), user.getId());
        Set<String> roleCodes = roles.stream()
                .map(SysRoleEntity::getRoleCode)
                .collect(Collectors.toSet());
        RoleData roleData = loadRoleData(user.getTenantId(), roleCodes, roles);
        return new AuthenticationUser(
                user.getId(),
                user.getTenantId(),
                user.getUsername(),
                includePasswordHash ? user.getPasswordHash() : null,
                user.getEnabled() != null && user.getEnabled() == 1,
                roleCodes,
                roleData.permissionCodes(),
                roleData.customDeptIds(),
                roleData.dataScopeType(),
                user.getSessionVersion() == null ? 1 : user.getSessionVersion(),
                user.getAvatarFileKey(),
                user.getMustChangePassword() != null && user.getMustChangePassword() == 1,
                user.getPasswordUpdatedAt()
        );
    }

    private List<SysRoleEntity> loadRoles(String tenantId, Long userId) {
        List<SysUserRoleEntity> links = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRoleEntity>()
                .eq(SysUserRoleEntity::getTenantId, tenantId)
                .eq(SysUserRoleEntity::getUserId, userId));
        if (links.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = links.stream().map(SysUserRoleEntity::getRoleId).distinct().toList();
        return sysRoleMapper.selectList(new LambdaQueryWrapper<SysRoleEntity>()
                .eq(SysRoleEntity::getTenantId, tenantId)
                .eq(SysRoleEntity::getDeleted, 0)
                .in(SysRoleEntity::getId, roleIds));
    }

    private RoleData loadRoleData(String tenantId, Set<String> roleCodes, List<SysRoleEntity> roles) {
        if (roleCodes.isEmpty()) {
            return new RoleData(new HashSet<>(), DataScopeType.SELF, new HashSet<>());
        }
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
        Set<String> permissionCodes = roleGrantQueryFacade.resolveGrantKeys(tenantId, roleCodes, superAdmin);
        return new RoleData(permissionCodes, dataScopeType, customDeptIds);
    }

    private DataScopeType parseScope(String value) {
        if (!org.springframework.util.StringUtils.hasText(value)) {
            return DataScopeType.SELF;
        }
        try {
            return DataScopeType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            log.debug("未知的用户数据范围类型，回退为 SELF。value={}", value);
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
