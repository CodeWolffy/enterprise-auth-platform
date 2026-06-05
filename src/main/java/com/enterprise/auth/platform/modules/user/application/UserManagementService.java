package com.enterprise.auth.platform.modules.user.application;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.modules.audit.application.AuditService;
import com.enterprise.auth.platform.modules.auth.application.SessionIndexService;
import com.enterprise.auth.platform.modules.resource.application.CatalogService;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.PasswordValidator;
import com.enterprise.auth.platform.modules.role.application.RoleQueryFacade;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserRoleEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserRoleMapper;
import com.enterprise.auth.platform.modules.auth.infrastructure.AuthPrincipalCacheService;
import com.enterprise.auth.platform.modules.security.application.SecurityPolicyApplicationService;
import com.enterprise.auth.platform.common.authz.DataScopeService;
import com.enterprise.auth.platform.modules.auth.domain.PasswordHasher;
import com.enterprise.auth.platform.common.authz.SecuritySupport;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.user.interfaces.CreateUserRequest;
import com.enterprise.auth.platform.modules.user.interfaces.UserSummary;
import com.enterprise.auth.platform.modules.user.application.UserDirectoryService;
import java.util.List;
import java.util.Map;
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
    private final PasswordHasher passwordHasher;
    private final UserDirectoryService userDirectoryService;
    private final CatalogService catalogService;
    private final AuditService auditService;
    private final DataScopeService dataScopeService;
    private final AuthPrincipalCacheService authPrincipalCacheService;
    private final SessionIndexService sessionIndexService;
    private final SecurityPolicyApplicationService securityPolicyApplicationService;

    public UserManagementService(
            SysUserMapper sysUserMapper,
            SysUserRoleMapper sysUserRoleMapper,
            RoleQueryFacade roleQueryFacade,
            PasswordHasher passwordHasher,
            UserDirectoryService userDirectoryService,
            CatalogService catalogService,
            AuditService auditService,
            DataScopeService dataScopeService,
            AuthPrincipalCacheService authPrincipalCacheService,
            SessionIndexService sessionIndexService,
            SecurityPolicyApplicationService securityPolicyApplicationService
    ) {
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.roleQueryFacade = roleQueryFacade;
        this.passwordHasher = passwordHasher;
        this.userDirectoryService = userDirectoryService;
        this.catalogService = catalogService;
        this.auditService = auditService;
        this.dataScopeService = dataScopeService;
        this.authPrincipalCacheService = authPrincipalCacheService;
        this.sessionIndexService = sessionIndexService;
        this.securityPolicyApplicationService = securityPolicyApplicationService;
    }

    @Transactional
    public UserSummary createUser(String tenantId, CreateUserRequest request, String operator) {
        if (existsByUsername(request.username())) {
            throw new BusinessException("用户名已存在");
        }
        validateDeptAccess(tenantId, request.deptId());
        validatePassword(request.password());

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
        entity.setPasswordUpdatedAt(TimeSupport.utcNowDateTime());
        try {
            sysUserMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("用户名已存在");
        }

        syncUserRoles(tenantId, entity.getId(), request.roleCodes());
        authPrincipalCacheService.evictByUser(entity.getId(), tenantId, entity.getUsername());
        auditService.record("USER_CREATED", operator, tenantId, Map.of("userId", entity.getId(), "username", entity.getUsername()));
        return loadSummary(entity.getId(), tenantId);
    }

    @Transactional
    public UserSummary create(CreateUserRequest request) {
        return createUser(currentTenantId(), request, SecuritySupport.currentOperator());
    }

    @Transactional
    public UserSummary update(Long userId, CreateUserRequest request) {
        String tenantId = currentTenantId();
        String operator = SecuritySupport.currentOperator();
        SysUserEntity entity = getUser(userId, tenantId);
        validateDeptAccess(tenantId, request.deptId());

        entity.setDisplayName(request.displayName());
        entity.setMobile(request.mobile());
        entity.setEmail(request.email());
        entity.setDeptId(request.deptId());
        boolean invalidateSessions = false;
        if (request.enabled() != null) {
            invalidateSessions = !request.enabled() && (entity.getEnabled() == null || entity.getEnabled() == 1);
            entity.setEnabled(request.enabled() ? 1 : 0);
        }
        if (StringUtils.hasText(request.password())) {
            validatePassword(request.password());
            entity.setPasswordHash(passwordHasher.hash(request.password()));
            entity.setSessionVersion((entity.getSessionVersion() == null ? 1 : entity.getSessionVersion()) + 1);
            entity.setMustChangePassword(1);
            entity.setPasswordUpdatedAt(TimeSupport.utcNowDateTime());
            invalidateSessions = true;
        }
        sysUserMapper.updateById(entity);

        if (request.roleCodes() != null) {
            syncUserRoles(tenantId, entity.getId(), request.roleCodes());
        }
        authPrincipalCacheService.evictByUser(entity.getId(), tenantId, entity.getUsername());
        if (invalidateSessions) {
            kickoutUserSessions(entity.getId());
        }
        auditService.record("USER_UPDATED", operator, tenantId, Map.of("userId", entity.getId(), "username", entity.getUsername()));
        return loadSummary(entity.getId(), tenantId);
    }

    @Transactional
    public UserSummary assignRoles(Long userId, Set<String> roleCodes) {
        String tenantId = currentTenantId();
        String operator = SecuritySupport.currentOperator();
        SysUserEntity entity = getUser(userId, tenantId);
        syncUserRoles(tenantId, userId, roleCodes);
        authPrincipalCacheService.evictByUser(entity.getId(), tenantId, entity.getUsername());
        auditService.record("USER_ROLE_ASSIGNED", operator, tenantId, Map.of("userId", userId, "roleCodes", roleCodes));
        return loadSummary(userId, tenantId);
    }

    public List<CatalogService.RoleView> listAssignedRoles(Long userId) {
        String tenantId = currentTenantId();
        getUser(userId, tenantId);
        List<Long> roleIds = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRoleEntity>()
                        .eq(SysUserRoleEntity::getTenantId, tenantId)
                        .eq(SysUserRoleEntity::getUserId, userId))
                .stream()
                .map(SysUserRoleEntity::getRoleId)
                .toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        Map<Long, String> roleCodeMap = roleQueryFacade.loadRoleCodeMap(tenantId);
        Set<String> roleCodes = roleIds.stream()
                .filter(roleCodeMap::containsKey)
                .map(roleCodeMap::get)
                .collect(Collectors.toSet());
        return catalogService.roles().stream()
                .filter(role -> roleCodes.contains(role.code()))
                .toList();
    }

    @Transactional
    public void delete(Long userId) {
        String tenantId = currentTenantId();
        String operator = SecuritySupport.currentOperator();
        SysUserEntity entity = getUser(userId, tenantId);

        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRoleEntity>()
                .eq(SysUserRoleEntity::getTenantId, tenantId)
                .eq(SysUserRoleEntity::getUserId, userId));
        entity.setEnabled(0);
        sysUserMapper.updateById(entity);
        sysUserMapper.deleteById(entity.getId());
        authPrincipalCacheService.evictByUser(entity.getId(), tenantId, entity.getUsername());
        kickoutUserSessions(entity.getId());
        auditService.record("USER_DELETED", operator, tenantId, Map.of("userId", userId, "username", entity.getUsername()));
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

    private void syncUserRoles(String tenantId, Long userId, Set<String> roleCodes) {
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRoleEntity>()
                .eq(SysUserRoleEntity::getTenantId, tenantId)
                .eq(SysUserRoleEntity::getUserId, userId));
        if (roleCodes == null || roleCodes.isEmpty()) {
            return;
        }

        var roles = roleQueryFacade.listAll(tenantId).stream()
                .filter(r -> roleCodes.contains(r.getRoleCode()))
                .toList();
        if (roles.size() != roleCodes.size()) {
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

    private SysUserEntity getUser(Long userId, String tenantId) {
        SysUserEntity entity = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getId, userId)
                .eq(SysUserEntity::getTenantId, tenantId)
                .eq(SysUserEntity::getDeleted, 0)
                .last("limit 1"));
        if (entity == null) {
            throw new BusinessException("用户不存在");
        }
        if (!dataScopeService.canAccessUser(tenantId, entity.getId())) {
            throw new BusinessException("无权访问该用户");
        }
        return entity;
    }

    private void validateDeptAccess(String tenantId, Long deptId) {
        if (deptId != null && !dataScopeService.canAccessDept(tenantId, deptId)) {
            throw new BusinessException("无权使用该部门");
        }
    }

    public boolean existsByUsername(String username) {
        return sysUserMapper.countActiveByUsername(username) > 0;
    }

    private void validatePassword(String password) {
        PasswordValidator.validate(password, securityPolicyApplicationService.currentTenantPolicy());
    }

    private UserSummary loadSummary(Long userId, String tenantId) {
        String previousTenantId = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenantId);
            return userDirectoryService.listUsers().stream()
                    .filter(user -> user.id().equals(userId))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("用户不存在"));
        } finally {
            if (StringUtils.hasText(previousTenantId)) {
                TenantContext.setTenantId(previousTenantId);
            } else {
                TenantContext.clear();
            }
        }
    }

    private String currentTenantId() {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : "platform";
    }
}
