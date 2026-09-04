package com.enterprise.auth.platform.modules.user.application;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.enterprise.auth.platform.modules.user.api.UserSessionIndexPort;
import com.enterprise.auth.platform.modules.iam.api.IamDeptQueryPort;
import com.enterprise.auth.platform.modules.iam.api.IamRoleQueryPort;
import com.enterprise.auth.platform.modules.iam.api.IamSecurityPolicyQueryPort;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.PasswordValidator;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserRoleEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserRoleMapper;
import com.enterprise.auth.platform.modules.user.api.UserAuthorizationInvalidationPort;
import com.enterprise.auth.platform.modules.user.api.UserTenantReferencePort;
import com.enterprise.auth.platform.modules.user.api.UserAccessControlPort;
import com.enterprise.auth.platform.modules.user.api.UserPasswordHashPort;
import com.enterprise.auth.platform.common.notification.NotificationScenarioPort;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.context.TenantContextSupport;
import com.enterprise.auth.platform.modules.user.interfaces.CreateUserRequest;
import com.enterprise.auth.platform.modules.user.interfaces.AssignedRoleView;
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
    private final IamRoleQueryPort roleQueryPort;
    private final IamDeptQueryPort deptQueryPort;
    private final UserPasswordHashPort passwordHashPort;
    private final UserDirectoryService userDirectoryService;
    private final UserAccessControlPort accessControlPort;
    private final UserAuthorizationInvalidationPort authorizationInvalidationPort;
    private final UserSessionIndexPort sessionIndexPort;
    private final IamSecurityPolicyQueryPort securityPolicyQueryPort;
    private final NotificationScenarioPort notificationScenarioPublisher;
    private final UserTenantReferencePort tenantReferences;

    public UserManagementService(
            SysUserMapper sysUserMapper,
            SysUserRoleMapper sysUserRoleMapper,
            IamRoleQueryPort roleQueryPort,
            IamDeptQueryPort deptQueryPort,
            UserPasswordHashPort passwordHashPort,
            UserDirectoryService userDirectoryService,
            UserAccessControlPort accessControlPort,
            UserAuthorizationInvalidationPort authorizationInvalidationPort,
            UserSessionIndexPort sessionIndexPort,
            IamSecurityPolicyQueryPort securityPolicyQueryPort,
            NotificationScenarioPort notificationScenarioPublisher,
            UserTenantReferencePort tenantReferences
    ) {
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.roleQueryPort = roleQueryPort;
        this.deptQueryPort = deptQueryPort;
        this.passwordHashPort = passwordHashPort;
        this.userDirectoryService = userDirectoryService;
        this.accessControlPort = accessControlPort;
        this.authorizationInvalidationPort = authorizationInvalidationPort;
        this.sessionIndexPort = sessionIndexPort;
        this.securityPolicyQueryPort = securityPolicyQueryPort;
        this.notificationScenarioPublisher = notificationScenarioPublisher;
        this.tenantReferences = tenantReferences;
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
        entity.setPasswordHash(passwordHashPort.hash(request.password()));
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
        authorizationInvalidationPort.invalidateUser(entity.getId(), tenantId, entity.getUsername());
        return loadSummary(entity.getId(), tenantId);
    }

    @Transactional
    public UserSummary create(CreateUserRequest request) {
        return createUser(resolveTargetTenantId(request.tenantId()), request, accessControlPort.currentOperator());
    }

    @Transactional
    public UserSummary update(Long userId, CreateUserRequest request) {
        String operator = accessControlPort.currentOperator();
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
            entity.setPasswordHash(passwordHashPort.hash(request.password()));
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
        authorizationInvalidationPort.invalidateUser(entity.getId(), tenantId, entity.getUsername());
        if (invalidateSessions) {
            kickoutUserSessions(entity.getId());
        }
        return loadSummary(entity.getId(), tenantId);
    }

    @Transactional
    public UserSummary assignRoles(Long userId, Set<String> roleCodes) {
        String operator = accessControlPort.currentOperator();
        SysUserEntity entity = getUser(userId);
        String tenantId = entity.getTenantId();
        validateSelfRoleAssignment(entity, roleCodes);
        validateRoleCodesRequired(roleCodes);
        syncUserRoles(tenantId, userId, roleCodes);
        authorizationInvalidationPort.invalidateUser(entity.getId(), tenantId, entity.getUsername());
        return loadSummary(userId, tenantId);
    }

    public List<AssignedRoleView> listAssignedRoles(Long userId) {
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
        return roleQueryPort.listRolesByIds(tenantId, assignedRoleIds).stream()
                .map(role -> new AssignedRoleView(
                        role.id(),
                        role.tenantId(),
                        role.code(),
                        role.name(),
                        role.description(),
                        role.dataScopeType(),
                        role.customDeptIds()
                ))
                .toList();
    }

    @Transactional
    public void delete(Long userId) {
        String operator = accessControlPort.currentOperator();
        SysUserEntity entity = getUser(userId);
        String tenantId = entity.getTenantId();
        validateSelfDeletion(entity);

        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRoleEntity>()
                .eq(SysUserRoleEntity::getTenantId, tenantId)
                .eq(SysUserRoleEntity::getUserId, userId));
        entity.setEnabled(0);
        sysUserMapper.updateById(entity);
        sysUserMapper.deleteById(entity.getId());
        authorizationInvalidationPort.invalidateUser(entity.getId(), tenantId, entity.getUsername());
        kickoutUserSessions(entity.getId());
        notificationScenarioPublisher.accountDisabled(tenantId, entity.getId(), entity.getUsername(), operator);
    }

    private void kickoutUserSessions(Long userId) {
        if (userId == null) {
            return;
        }
        afterCommit(() -> {
            StpUtil.kickout(userId);
        sessionIndexPort.removeUser(userId);
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
        return accessControlPort.currentUser()
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

        var roles = roleQueryPort.loadRoleIdMap(tenantId, normalizedRoleCodes);
        if (roles.size() != normalizedRoleCodes.size()) {
            throw new BusinessException("存在无效的角色编码");
        }

        for (Long roleId : roles.values()) {
            SysUserRoleEntity link = new SysUserRoleEntity();
            link.setTenantId(tenantId);
            link.setUserId(userId);
            link.setRoleId(roleId);
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
        boolean globalScope = accessControlPort.isPlatformSuperAdmin();
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
        if (!accessControlPort.canAccessUser(entity.getTenantId(), entity.getId())) {
            throw new BusinessException("无权访问该用户");
        }
        return entity;
    }

    private void validateDeptAccess(String tenantId, Long deptId) {
        if (deptId == null) {
            return;
        }
        if (!accessControlPort.canAccessDept(tenantId, deptId)
                || deptQueryPort.countByIds(tenantId, List.of(deptId)) != 1) {
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
        PasswordValidator.validate(password, securityPolicyQueryPort.effectivePolicy(tenantId));
    }

    private UserSummary loadSummary(Long userId, String tenantId) {
        String previousTenantId = TenantContext.getTenantId();
        boolean previousGlobalScope = TenantContext.isGlobalScope();
        try {
            if (accessControlPort.isPlatformSuperAdmin()) {
                TenantContext.setGlobalScope(tenantId);
            } else {
                TenantContext.setTenantId(tenantId);
            }
            return userDirectoryService.findUserSummary(userId, tenantId)
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
        if (!accessControlPort.isPlatformSuperAdmin()) {
            return currentTenantId;
        }
        String targetTenantId = StringUtils.hasText(requestedTenantId) ? requestedTenantId.trim() : currentTenantId;
        if (!tenantReferences.tenantExists(targetTenantId)) {
            throw new BusinessException("TENANT_NOT_FOUND", "租户不存在");
        }
        return targetTenantId;
    }
}
