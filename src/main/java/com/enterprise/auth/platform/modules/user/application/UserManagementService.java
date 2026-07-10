package com.enterprise.auth.platform.modules.user.application;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.enterprise.auth.platform.modules.log.application.LogPublisher;
import com.enterprise.auth.platform.modules.auth.application.SessionIndexService;
import com.enterprise.auth.platform.modules.dept.application.DeptQueryFacade;
import com.enterprise.auth.platform.modules.role.application.RoleCatalogFacade;
import com.enterprise.auth.platform.modules.role.application.RoleView;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.PasswordValidator;
import com.enterprise.auth.platform.modules.role.application.RoleQueryFacade;
import com.enterprise.auth.platform.modules.tenant.application.TenantProfileFacade;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserRoleEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserRoleMapper;
import com.enterprise.auth.platform.modules.auth.application.AuthPermissionSnapshotInvalidationService;
import com.enterprise.auth.platform.modules.security.application.SecurityPolicyApplicationService;
import com.enterprise.auth.platform.common.authz.DataScopeService;
import com.enterprise.auth.platform.modules.auth.domain.PasswordHasher;
import com.enterprise.auth.platform.modules.notification.application.NotificationScenarioPublisher;
import com.enterprise.auth.platform.common.authz.SecuritySupport;
import com.enterprise.auth.platform.common.context.AuthContextHolder;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.context.TenantContextSupport;
import com.enterprise.auth.platform.modules.user.interfaces.CreateUserRequest;
import com.enterprise.auth.platform.modules.user.interfaces.UserSummary;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
public class UserManagementService {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final RoleQueryFacade roleQueryFacade;
    private final DeptQueryFacade deptQueryFacade;
    private final PasswordHasher passwordHasher;
    private final UserDirectoryService userDirectoryService;
    private final RoleCatalogFacade roleCatalogFacade;
    private final LogPublisher logPublisher;
    private final DataScopeService dataScopeService;
    private final AuthPermissionSnapshotInvalidationService permissionSnapshotInvalidationService;
    private final SessionIndexService sessionIndexService;
    private final SecurityPolicyApplicationService securityPolicyApplicationService;
    private final NotificationScenarioPublisher notificationScenarioPublisher;
    private final TenantProfileFacade tenantProfileFacade;

    public UserManagementService(
            SysUserMapper sysUserMapper,
            SysUserRoleMapper sysUserRoleMapper,
            RoleQueryFacade roleQueryFacade,
            DeptQueryFacade deptQueryFacade,
            PasswordHasher passwordHasher,
            UserDirectoryService userDirectoryService,
            RoleCatalogFacade roleCatalogFacade,
            LogPublisher logPublisher,
            DataScopeService dataScopeService,
            AuthPermissionSnapshotInvalidationService permissionSnapshotInvalidationService,
            SessionIndexService sessionIndexService,
            SecurityPolicyApplicationService securityPolicyApplicationService,
            NotificationScenarioPublisher notificationScenarioPublisher,
            TenantProfileFacade tenantProfileFacade
    ) {
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.roleQueryFacade = roleQueryFacade;
        this.deptQueryFacade = deptQueryFacade;
        this.passwordHasher = passwordHasher;
        this.userDirectoryService = userDirectoryService;
        this.roleCatalogFacade = roleCatalogFacade;
        this.logPublisher = logPublisher;
        this.dataScopeService = dataScopeService;
        this.permissionSnapshotInvalidationService = permissionSnapshotInvalidationService;
        this.sessionIndexService = sessionIndexService;
        this.securityPolicyApplicationService = securityPolicyApplicationService;
        this.notificationScenarioPublisher = notificationScenarioPublisher;
        this.tenantProfileFacade = tenantProfileFacade;
    }

    @Transactional
    public UserSummary createUser(String tenantId, CreateUserRequest request, String operator) {
        if (existsByUsername(request.username())) {
            throw new BusinessException("用户名已存在");
        }
        validateDeptAccess(tenantId, request.deptId());
        validateRoleCodesRequired(request.roleCodes());
        validatePassword(request.password(), tenantId);

        SysUserEntity entity = new SysUserEntity();
        entity.setTenantId(tenantId);
        entity.setDeptId(request.deptId());
        entity.setUsername(request.username());
        entity.setDisplayName(StringUtils.hasText(request.displayName()) ? request.displayName() : request.username());
        entity.setMobile(request.mobile());
        entity.setEmail(request.email());
        entity.setPasswordHash(passwordHasher.hash(request.password()));
        entity.setEnabled(Boolean.FALSE.equals(request.enabled()) ? 0 : 1);
        entity.setSessionVersion(1);
        entity.setMustChangePassword(1);
        entity.setPasswordUpdatedAt(TimeSupport.now());
        try {
            sysUserMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("用户名已存在");
        }

        syncUserRoles(tenantId, entity.getId(), request.roleCodes());
        permissionSnapshotInvalidationService.invalidateUser(entity.getId(), tenantId, entity.getUsername());
        return loadSummary(entity.getId(), tenantId);
    }

