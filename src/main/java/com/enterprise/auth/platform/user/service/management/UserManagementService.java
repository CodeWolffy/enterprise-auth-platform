package com.enterprise.auth.platform.user.service.management;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.audit.service.AuditService;
import com.enterprise.auth.platform.catalog.CatalogService;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.time.TimeSupport;
import com.enterprise.auth.platform.common.validator.PasswordValidator;
import com.enterprise.auth.platform.persistence.entity.SysRoleEntity;
import com.enterprise.auth.platform.persistence.entity.SysUserEntity;
import com.enterprise.auth.platform.persistence.entity.SysUserRoleEntity;
import com.enterprise.auth.platform.persistence.mapper.SysRoleMapper;
import com.enterprise.auth.platform.persistence.mapper.SysUserMapper;
import com.enterprise.auth.platform.persistence.mapper.SysUserRoleMapper;
import com.enterprise.auth.platform.security.AuthPrincipalCacheService;
import com.enterprise.auth.platform.security.DataScopeService;
import com.enterprise.auth.platform.security.PasswordHasher;
import com.enterprise.auth.platform.security.SecuritySupport;
import com.enterprise.auth.platform.tenant.TenantContext;
import com.enterprise.auth.platform.user.dto.CreateUserRequest;
import com.enterprise.auth.platform.user.dto.UpdateUserRequest;
import com.enterprise.auth.platform.user.model.UserSummary;
import com.enterprise.auth.platform.user.service.UserDirectoryService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserManagementService {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final PasswordHasher passwordHasher;
    private final UserDirectoryService userDirectoryService;
    private final CatalogService catalogService;
    private final AuditService auditService;
    private final DataScopeService dataScopeService;
    private final AuthPrincipalCacheService authPrincipalCacheService;

    public UserManagementService(
            SysUserMapper sysUserMapper,
            SysUserRoleMapper sysUserRoleMapper,
            SysRoleMapper sysRoleMapper,
            PasswordHasher passwordHasher,
            UserDirectoryService userDirectoryService,
            CatalogService catalogService,
            AuditService auditService,
            DataScopeService dataScopeService,
            AuthPrincipalCacheService authPrincipalCacheService
    ) {
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.passwordHasher = passwordHasher;
        this.userDirectoryService = userDirectoryService;
        this.catalogService = catalogService;
        this.auditService = auditService;
        this.dataScopeService = dataScopeService;
        this.authPrincipalCacheService = authPrincipalCacheService;
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
        entity.setPasswordUpdatedAt(TimeSupport.utcNowDateTime());
        try {
            sysUserMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("鐢ㄦ埛鍚嶅凡瀛樺湪");
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
    public UserSummary update(Long userId, UpdateUserRequest request) {
        String tenantId = currentTenantId();
        String operator = SecuritySupport.currentOperator();
        SysUserEntity entity = getUser(userId, tenantId);
        validateDeptAccess(tenantId, request.deptId());

        entity.setDisplayName(request.displayName());
        entity.setMobile(request.mobile());
        entity.setEmail(request.email());
        entity.setDeptId(request.deptId());
        if (request.enabled() != null) {
            entity.setEnabled(request.enabled() ? 1 : 0);
        }
        if (StringUtils.hasText(request.password())) {
            validatePassword(request.password());
            entity.setPasswordHash(passwordHasher.hash(request.password()));
            entity.setSessionVersion((entity.getSessionVersion() == null ? 1 : entity.getSessionVersion()) + 1);
            entity.setPasswordUpdatedAt(TimeSupport.utcNowDateTime());
        }
        sysUserMapper.updateById(entity);

        if (request.roleCodes() != null) {
            syncUserRoles(tenantId, entity.getId(), request.roleCodes());
        }
        authPrincipalCacheService.evictByUser(entity.getId(), tenantId, entity.getUsername());
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
        Set<String> roleCodes = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRoleEntity>()
                        .eq(SysRoleEntity::getTenantId, tenantId)
                        .eq(SysRoleEntity::getDeleted, 0)
                        .in(SysRoleEntity::getId, roleIds))
                .stream()
                .map(SysRoleEntity::getRoleCode)
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
        auditService.record("USER_DELETED", operator, tenantId, Map.of("userId", userId, "username", entity.getUsername()));
    }

    private void syncUserRoles(String tenantId, Long userId, Set<String> roleCodes) {
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRoleEntity>()
                .eq(SysUserRoleEntity::getTenantId, tenantId)
                .eq(SysUserRoleEntity::getUserId, userId));
        if (roleCodes == null || roleCodes.isEmpty()) {
            return;
        }

        List<SysRoleEntity> roles = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRoleEntity>()
                .eq(SysRoleEntity::getTenantId, tenantId)
                .eq(SysRoleEntity::getDeleted, 0)
                .in(SysRoleEntity::getRoleCode, roleCodes));
        if (roles.size() != roleCodes.size()) {
            throw new BusinessException("存在无效的角色编码");
        }

        for (SysRoleEntity role : roles) {
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
        PasswordValidator.validate(password);
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
