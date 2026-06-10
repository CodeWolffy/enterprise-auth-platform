package com.enterprise.auth.platform.modules.user.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.role.application.RoleQueryFacade;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserRoleEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserRoleMapper;
import com.enterprise.auth.platform.modules.role.application.RoleGrantQueryFacade;
import com.enterprise.auth.platform.common.authz.DataScopeService;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.user.interfaces.UserSummary;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserDirectoryService {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final RoleQueryFacade roleQueryFacade;
    private final DataScopeService dataScopeService;
    private final RoleGrantQueryFacade roleGrantQueryFacade;

    public UserDirectoryService(
            SysUserMapper sysUserMapper,
            SysUserRoleMapper sysUserRoleMapper,
            RoleQueryFacade roleQueryFacade,
            DataScopeService dataScopeService,
            RoleGrantQueryFacade roleGrantQueryFacade
    ) {
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.roleQueryFacade = roleQueryFacade;
        this.dataScopeService = dataScopeService;
        this.roleGrantQueryFacade = roleGrantQueryFacade;
    }

    public List<UserSummary> listUsers() {
        return listUsers(null, null, null, null, null, 1, 10000).records();
    }

    public PageResult<UserSummary> listUsers(String username, String mobile, String email, Boolean enabled, int page, int size) {
        return listUsers(username, mobile, email, enabled, null, page, size);
    }

    public PageResult<UserSummary> listUsers(String username, String mobile, String email, Boolean enabled, Long deptId, int page, int size) {
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
        if (deptId != null) {
            query.eq(SysUserEntity::getDeptId, deptId);
        }
        dataScopeService.visibleUserIds(tenantId).ifPresent(visibleUserIds -> {
            if (visibleUserIds.isEmpty()) {
                query.apply("1 = 0");
            } else {
                query.in(SysUserEntity::getId, visibleUserIds);
            }
        });
        query.orderByAsc(SysUserEntity::getId);

        Page<SysUserEntity> userPage = sysUserMapper.selectPage(Page.of(page, size), query);
        List<SysUserEntity> users = userPage.getRecords();
        if (users.isEmpty()) {
            return PageResult.of(userPage.getTotal(), page, size, List.of());
        }

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

        return PageResult.of(userPage.getTotal(), page, size, records);
    }

    private Map<Long, Set<String>> loadRoleCodes(String tenantId, List<SysUserEntity> users) {
        List<Long> userIds = users.stream().map(SysUserEntity::getId).toList();
        List<SysUserRoleEntity> userRoles = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRoleEntity>()
                .eq(SysUserRoleEntity::getTenantId, tenantId)
                .in(SysUserRoleEntity::getUserId, userIds));
        if (userRoles.isEmpty()) {
            return Map.of();
        }
        List<Long> roleIds = userRoles.stream().map(SysUserRoleEntity::getRoleId).distinct().toList();
        Map<Long, String> roleCodeMap = roleQueryFacade.loadRoleCodeMap(tenantId);

        return userRoles.stream().collect(Collectors.groupingBy(
                SysUserRoleEntity::getUserId,
                Collectors.mapping(link -> roleCodeMap.get(link.getRoleId()), Collectors.toSet())
        ));
    }

    private Map<Long, Set<String>> loadPermissionCodes(String tenantId, Map<Long, Set<String>> roleCodesByUserId) {
        if (roleCodesByUserId.isEmpty()) {
            return Map.of();
        }
        Map<Set<String>, Set<String>> cache = new java.util.HashMap<>();
        return roleCodesByUserId.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> {
                    Set<String> key = entry.getValue() == null ? Set.of() : new java.util.TreeSet<>(entry.getValue());
                    return cache.computeIfAbsent(key, item -> roleGrantQueryFacade.resolveGrantKeys(tenantId, item, false));
                }
        ));
    }

    private String currentTenantId() {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : "platform";
    }
}