    @Transactional
    public UserSummary create(CreateUserRequest request) {
        return createUser(resolveTargetTenantId(request.tenantId()), request, SecuritySupport.currentOperator());
    }

    @Transactional
    public UserSummary update(Long userId, CreateUserRequest request) {
        String operator = SecuritySupport.currentOperator();
        SysUserEntity entity = getUser(userId);
        String tenantId = entity.getTenantId();
        validateDeptAccess(tenantId, request.deptId());
        validateSelfProtection(entity, request);

        entity.setDisplayName(StringUtils.hasText(request.displayName()) ? request.displayName() : entity.getDisplayName());
        entity.setMobile(request.mobile() != null ? request.mobile() : entity.getMobile());
        entity.setEmail(request.email() != null ? request.email() : entity.getEmail());
        entity.setDeptId(request.deptId() != null ? request.deptId() : entity.getDeptId());
        boolean invalidateSessions = false;
        boolean disabledByUpdate = false;
        boolean passwordResetByAdmin = false;
        if (request.enabled() != null) {
            disabledByUpdate = !request.enabled() && (entity.getEnabled() == null || entity.getEnabled() == 1);
            invalidateSessions = disabledByUpdate;
            entity.setEnabled(request.enabled() ? 1 : 0);
        }
        if (StringUtils.hasText(request.password())) {
            validatePassword(request.password(), tenantId);
            entity.setPasswordHash(passwordHasher.hash(request.password()));
            entity.setSessionVersion((entity.getSessionVersion() == null ? 1 : entity.getSessionVersion()) + 1);
            entity.setMustChangePassword(1);
            entity.setPasswordUpdatedAt(TimeSupport.now());
            invalidateSessions = true;
            passwordResetByAdmin = true;
        }
        sysUserMapper.updateById(entity);

        if (request.roleCodes() != null) {
            syncUserRoles(tenantId, entity.getId(), request.roleCodes());
        }
        permissionSnapshotInvalidationService.invalidateUser(entity.getId(), tenantId, entity.getUsername());
        if (invalidateSessions) {
            kickoutUserSessions(entity.getId());
        }
        return loadSummary(entity.getId(), tenantId);
    }

    @Transactional
    public UserSummary assignRoles(Long userId, Set<String> roleCodes) {
        String operator = SecuritySupport.currentOperator();
        SysUserEntity entity = getUser(userId);
        String tenantId = entity.getTenantId();
        validateSelfRoleAssignment(entity, roleCodes);
        validateRoleCodesRequired(roleCodes);
        syncUserRoles(tenantId, userId, roleCodes);
        permissionSnapshotInvalidationService.invalidateUser(entity.getId(), tenantId, entity.getUsername());
        return loadSummary(userId, tenantId);
    }

