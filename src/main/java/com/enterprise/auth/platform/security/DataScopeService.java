package com.enterprise.auth.platform.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.model.DataScopeType;
import com.enterprise.auth.platform.persistence.entity.SysDeptEntity;
import com.enterprise.auth.platform.persistence.entity.SysUserEntity;
import com.enterprise.auth.platform.persistence.mapper.SysDeptMapper;
import com.enterprise.auth.platform.persistence.mapper.SysUserMapper;
import com.enterprise.auth.platform.user.model.UserAccount;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DataScopeService {

    private final SysUserMapper sysUserMapper;
    private final SysDeptMapper sysDeptMapper;
    private final PlatformAdminSupport platformAdminSupport;
    private final CurrentUserService currentUserService;

    public DataScopeService(
            SysUserMapper sysUserMapper,
            SysDeptMapper sysDeptMapper,
            PlatformAdminSupport platformAdminSupport,
            CurrentUserService currentUserService
    ) {
        this.sysUserMapper = sysUserMapper;
        this.sysDeptMapper = sysDeptMapper;
        this.platformAdminSupport = platformAdminSupport;
        this.currentUserService = currentUserService;
    }

    public Optional<UserAccount> currentUser() {
        return currentUserService.currentUser();
    }

    public List<SysUserEntity> filterUsers(String tenantId, List<SysUserEntity> users) {
        Optional<UserAccount> currentUser = currentUser();
        if (currentUser.isEmpty()) {
            return users;
        }
        ScopeContext context = buildContext(tenantId, currentUser.get());
        if (context == ScopeContext.ALL) {
            return users;
        }
        if (context == ScopeContext.NONE) {
            return List.of();
        }
        return users.stream()
                .filter(user -> user.getId() != null && context.userIds().contains(user.getId()))
                .toList();
    }

    public List<SysDeptEntity> filterDepartments(String tenantId, List<SysDeptEntity> departments) {
        Optional<UserAccount> currentUser = currentUser();
        if (currentUser.isEmpty()) {
            return departments;
        }
        ScopeContext context = buildContext(tenantId, currentUser.get());
        if (context == ScopeContext.ALL) {
            return departments;
        }
        if (context == ScopeContext.NONE) {
            return List.of();
        }
        return departments.stream()
                .filter(dept -> dept.getId() != null && context.deptIds().contains(dept.getId()))
                .toList();
    }

    public <T> List<T> filterByCreator(String tenantId, List<T> items, java.util.function.Function<T, String> creatorExtractor) {
        Optional<Set<String>> visibleUsernames = visibleUsernames(tenantId);
        if (visibleUsernames.isEmpty()) {
            return items;
        }
        Set<String> usernames = visibleUsernames.get();
        if (usernames.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .filter(item -> usernames.contains(creatorExtractor.apply(item)))
                .toList();
    }

    public Optional<Set<String>> visibleUsernames(String tenantId) {
        Optional<UserAccount> currentUser = currentUser();
        if (currentUser.isEmpty()) {
            return Optional.empty();
        }
        ScopeContext context = buildContext(tenantId, currentUser.get());
        if (context == ScopeContext.ALL) {
            return Optional.empty();
        }
        if (context == ScopeContext.NONE) {
            return Optional.of(Set.of());
        }
        return Optional.of(context.usernames());
    }

    public Optional<Set<Long>> visibleUserIds(String tenantId) {
        Optional<UserAccount> currentUser = currentUser();
        if (currentUser.isEmpty()) {
            return Optional.empty();
        }
        ScopeContext context = buildContext(tenantId, currentUser.get());
        if (context == ScopeContext.ALL) {
            return Optional.empty();
        }
        if (context == ScopeContext.NONE) {
            return Optional.of(Set.of());
        }
        return Optional.of(context.userIds());
    }

    public Optional<Set<Long>> visibleDeptIds(String tenantId) {
        Optional<UserAccount> currentUser = currentUser();
        if (currentUser.isEmpty()) {
            return Optional.empty();
        }
        ScopeContext context = buildContext(tenantId, currentUser.get());
        if (context == ScopeContext.ALL) {
            return Optional.empty();
        }
        if (context == ScopeContext.NONE) {
            return Optional.of(Set.of());
        }
        return Optional.of(context.deptIds());
    }

    public boolean canAccessUser(String tenantId, Long userId) {
        if (userId == null) {
            return false;
        }
        return visibleUserIds(tenantId)
                .map(userIds -> userIds.contains(userId))
                .orElse(true);
    }

    public boolean canAccessDept(String tenantId, Long deptId) {
        if (deptId == null) {
            return false;
        }
        return visibleDeptIds(tenantId)
                .map(deptIds -> deptIds.contains(deptId))
                .orElse(true);
    }

    public boolean canAccessCreatedBy(String tenantId, String createdBy) {
        Optional<Set<String>> visibleUsernames = visibleUsernames(tenantId);
        if (visibleUsernames.isEmpty()) {
            return true;
        }
        if (!StringUtils.hasText(createdBy)) {
            return false;
        }
        return visibleUsernames.get().contains(createdBy);
    }

    private ScopeContext buildContext(String tenantId, UserAccount principal) {
        if (platformAdminSupport.isPlatformSuperAdmin(principal)) {
            return ScopeContext.ALL;
        }
        if (!tenantId.equals(principal.tenantId())) {
            return ScopeContext.NONE;
        }
        if (principal.dataScopeType() == DataScopeType.ALL) {
            return ScopeContext.ALL;
        }

        SysUserEntity currentEntity = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getId, principal.id())
                .eq(SysUserEntity::getTenantId, tenantId)
                .eq(SysUserEntity::getDeleted, 0)
                .last("limit 1"));
        if (currentEntity == null) {
            return ScopeContext.NONE;
        }

        Set<Long> deptIds = switch (principal.dataScopeType()) {
            case SELF -> Set.of();
            case DEPT -> currentEntity.getDeptId() == null ? Set.of() : Set.of(currentEntity.getDeptId());
            case DEPT_AND_CHILDREN -> collectDeptAndChildren(tenantId, currentEntity.getDeptId());
            case CUSTOM -> principal.customDeptIds() == null ? Set.of() : principal.customDeptIds();
            case ALL -> Set.of();
        };

        Set<Long> userIds;
        Set<String> usernames;
        if (principal.dataScopeType() == DataScopeType.SELF) {
            userIds = Set.of(principal.id());
            usernames = Set.of(principal.username());
        } else {
            List<SysUserEntity> visibleUsers = loadUsersByDeptIds(tenantId, deptIds);
            userIds = visibleUsers.stream().map(SysUserEntity::getId).collect(Collectors.toSet());
            usernames = visibleUsers.stream().map(SysUserEntity::getUsername).collect(Collectors.toSet());
        }
        return new ScopeContext(deptIds, userIds, usernames);
    }

    private List<SysUserEntity> loadUsersByDeptIds(String tenantId, Set<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return List.of();
        }
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getTenantId, tenantId)
                .eq(SysUserEntity::getDeleted, 0)
                .in(SysUserEntity::getDeptId, deptIds));
    }

    private Set<Long> collectDeptAndChildren(String tenantId, Long rootDeptId) {
        if (rootDeptId == null) {
            return Set.of();
        }
        List<SysDeptEntity> departments = sysDeptMapper.selectList(new LambdaQueryWrapper<SysDeptEntity>()
                .eq(SysDeptEntity::getTenantId, tenantId)
                .eq(SysDeptEntity::getDeleted, 0));
        var childrenByParentId = departments.stream()
                .filter(dept -> dept.getId() != null)
                .collect(Collectors.groupingBy(
                        dept -> dept.getParentId() == null ? 0L : dept.getParentId(),
                        Collectors.mapping(SysDeptEntity::getId, Collectors.toList())
                ));
        Set<Long> deptIds = new HashSet<>();
        Queue<Long> queue = new ArrayDeque<>();
        queue.add(rootDeptId);
        while (!queue.isEmpty()) {
            Long deptId = queue.poll();
            if (!deptIds.add(deptId)) {
                continue;
            }
            queue.addAll(childrenByParentId.getOrDefault(deptId, List.of()));
        }
        return deptIds;
    }

    private record ScopeContext(Set<Long> deptIds, Set<Long> userIds, Set<String> usernames) {
        private static final ScopeContext ALL = new ScopeContext(Set.of(), Set.of(), Set.of());
        private static final ScopeContext NONE = new ScopeContext(Set.of(), Set.of(), Set.of());
    }
}
