package com.enterprise.auth.platform.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.model.DataScopeType;
import com.enterprise.auth.platform.config.PersistenceProperties;
import com.enterprise.auth.platform.persistence.entity.SysPermissionEntity;
import com.enterprise.auth.platform.persistence.entity.SysRoleEntity;
import com.enterprise.auth.platform.persistence.entity.SysRolePermissionEntity;
import com.enterprise.auth.platform.persistence.entity.SysUserEntity;
import com.enterprise.auth.platform.persistence.entity.SysUserRoleEntity;
import com.enterprise.auth.platform.persistence.mapper.SysPermissionMapper;
import com.enterprise.auth.platform.persistence.mapper.SysRoleMapper;
import com.enterprise.auth.platform.persistence.mapper.SysRolePermissionMapper;
import com.enterprise.auth.platform.persistence.mapper.SysUserMapper;
import com.enterprise.auth.platform.persistence.mapper.SysUserRoleMapper;
import com.enterprise.auth.platform.security.DataScopeService;
import com.enterprise.auth.platform.tenant.TenantContext;
import com.enterprise.auth.platform.user.model.UserSummary;
import com.enterprise.auth.platform.user.repository.UserRepository;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserDirectoryService {

    private final UserRepository userRepository;
    private final PersistenceProperties persistenceProperties;
    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final DataScopeService dataScopeService;

    public UserDirectoryService(
            UserRepository userRepository,
            PersistenceProperties persistenceProperties,
            @Nullable SysUserMapper sysUserMapper,
            @Nullable SysUserRoleMapper sysUserRoleMapper,
            @Nullable SysRoleMapper sysRoleMapper,
            @Nullable SysRolePermissionMapper sysRolePermissionMapper,
            @Nullable SysPermissionMapper sysPermissionMapper,
            DataScopeService dataScopeService
    ) {
        this.userRepository = userRepository;
        this.persistenceProperties = persistenceProperties;
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysRolePermissionMapper = sysRolePermissionMapper;
        this.sysPermissionMapper = sysPermissionMapper;
        this.dataScopeService = dataScopeService;
    }

    public List<UserSummary> listUsers() {
        String tenantId = currentTenantId();
        // 默认模式仍然走内存目录，但同样要按当前租户过滤，避免演示数据串租户。
        if (!persistenceProperties.databaseEnabled() || sysUserMapper == null) {
            return userRepository.findAll().stream()
                    .filter(user -> tenantId.equals(user.tenantId()))
                    .map(user -> new UserSummary(
                            user.id(),
                            user.tenantId(),
                            user.username(),
                            user.username(),
                            null,
                            null,
                            null,
                            user.enabled(),
                            user.roles(),
                            user.permissions(),
                            user.dataScopeType()
                    ))
                    .toList();
        }

        List<SysUserEntity> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getTenantId, tenantId)
                .eq(SysUserEntity::getDeleted, 0)
                .orderByAsc(SysUserEntity::getId));
        if (users.isEmpty()) {
            return List.of();
        }

        users = dataScopeService.filterUsers(tenantId, users);
        if (users.isEmpty()) {
            return List.of();
        }

        Map<Long, Set<String>> roleCodesByUserId = loadRoleCodes(tenantId, users);
        Map<Long, Set<String>> permissionsByUserId = loadPermissionCodes(tenantId, roleCodesByUserId);

        return users.stream()
                .map(user -> new UserSummary(
                        user.getId(),
                        user.getTenantId(),
                        user.getUsername(),
                        user.getDisplayName(),
                        user.getMobile(),
                        user.getEmail(),
                        user.getDeptId(),
                        user.getEnabled() != null && user.getEnabled() == 1,
                        roleCodesByUserId.getOrDefault(user.getId(), Set.of()),
                        permissionsByUserId.getOrDefault(user.getId(), Set.of()),
                        DataScopeType.SELF
                ))
                .toList();
    }

    private Map<Long, Set<String>> loadRoleCodes(String tenantId, List<SysUserEntity> users) {
        if (sysUserRoleMapper == null || sysRoleMapper == null) {
            return Collections.emptyMap();
        }
        List<Long> userIds = users.stream().map(SysUserEntity::getId).toList();
        List<SysUserRoleEntity> userRoles = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRoleEntity>()
                .eq(SysUserRoleEntity::getTenantId, tenantId)
                .in(SysUserRoleEntity::getUserId, userIds));
        if (userRoles.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> roleIds = userRoles.stream().map(SysUserRoleEntity::getRoleId).distinct().toList();
        Map<Long, String> roleCodeMap = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRoleEntity>()
                        .eq(SysRoleEntity::getTenantId, tenantId)
                        .eq(SysRoleEntity::getDeleted, 0)
                        .in(SysRoleEntity::getId, roleIds))
                .stream()
                .collect(Collectors.toMap(SysRoleEntity::getId, SysRoleEntity::getRoleCode));

        return userRoles.stream().collect(Collectors.groupingBy(
                SysUserRoleEntity::getUserId,
                Collectors.mapping(link -> roleCodeMap.get(link.getRoleId()), Collectors.toSet())
        ));
    }

    private Map<Long, Set<String>> loadPermissionCodes(String tenantId, Map<Long, Set<String>> roleCodesByUserId) {
        if (sysRoleMapper == null || sysRolePermissionMapper == null || sysPermissionMapper == null) {
            return Collections.emptyMap();
        }
        Set<String> roleCodes = roleCodesByUserId.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
        if (roleCodes.isEmpty()) {
            return Collections.emptyMap();
        }

        List<SysRoleEntity> roles = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRoleEntity>()
                .eq(SysRoleEntity::getTenantId, tenantId)
                .eq(SysRoleEntity::getDeleted, 0)
                .in(SysRoleEntity::getRoleCode, roleCodes));
        Map<String, Long> roleIdByCode = roles.stream().collect(Collectors.toMap(SysRoleEntity::getRoleCode, SysRoleEntity::getId));
        List<Long> roleIds = roles.stream().map(SysRoleEntity::getId).toList();
        List<SysRolePermissionEntity> rolePermissions = sysRolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermissionEntity>()
                .eq(SysRolePermissionEntity::getTenantId, tenantId)
                .in(SysRolePermissionEntity::getRoleId, roleIds));
        if (rolePermissions.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> permissionIds = rolePermissions.stream().map(SysRolePermissionEntity::getPermissionId).distinct().toList();
        Map<Long, String> permissionCodeMap = sysPermissionMapper.selectList(new LambdaQueryWrapper<SysPermissionEntity>()
                        .eq(SysPermissionEntity::getTenantId, tenantId)
                        .eq(SysPermissionEntity::getDeleted, 0)
                        .in(SysPermissionEntity::getId, permissionIds))
                .stream()
                .collect(Collectors.toMap(SysPermissionEntity::getId, SysPermissionEntity::getPermissionCode));
        Map<Long, Set<String>> permissionByRoleId = rolePermissions.stream().collect(Collectors.groupingBy(
                SysRolePermissionEntity::getRoleId,
                Collectors.mapping(item -> permissionCodeMap.get(item.getPermissionId()), Collectors.toSet())
        ));

        return roleCodesByUserId.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().stream()
                        .map(roleIdByCode::get)
                        .filter(java.util.Objects::nonNull)
                        .map(permissionByRoleId::get)
                        .filter(java.util.Objects::nonNull)
                        .flatMap(Set::stream)
                        .collect(Collectors.toSet())
        ));
    }

    private String currentTenantId() {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : "platform";
    }
}
