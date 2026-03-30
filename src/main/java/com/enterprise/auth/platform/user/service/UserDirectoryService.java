package com.enterprise.auth.platform.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.model.DataScopeType;
import com.enterprise.auth.platform.common.model.PageResult;
import com.enterprise.auth.platform.config.PersistenceProperties;
import com.enterprise.auth.platform.persistence.entity.SysRoleEntity;
import com.enterprise.auth.platform.persistence.entity.SysUserEntity;
import com.enterprise.auth.platform.persistence.entity.SysUserRoleEntity;
import com.enterprise.auth.platform.persistence.mapper.SysRoleMapper;
import com.enterprise.auth.platform.persistence.mapper.SysUserMapper;
import com.enterprise.auth.platform.persistence.mapper.SysUserRoleMapper;
import com.enterprise.auth.platform.role.support.RolePayloadCodec;
import com.enterprise.auth.platform.security.DataScopeService;
import com.enterprise.auth.platform.tenant.TenantContext;
import com.enterprise.auth.platform.user.model.UserSummary;
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

    private final PersistenceProperties persistenceProperties;
    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final DataScopeService dataScopeService;
    private final RolePayloadCodec rolePayloadCodec;

    public UserDirectoryService(
            PersistenceProperties persistenceProperties,
            @Nullable SysUserMapper sysUserMapper,
            @Nullable SysUserRoleMapper sysUserRoleMapper,
            @Nullable SysRoleMapper sysRoleMapper,
            DataScopeService dataScopeService,
            RolePayloadCodec rolePayloadCodec
    ) {
        this.persistenceProperties = persistenceProperties;
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.dataScopeService = dataScopeService;
        this.rolePayloadCodec = rolePayloadCodec;
    }

    public List<UserSummary> listUsers() {
        return listUsers(null, null, null, null, 1, 10000).records();
    }

    public PageResult<UserSummary> listUsers(String username, String mobile, String email, Boolean enabled, int page, int size) {
        requireDatabaseMode();
        String tenantId = currentTenantId();

        LambdaQueryWrapper<SysUserEntity> query = new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getTenantId, tenantId)
                .eq(SysUserEntity::getDeleted, 0);
        if (StringUtils.hasText(username)) {
            query.like(SysUserEntity::getUsername, username);
        }
        if (StringUtils.hasText(mobile)) {
            query.like(SysUserEntity::getMobile, mobile);
        }
        if (StringUtils.hasText(email)) {
            query.like(SysUserEntity::getEmail, email);
        }
        if (enabled != null) {
            query.eq(SysUserEntity::getEnabled, enabled ? 1 : 0);
        }
        query.orderByAsc(SysUserEntity::getId);

        List<SysUserEntity> users = sysUserMapper.selectList(query);
        if (users.isEmpty()) {
            return PageResult.of(0, page, size, List.of());
        }

        users = dataScopeService.filterUsers(tenantId, users);
        if (users.isEmpty()) {
            return PageResult.of(0, page, size, List.of());
        }

        int total = users.size();
        int fromIndex = (page - 1) * size;
        if (fromIndex >= total) {
            return PageResult.of(total, page, size, List.of());
        }
        users = users.subList(fromIndex, Math.min(fromIndex + size, total));

        Map<Long, Set<String>> roleCodesByUserId = loadRoleCodes(tenantId, users);
        Map<Long, Set<String>> permissionsByUserId = loadPermissionCodes(tenantId, roleCodesByUserId);

        List<UserSummary> records = users.stream()
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

        return PageResult.of(total, page, size, records);
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
        if (sysRoleMapper == null) {
            return Collections.emptyMap();
        }
        Set<String> roleCodes = roleCodesByUserId.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
        if (roleCodes.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Set<String>> permissionCodesByRoleCode = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRoleEntity>()
                        .eq(SysRoleEntity::getTenantId, tenantId)
                        .eq(SysRoleEntity::getDeleted, 0)
                        .in(SysRoleEntity::getRoleCode, roleCodes))
                .stream()
                .collect(Collectors.toMap(
                        SysRoleEntity::getRoleCode,
                        role -> rolePayloadCodec.readPermissionCodes(role.getPermissionsJson()),
                        (left, right) -> right
                ));

        return roleCodesByUserId.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().stream()
                        .map(permissionCodesByRoleCode::get)
                        .filter(java.util.Objects::nonNull)
                        .flatMap(Set::stream)
                        .collect(Collectors.toSet())
        ));
    }

    private String currentTenantId() {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : "platform";
    }

    private void requireDatabaseMode() {
        if (!persistenceProperties.databaseEnabled()
                || sysUserMapper == null
                || sysUserRoleMapper == null
                || sysRoleMapper == null) {
            throw new BusinessException("当前仅支持数据库模式用户目录查询");
        }
    }
}
