package com.enterprise.auth.platform.modules.user.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.common.web.PaginationSupport;
import com.enterprise.auth.platform.modules.iam.api.IamRoleQueryPort;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserRoleEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserRoleMapper;
import com.enterprise.auth.platform.modules.user.api.UserAccessControlPort;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.context.TenantContextSupport;
import com.enterprise.auth.platform.modules.user.interfaces.UserSummary;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserDirectoryService {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final IamRoleQueryPort roleQueryPort;
    private final UserAccessControlPort accessControlPort;

    public UserDirectoryService(
            SysUserMapper sysUserMapper,
            SysUserRoleMapper sysUserRoleMapper,
            IamRoleQueryPort roleQueryPort,
            UserAccessControlPort accessControlPort
    ) {
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.roleQueryPort = roleQueryPort;
        this.accessControlPort = accessControlPort;
    }

    public List<UserSummary> listUsers() {
        // 兼容旧调用：限制为单页上限，禁止 10000 全集路径
        return listUsers(null, null, null, null, null, 1, PaginationSupport.DEFAULT_MAX_SIZE).records();
    }

    /** 按 ID 查询单个用户摘要，避免 loadSummary 拉全集。 */
    public Optional<UserSummary> findUserSummary(Long userId, String tenantId) {
        if (userId == null) {
            return Optional.empty();
        }
        boolean globalScope = isGlobalScope();
        if (globalScope) {
            return InterceptorIgnoreHelper.execute(
                    IgnoreStrategy.builder().tenantLine(true).build(),
                    () -> doFindUserSummary(userId, tenantId, true)
            );
        }
        return doFindUserSummary(userId, tenantId, false);
    }

    private Optional<UserSummary> doFindUserSummary(Long userId, String tenantIdFilter, boolean globalScope) {
        String tenantId = TenantContextSupport.currentTenantIdOrPlatform();
        String effectiveTenant = StringUtils.hasText(tenantIdFilter) ? tenantIdFilter.trim() : tenantId;
        LambdaQueryWrapper<SysUserEntity> query = new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getId, userId)
                .eq(SysUserEntity::getDeleted, 0)
                .eq(!globalScope, SysUserEntity::getTenantId, tenantId)
                .eq(globalScope && StringUtils.hasText(tenantIdFilter), SysUserEntity::getTenantId, effectiveTenant)
                .last("limit 1");
        SysUserEntity user = sysUserMapper.selectOne(query);
        if (user == null) {
            return Optional.empty();
        }
        if (!globalScope) {
            Optional<Set<Long>> visible = accessControlPort.visibleUserIds(tenantId);
            if (visible.isPresent() && !visible.get().contains(user.getId())) {
                return Optional.empty();
            }
        }
        Map<Long, Set<String>> roleCodesByUserId = loadRoleCodes(user.getTenantId(), List.of(user), globalScope);
        Map<Long, Set<String>> permissionsByUserId = loadPermissionCodes(
                user.getTenantId(), List.of(user), roleCodesByUserId, globalScope);
        return Optional.of(new UserSummary(
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
                DataScopeType.SELF,
                user.getCreatedAt(),
                user.getLastLoginAt(),
                user.getLastLoginIp()
        ));
    }

    public PageResult<UserSummary> listUsers(String username, String mobile, String email, Boolean enabled, int page, int size) {
        return listUsers(username, mobile, email, enabled, null, page, size);
    }

    public PageResult<UserSummary> listUsers(String username, String mobile, String email, Boolean enabled, Long deptId, int page, int size) {
        return listUsers(username, mobile, email, enabled, deptId, null, page, size);
    }

    public PageResult<UserSummary> listUsers(
            String username,
            String mobile,
            String email,
            Boolean enabled,
            Long deptId,
            String tenantIdFilter,
            int page,
            int size
    ) {
        boolean globalScope = isGlobalScope();
        if (globalScope) {
            return InterceptorIgnoreHelper.execute(
                    IgnoreStrategy.builder().tenantLine(true).build(),
                    () -> doListUsers(username, mobile, email, enabled, deptId, tenantIdFilter, page, size, true)
            );
        }
        return doListUsers(username, mobile, email, enabled, deptId, null, page, size, false);
    }

    private PageResult<UserSummary> doListUsers(
            String username,
            String mobile,
            String email,
            Boolean enabled,
            Long deptId,
            String tenantIdFilter,
            int page,
            int size,
            boolean globalScope
    ) {
        String tenantId = TenantContextSupport.currentTenantIdOrPlatform();
        String normalizedTenantFilter = StringUtils.hasText(tenantIdFilter) ? tenantIdFilter.trim() : null;

        LambdaQueryWrapper<SysUserEntity> query = new LambdaQueryWrapper<SysUserEntity>()
                .eq(!globalScope, SysUserEntity::getTenantId, tenantId)
                .eq(globalScope && StringUtils.hasText(normalizedTenantFilter), SysUserEntity::getTenantId, normalizedTenantFilter)
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
        if (!globalScope) {
            accessControlPort.visibleUserIds(tenantId).ifPresent(visibleUserIds -> {
                if (visibleUserIds.isEmpty()) {
                    query.apply("1 = 0");
                } else {
                    query.in(SysUserEntity::getId, visibleUserIds);
                }
            });
        }
        query.orderByAsc(SysUserEntity::getTenantId).orderByAsc(SysUserEntity::getId);

        Page<SysUserEntity> userPage = sysUserMapper.selectPage(Page.of(page, size), query);
        List<SysUserEntity> users = userPage.getRecords();
        if (users.isEmpty()) {
            return PageResult.of(userPage.getTotal(), page, size, List.of());
        }

        Map<Long, Set<String>> roleCodesByUserId = loadRoleCodes(tenantId, users, globalScope);
        Map<Long, Set<String>> permissionsByUserId = loadPermissionCodes(tenantId, users, roleCodesByUserId, globalScope);

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
                        DataScopeType.SELF,
                        user.getCreatedAt(),
                        user.getLastLoginAt(),
                        user.getLastLoginIp()
                ))
                .toList();

        return PageResult.of(userPage.getTotal(), page, size, records);
    }

    private Map<Long, Set<String>> loadRoleCodes(String tenantId, List<SysUserEntity> users, boolean globalScope) {
        List<Long> userIds = users.stream().map(SysUserEntity::getId).toList();
        List<SysUserRoleEntity> userRoles = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRoleEntity>()
                .eq(!globalScope, SysUserRoleEntity::getTenantId, tenantId)
                .in(SysUserRoleEntity::getUserId, userIds));
        if (userRoles.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<Long, String>> roleCodeMapByTenant = userRoles.stream()
                .map(SysUserRoleEntity::getTenantId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toMap(
                        java.util.function.Function.identity(),
                        roleQueryPort::loadRoleCodeMap
                ));

        return userRoles.stream().collect(Collectors.groupingBy(
                SysUserRoleEntity::getUserId,
                Collectors.mapping(link -> roleCodeMapByTenant
                        .getOrDefault(link.getTenantId(), Map.of())
                        .get(link.getRoleId()), Collectors.filtering(java.util.Objects::nonNull, Collectors.toSet()))
        ));
    }

    private Map<Long, Set<String>> loadPermissionCodes(
            String tenantId,
            List<SysUserEntity> users,
            Map<Long, Set<String>> roleCodesByUserId,
            boolean globalScope
    ) {
        if (roleCodesByUserId.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> userTenantMap = users.stream().collect(Collectors.toMap(SysUserEntity::getId, SysUserEntity::getTenantId));
        Map<String, Map<Set<String>, Set<String>>> cache = new java.util.HashMap<>();
        return roleCodesByUserId.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> {
                    String effectiveTenantId = globalScope ? userTenantMap.getOrDefault(entry.getKey(), tenantId) : tenantId;
                    Set<String> key = entry.getValue() == null ? Set.of() : new java.util.TreeSet<>(entry.getValue());
                    return cache.computeIfAbsent(effectiveTenantId, ignored -> new java.util.HashMap<>())
                            .computeIfAbsent(key, item -> roleQueryPort.resolveGrantKeys(effectiveTenantId, item, false));
                }
        ));
    }

    private boolean isGlobalScope() {
        return TenantContext.isGlobalScope() || accessControlPort.isPlatformSuperAdmin();
    }
}