    public List<RoleView> listAssignedRoles(Long userId) {
        SysUserEntity entity = getUser(userId);
        String tenantId = entity.getTenantId();
        List<Long> roleIds = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRoleEntity>()
                .eq(SysUserRoleEntity::getTenantId, tenantId)
                .eq(SysUserRoleEntity::getUserId, userId))
                .stream()
                .map(SysUserRoleEntity::getRoleId)
                .toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        Set<Long> assignedRoleIds = roleIds.stream().collect(Collectors.toCollection(LinkedHashSet::new));
        return roleCatalogFacade.tenantRoles(tenantId).stream()
                .filter(role -> assignedRoleIds.contains(role.id()))
                .toList();
    }

    @Transactional
    public void delete(Long userId) {
        String operator = SecuritySupport.currentOperator();
        SysUserEntity entity = getUser(userId);
        String tenantId = entity.getTenantId();
        validateSelfDeletion(entity);

        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRoleEntity>()
                .eq(SysUserRoleEntity::getTenantId, tenantId)
                .eq(SysUserRoleEntity::getUserId, userId));
        entity.setEnabled(0);
        sysUserMapper.updateById(entity);
        sysUserMapper.deleteById(entity.getId());
        permissionSnapshotInvalidationService.invalidateUser(entity.getId(), tenantId, entity.getUsername());
        kickoutUserSessions(entity.getId());
        notificationScenarioPublisher.accountDisabled(tenantId, entity.getId(), entity.getUsername(), operator);
    }

    private void kickoutUserSessions(Long userId) {
        if (userId == null) {
            return;
        }
        afterCommit(() -> {
            StpUtil.kickout(userId);
            sessionIndexService.removeUser(userId);
        });
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    private void validateRoleCodesRequired(Set<String> roleCodes) {
        if (normalizeRoleCodes(roleCodes).isEmpty()) {
            throw new BusinessException("用户至少需要分配一个角色");
        }
    }

    private void validateSelfProtection(SysUserEntity entity, CreateUserRequest request) {
        if (!isCurrentUser(entity)) {
            return;
        }
        if (Boolean.FALSE.equals(request.enabled())) {
            throw new BusinessException("不能停用当前登录用户");
        }
        validateSelfRoleAssignment(entity, request.roleCodes());
    }

    private void validateSelfRoleAssignment(SysUserEntity entity, Set<String> roleCodes) {
        if (!isCurrentUser(entity) || roleCodes == null) {
            return;
        }
        if (normalizeRoleCodes(roleCodes).isEmpty()) {
            throw new BusinessException("不能移除当前登录用户的全部角色");
        }
    }

    private void validateSelfDeletion(SysUserEntity entity) {
        if (isCurrentUser(entity)) {
            throw new BusinessException("不能删除当前登录用户");
        }
    }

    private boolean isCurrentUser(SysUserEntity entity) {
        if (entity == null || entity.getId() == null) {
            return false;
        }
        return AuthContextHolder.currentUser()
                .map(user -> entity.getId().equals(user.id()) && entity.getTenantId().equals(user.tenantId()))
                .orElse(false);
    }

    private void syncUserRoles(String tenantId, Long userId, Set<String> roleCodes) {
        Set<String> normalizedRoleCodes = normalizeRoleCodes(roleCodes);
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRoleEntity>()
                .eq(SysUserRoleEntity::getTenantId, tenantId)
                .eq(SysUserRoleEntity::getUserId, userId));
        if (normalizedRoleCodes.isEmpty()) {
            return;
        }

        var roles = roleQueryFacade.listAll(tenantId).stream()
                .filter(r -> normalizedRoleCodes.contains(r.getRoleCode()))
                .toList();
        if (roles.size() != normalizedRoleCodes.size()) {
            throw new BusinessException("存在无效的角色编码");
        }

        for (var role : roles) {
            SysUserRoleEntity link = new SysUserRoleEntity();
            link.setTenantId(tenantId);
            link.setUserId(userId);
            link.setRoleId(role.getId());
            sysUserRoleMapper.insert(link);
        }
    }

    private Set<String> normalizeRoleCodes(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Set.of();
        }
        return roleCodes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private SysUserEntity getUser(Long userId) {
        boolean globalScope = dataScopeService.isPlatformSuperAdmin();
        SysUserEntity entity;
        if (globalScope) {
            entity = InterceptorIgnoreHelper.execute(
                    IgnoreStrategy.builder().tenantLine(true).build(),
                    () -> sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                            .eq(SysUserEntity::getId, userId)
                            .eq(SysUserEntity::getDeleted, 0)
                            .last("limit 1"))
            );
        } else {
            entity = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                    .eq(SysUserEntity::getId, userId)
                .eq(SysUserEntity::getTenantId, TenantContextSupport.currentTenantIdOrPlatform())
                    .eq(SysUserEntity::getDeleted, 0)
                    .last("limit 1"));
        }
        if (entity == null) {
            throw new BusinessException("用户不存在");
        }
        if (!dataScopeService.canAccessUser(entity.getTenantId(), entity.getId())) {
            throw new BusinessException("无权访问该用户");
        }
        return entity;
    }

    private void validateDeptAccess(String tenantId, Long deptId) {
        if (deptId == null) {
            return;
        }
        if (!dataScopeService.canAccessDept(tenantId, deptId)
                || deptQueryFacade.countByIds(tenantId, List.of(deptId)) != 1) {
            throw new BusinessException("无权使用该部门");
        }
    }

    public boolean existsByUsername(String username) {
        return sysUserMapper.countActiveByUsername(username) > 0;
    }

    private void validatePassword(String password) {
        validatePassword(password, TenantContextSupport.currentTenantIdOrPlatform());
    }

    private void validatePassword(String password, String tenantId) {
        PasswordValidator.validate(password, securityPolicyApplicationService.effectivePolicy(tenantId));
    }

    private UserSummary loadSummary(Long userId, String tenantId) {
        String previousTenantId = TenantContext.getTenantId();
        boolean previousGlobalScope = TenantContext.isGlobalScope();
        try {
            if (dataScopeService.isPlatformSuperAdmin()) {
                TenantContext.setGlobalScope(tenantId);
            } else {
                TenantContext.setTenantId(tenantId);
            }
            return userDirectoryService.listUsers().stream()
                    .filter(user -> user.id().equals(userId))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("用户不存在"));
        } finally {
            if (StringUtils.hasText(previousTenantId)) {
                if (previousGlobalScope) {
                    TenantContext.setGlobalScope(previousTenantId);
                } else {
                    TenantContext.setTenantId(previousTenantId);
                }
            } else {
                TenantContext.clear();
            }
        }
    }

    private String resolveTargetTenantId(String requestedTenantId) {
        String currentTenantId = TenantContextSupport.currentTenantIdOrPlatform();
        if (!dataScopeService.isPlatformSuperAdmin()) {
            return currentTenantId;
        }
        String targetTenantId = StringUtils.hasText(requestedTenantId) ? requestedTenantId.trim() : currentTenantId;
        tenantProfileFacade.findByTenantId(targetTenantId)
                .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "租户不存在"));
        return targetTenantId;
    }
}
